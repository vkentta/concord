package com.walmartlabs.concord.dependencymanager;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.aether.repository.RepositoryPolicy.CHECKSUM_POLICY_IGNORE;
import static org.eclipse.aether.repository.RepositoryPolicy.UPDATE_POLICY_NEVER;

public class DependencyManager {

    private static final Logger log = LoggerFactory.getLogger(DependencyManager.class);

    private static final String CFG_FILE_KEY = "CONCORD_MAVEN_CFG";
    private static final String CFG_PLUGIN_VERSION_KEY = "CONCORD_PLUGIN_VERSION_CFG";

    private static final String FILES_CACHE_DIR = "files";
    public static final String MAVEN_SCHEME = "mvn";
    private static final String MVN_VERSION_PATTERN = ".*:[\\d]+(?:\\.\\d+)*(?:-\\w*)?$";

    private static final MavenRepository MAVEN_CENTRAL = new MavenRepository("central", "default", "https://repo.maven.apache.org/maven2/", false);
    private static final List<MavenRepository> DEFAULT_REPOS = Collections.singletonList(MAVEN_CENTRAL);

    private final Path cacheDir;
    private final Path localCacheDir;
    private final List<RemoteRepository> repositories;
    private final Object mutex = new Object();
    private final RepositorySystem maven = newMavenRepositorySystem();
    private final RepositoryCache mavenCache = new DefaultRepositoryCache();
    private final Map<String, String> pluginsVersion;

    public DependencyManager(Path cacheDir) throws IOException {
        this(cacheDir, readCfg());
    }

    public DependencyManager(Path cacheDir, List<MavenRepository> repositories) throws IOException {
        this.cacheDir = cacheDir;
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        this.localCacheDir = Paths.get(System.getProperty("user.home")).resolve(".m2/repository");

        log.info("init -> using repositories: {}", repositories);
        this.repositories = toRemote(repositories);

        String pluginsVersionCfgPath = System.getenv(CFG_PLUGIN_VERSION_KEY);

        if (pluginsVersionCfgPath == null) {
            this.pluginsVersion = Collections.emptyMap();
        } else {
            this.pluginsVersion = new ObjectMapper().readValue(new FileInputStream(pluginsVersionCfgPath),
                    TypeFactory.defaultInstance().constructMapType(HashMap.class, String.class, String.class));
        }
    }

    public Path getLocalCacheDir() {
        return localCacheDir;
    }

    public Collection<DependencyEntity> resolve(Collection<URI> items) throws IOException {
        if (items == null || items.isEmpty()) {
            return Collections.emptySet();
        }

        // ensure stable order
        List<URI> uris = new ArrayList<>(items);
        Collections.sort(uris);

        DependencyList deps = categorize(uris);

        Collection<DependencyEntity> result = new HashSet<>();

        result.addAll(resolveDirectLinks(deps.directLinks));

        result.addAll(resolveMavenTransitiveDependencies(deps.mavenTransitiveDependencies).stream()
                .map(DependencyManager::toDependency)
                .collect(Collectors.toList()));

        result.addAll(resolveMavenSingleDependencies(deps.mavenSingleDependencies).stream()
                .map(DependencyManager::toDependency)
                .collect(Collectors.toList()));

        return result;
    }

    private DependencyList categorize(List<URI> items) throws IOException {
        List<MavenDependency> mavenTransitiveDependencies = new ArrayList<>();
        List<MavenDependency> mavenSingleDependencies = new ArrayList<>();
        List<URI> directLinks = new ArrayList<>();

        for (URI item : items) {
            String scheme = item.getScheme();
            if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
                String id = item.getAuthority();

                if (!pluginsVersion.isEmpty() && !hasVersion(id)) {
                    id = appendVersion(id);
                }

                Artifact artifact = new DefaultArtifact(id);

                Map<String, String> cfg = splitQuery(item);
                String scope = cfg.getOrDefault("scope", JavaScopes.COMPILE);
                boolean transitive = Boolean.parseBoolean(cfg.getOrDefault("transitive", "true"));

                if (transitive) {
                    mavenTransitiveDependencies.add(new MavenDependency(artifact, scope));
                } else {
                    mavenSingleDependencies.add(new MavenDependency(artifact, scope));
                }
            } else {
                directLinks.add(item);
            }
        }

        return new DependencyList(mavenTransitiveDependencies, mavenSingleDependencies, directLinks);
    }

    public DependencyEntity resolveSingle(URI item) throws IOException {
        String scheme = item.getScheme();
        if (MAVEN_SCHEME.equalsIgnoreCase(scheme)) {
            String id = item.getAuthority();
            Artifact artifact = resolveMavenSingle(new MavenDependency(new DefaultArtifact(id), JavaScopes.COMPILE));
            return toDependency(artifact);
        } else {
            return new DependencyEntity(resolveFile(item), item);
        }
    }

    private Collection<DependencyEntity> resolveDirectLinks(Collection<URI> items) throws IOException {
        Collection<DependencyEntity> paths = new HashSet<>();
        for (URI item : items) {
            paths.add(new DependencyEntity(resolveFile(item), item));
        }
        return paths;
    }

    private Path resolveFile(URI uri) throws IOException {
        boolean skipCache = shouldSkipCache(uri);
        String name = getLastPart(uri);

        Path baseDir = cacheDir.resolve(FILES_CACHE_DIR);
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        Path p = baseDir.resolve(name);

        synchronized (mutex) {
            if (skipCache || !Files.exists(p)) {
                log.info("resolveFile -> downloading {}...", uri);
                download(uri, p);
            }

            return p;
        }
    }

    private Artifact resolveMavenSingle(MavenDependency dep) throws IOException {
        RepositorySystemSession session = newRepositorySystemSession(maven);

        ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(dep.artifact);
        req.setRepositories(repositories);

        synchronized (mutex) {
            try {
                ArtifactResult r = maven.resolveArtifact(session, req);
                return r.getArtifact();
            } catch (ArtifactResolutionException e) {
                throw new IOException(e);
            }
        }
    }

    private Collection<Artifact> resolveMavenSingleDependencies(Collection<MavenDependency> deps) throws IOException {
        Collection<Artifact> paths = new HashSet<>();
        for (MavenDependency dep : deps) {
            paths.add(resolveMavenSingle(dep));
        }
        return paths;
    }

    private Collection<Artifact> resolveMavenTransitiveDependencies(Collection<MavenDependency> deps) throws IOException {
        RepositorySystem system = newMavenRepositorySystem();
        RepositorySystemSession session = newRepositorySystemSession(system);

        CollectRequest req = new CollectRequest();
        req.setDependencies(deps.stream()
                .map(d -> new Dependency(d.artifact, d.scope))
                .collect(Collectors.toList()));
        req.setRepositories(repositories);

        DependencyRequest dependencyRequest = new DependencyRequest(req, null);

        synchronized (mutex) {
            try {
                return system.resolveDependencies(session, dependencyRequest)
                        .getArtifactResults().stream()
                        .map(ArtifactResult::getArtifact)
                        .collect(Collectors.toSet());
            } catch (DependencyResolutionException e) {
                throw new IOException(e);
            }
        }
    }

    private DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) throws IOException {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setCache(mavenCache);
        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_IGNORE);

        LocalRepository localRepo = new LocalRepository(localCacheDir.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        session.setTransferListener(new AbstractTransferListener() {
            @Override
            public void transferFailed(TransferEvent event) {
                log.error("transferFailed -> {}", event);
            }
        });
        session.setRepositoryListener(new AbstractRepositoryListener() {
            @Override
            public void artifactResolving(RepositoryEvent event) {
                log.debug("artifactResolving -> {}", event);
            }

            @Override
            public void artifactResolved(RepositoryEvent event) {
                log.debug("artifactResolved -> {}", event);
            }
        });

        return session;
    }

    private String appendVersion(String dep) {
        if (!isOfficialPlugin(dep)) {
            throw new IllegalArgumentException("Unofficial plugin '" + dep + "': version is required");
        }
        return dep + ":" + pluginsVersion.get(dep);
    }

    private boolean isOfficialPlugin(String s) {
        return pluginsVersion.containsKey(s);
    }

    private Boolean hasVersion(String s) {
        return s.matches(MVN_VERSION_PATTERN);
    }

    private static DependencyEntity toDependency(Artifact artifact) {
        return new DependencyEntity(artifact.getFile().toPath(),
                artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    private static void download(URI uri, Path dst) throws IOException {
        try (InputStream in = uri.toURL().openStream();
             OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] ab = new byte[4096];
            int read;
            while ((read = in.read(ab)) > 0) {
                out.write(ab, 0, read);
            }
        }
    }

    private static String getLastPart(URI uri) {
        String p = uri.getPath();
        int idx = p.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < p.length()) {
            return p.substring(idx + 1);
        }
        throw new IllegalArgumentException("Invalid dependency URL. Can't get a file name: " + uri);
    }

    private static boolean shouldSkipCache(URI u) {
        return "file".equalsIgnoreCase(u.getScheme()) || u.getPath().contains("SNAPSHOT");
    }

    private static RepositorySystem newMavenRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                log.error("newMavenRepositorySystem -> service creation error: type={}, impl={}", type, impl, exception);
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    private static List<RemoteRepository> toRemote(List<MavenRepository> l) {
        return l.stream()
                .map(r -> {
                    RemoteRepository.Builder result = new RemoteRepository.Builder(r.getId(), r.getContentType(), r.getUrl());
                    if (!r.isSnapshotEnabled()) {
                        result.setSnapshotPolicy(new RepositoryPolicy(false, UPDATE_POLICY_NEVER, CHECKSUM_POLICY_IGNORE));
                    }
                    return result.build();
                })
                .collect(Collectors.toList());
    }

    private static Map<String, String> splitQuery(URI uri) throws UnsupportedEncodingException {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> m = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String k = pair.substring(0, idx);
            String v = pair.substring(idx + 1);
            m.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<MavenRepository> readCfg() {
        String s = System.getenv(CFG_FILE_KEY);
        if (s == null || s.trim().isEmpty()) {
            return DEFAULT_REPOS;
        }

        Path p = Paths.get(s);
        if (!Files.exists(p)) {
            log.warn("readCfg -> file not found: {}, using the default repos", s);
            return DEFAULT_REPOS;
        }

        Map<String, Object> m;
        try (InputStream in = Files.newInputStream(p)) {
            ObjectMapper om = new ObjectMapper();
            m = om.readValue(in, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading the Maven configuration file: " + s, e);
        }

        Object v = m.get("repositories");
        if (v == null) {
            return DEFAULT_REPOS;
        }

        if (!(v instanceof Collection)) {
            throw new RuntimeException("The 'repositories' value should be an array of objects: " + s);
        }

        Collection<Map<String, Object>> repos = (Collection<Map<String, Object>>) v;

        List<MavenRepository> l = new ArrayList<>();
        for (Map<String, Object> r : repos) {
            String id = (String) r.get("id");
            if (id == null) {
                throw new RuntimeException("Missing repository 'id' value: " + s);
            }

            String contentType = (String) r.getOrDefault("layout", "default");

            String url = (String) r.get("url");
            if (url == null) {
                throw new RuntimeException("Missing repository 'url' value: " + s);
            }

            l.add(new MavenRepository(id, contentType, url, true));
        }
        return l;
    }

    private static final class DependencyList {

        private final List<MavenDependency> mavenTransitiveDependencies;
        private final List<MavenDependency> mavenSingleDependencies;
        private final List<URI> directLinks;

        private DependencyList(List<MavenDependency> mavenTransitiveDependencies,
                               List<MavenDependency> mavenSingleDependencies,
                               List<URI> directLinks) {

            this.mavenTransitiveDependencies = mavenTransitiveDependencies;
            this.mavenSingleDependencies = mavenSingleDependencies;
            this.directLinks = directLinks;
        }
    }

    private static final class MavenDependency {

        private final Artifact artifact;
        private final String scope;

        private MavenDependency(Artifact artifact, String scope) {
            this.artifact = artifact;
            this.scope = scope;
        }
    }
}
