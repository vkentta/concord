package com.walmartlabs.concord.agent.executors.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.agent.ExecutionException;
import com.walmartlabs.concord.agent.JobRequest;
import com.walmartlabs.concord.agent.executors.runner.RunnerJobExecutor.RunnerJobExecutorConfiguration;
import com.walmartlabs.concord.agent.logging.ProcessLogFactory;
import com.walmartlabs.concord.agent.logging.RedirectedProcessLog;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.runner.model.ApiConfiguration;
import com.walmartlabs.concord.runner.model.DockerConfiguration;
import com.walmartlabs.concord.runner.model.ImmutableRunnerConfiguration;
import com.walmartlabs.concord.runner.model.RunnerConfiguration;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class RunnerJob {

    @SuppressWarnings("unchecked")
    public static RunnerJob from(RunnerJobExecutorConfiguration runnerExecutorCfg, JobRequest jobRequest, ProcessLogFactory processLogFactory) throws ExecutionException {
        Map<String, Object> cfg = Collections.emptyMap();

        Path payloadDir = jobRequest.getPayloadDir();

        Path p = payloadDir.resolve(InternalConstants.Files.REQUEST_DATA_FILE_NAME);
        if (Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                cfg = new ObjectMapper().readValue(in, Map.class);
            } catch (IOException e) {
                throw new ExecutionException("Error while reading process configuration", e);
            }
        }

        RunnerConfiguration runnerCfg = createRunnerConfiguration(runnerExecutorCfg, cfg);
        RedirectedProcessLog log = processLogFactory.createRedirectedLog(jobRequest.getInstanceId());

        return new RunnerJob(jobRequest.getInstanceId(), payloadDir, cfg, runnerCfg, log);
    }

    private final UUID instanceId;
    private final Path payloadDir;
    private final Map<String, Object> processCfg;
    private final RunnerConfiguration runnerCfg;
    private final boolean debugMode;
    private final RedirectedProcessLog log;

    private RunnerJob(UUID instanceId, Path payloadDir, Map<String, Object> processCfg, RunnerConfiguration runnerCfg, RedirectedProcessLog log) {
        this.instanceId = instanceId;
        this.payloadDir = payloadDir;
        this.processCfg = processCfg;
        this.runnerCfg = runnerCfg;
        this.debugMode = debugMode(processCfg);
        this.log = log;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Path getPayloadDir() {
        return payloadDir;
    }

    public Map<String, Object> getProcessCfg() {
        return processCfg;
    }

    public RunnerConfiguration getRunnerCfg() {
        return runnerCfg;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public RedirectedProcessLog getLog() {
        return log;
    }

    private static boolean debugMode(Map<String, Object> processCfg) {
        Object v = processCfg.get(Constants.Request.DEBUG_KEY);
        if (v instanceof String) {
            // allows `curl ... -F debug=true`
            return Boolean.parseBoolean((String) v);
        }

        return Boolean.TRUE.equals(v);
    }

    public RunnerJob withDependencies(Collection<String> resolvedDeps) {
        if (resolvedDeps == null) {
            resolvedDeps = Collections.emptyList();
        }

        RunnerConfiguration cfg = RunnerConfiguration.builder().from(runnerCfg)
                .dependencies(resolvedDeps)
                .build();

        // TODO replace with immutables?
        return new RunnerJob(instanceId, payloadDir, processCfg, cfg, log);
    }

    @Override
    public String toString() {
        return "RunnerJob{" +
                "instanceId=" + instanceId +
                ", debugMode=" + debugMode +
                '}';
    }

    private static RunnerConfiguration createRunnerConfiguration(RunnerJobExecutorConfiguration execCfg, Map<String, Object> processCfg) {
        ImmutableRunnerConfiguration.Builder b = RunnerConfiguration.builder();

        ApiConfiguration apiCfgSrc = ApiConfiguration.builder().build();
        DockerConfiguration dockerCfgSrc = DockerConfiguration.builder().build();

        Object v = processCfg.get(InternalConstants.Request.RUNNER_KEY);
        if (v != null) {
            RunnerConfiguration src = createObjectMapper().convertValue(v, RunnerConfiguration.class);

            // grab whatever we had in the process configuration
            apiCfgSrc = src.api();
            dockerCfgSrc = src.docker();

            b = b.from(src);
        }

        // override system values
        // TODO simplify somehow?
        return b.agentId(execCfg.getAgentId())
                .debug(debugMode(processCfg))
                .api(ApiConfiguration.builder().from(apiCfgSrc)
                        .baseUrl(execCfg.getServerApiBaseUrl())
                        .maxNoHeartbeatInterval(execCfg.getMaxNoHeartbeatInterval())
                        .build())
                .docker(DockerConfiguration.builder().from(dockerCfgSrc)
                        .extraVolumes(execCfg.getExtraDockerVolumes())
                        .build())
                .build();
    }

    // TODO reuse the same ObjectMapper instance?
    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new GuavaModule());
        om.registerModule(new Jdk8Module());
        return om;
    }
}
