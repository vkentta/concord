package com.walmartlabs.concord.it.server;

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

import com.googlecode.junittoolbox.ParallelRunner;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

import static org.junit.Assert.*;

@RunWith(ParallelRunner.class)
public class CrudIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOrgUpdate() throws Exception {
        String orgName = "org_" + randomString();
        Map<String, Object> meta = Collections.singletonMap("x", "123");

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry()
                .setName(orgName)
                .setMeta(meta)
                .setVisibility(OrganizationEntry.VisibilityEnum.PUBLIC));

        // ---

        OrganizationEntry e = orgApi.get(orgName);
        assertNotNull(e.getMeta());
        assertEquals("123", e.getMeta().get("x"));

        // ---

        orgApi.createOrUpdate(e.setMeta(Collections.singletonMap("x", "234")));

        // ---

        e = orgApi.get(orgName);
        assertNotNull(e.getMeta());
        assertEquals("234", e.getMeta().get("x"));
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testProject() throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String orgName = "Default";
        String projectName = "project_" + randomString();
        String updateProjectName = "updateProject_" + randomString();

        // --- create

        ProjectOperationResponse createResp = projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName));
        assertTrue(createResp.isOk());
        assertNotNull(createResp.getId());

        // --- update

        ProjectOperationResponse updateResp = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setId(createResp.getId())
                .setName(updateProjectName));
        assertEquals(updateResp.getResult(), ProjectOperationResponse.ResultEnum.UPDATED);
        assertEquals(createResp.getId(), updateResp.getId());

        // --- get

        ProjectEntry projectEntry = projectsApi.get(orgName, updateProjectName);
        assertEquals(projectEntry.getId(), createResp.getId());

        // --- create

        createResp = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.emptyMap()));
        assertTrue(createResp.isOk());

        // --- list

        List<ProjectEntry> projectList = projectsApi.list(orgName);
        projectEntry = findProject(projectList, projectName);
        assertNotNull(projectEntry);

        // --- delete

        GenericOperationResult deleteResp = projectsApi.delete(orgName, projectName);
        assertTrue(deleteResp.isOk());
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testNonUniqueRepositoryNames() throws Exception {
        String orgName = "Default";

        String projectName1 = "project1_" + randomString();
        String projectName2 = "project2_" + randomString();

        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName1)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl("n/a"))));

        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName2)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl("n/a"))));
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testInventory() throws Exception {
        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String updatedInventoryName = "updateInventory_" + randomString();

        // --- create

        CreateInventoryResponse cir = inventoriesApi.createOrUpdate(orgName, new InventoryEntry().setName(inventoryName));
        assertTrue(cir.isOk());
        assertNotNull(cir.getId());

        // --- update

        CreateInventoryResponse updateInventoryResponse = inventoriesApi.createOrUpdate(orgName,
                new InventoryEntry()
                        .setId(cir.getId())
                        .setName(updatedInventoryName));
        assertEquals(updateInventoryResponse.getResult(), CreateInventoryResponse.ResultEnum.UPDATED);
        assertEquals(updateInventoryResponse.getId(), cir.getId());

        // --- get

        InventoryEntry inventoryEntry = inventoriesApi.get(orgName, updatedInventoryName);
        assertNotNull(inventoryEntry);

        // -- list

        List<InventoryEntry> l = inventoriesApi.list(orgName);
        assertTrue(l.stream().anyMatch(e -> updatedInventoryName.equals(e.getName()) && orgName.equals(e.getOrgName())));

        // --- delete

        GenericOperationResult deleteInventoryResponse = inventoriesApi.delete(orgName, updatedInventoryName);
        assertTrue(deleteInventoryResponse.getResult() == GenericOperationResult.ResultEnum.DELETED);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testInventoryData() throws Exception {
        InventoryDataApi dataApi = new InventoryDataApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String itemPath = "/a";
        Map<String, Object> data = Collections.singletonMap("k", "v");

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());
        inventoriesApi.createOrUpdate(orgName, new InventoryEntry().setName(inventoryName));

        // --- create

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) dataApi.data(orgName, inventoryName, itemPath, data);
        assertNotNull(result);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- get

        @SuppressWarnings("unchecked")
        Map<String, Object> result2 = (Map<String, Object>) dataApi.get(orgName, inventoryName, itemPath, false);
        assertNotNull(result2);
        assertEquals(Collections.singletonMap("a", data), result);

        // --- delete

        DeleteInventoryDataResponse didr = dataApi.delete(orgName, inventoryName, itemPath);
        assertNotNull(didr);
        assertTrue(didr.isOk());
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    @SuppressWarnings("unchecked")
    public void testInventoryQuery() throws Exception {
        InventoryQueriesApi queriesApi = new InventoryQueriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String queryName = "queryName_" + randomString();
        String text = "select * from test_" + randomString();

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());
        inventoriesApi.createOrUpdate(orgName, new InventoryEntry().setName(inventoryName));

        // ---

        InventoryDataApi dataApi = new InventoryDataApi(getApiClient());
        dataApi.data(orgName, inventoryName, "/test", Collections.singletonMap("k", "v"));

        // --- create

        CreateInventoryQueryResponse cqr = queriesApi.createOrUpdate(orgName, inventoryName, queryName, text);
        assertTrue(cqr.isOk());
        assertNotNull(cqr.getId());

        // --- update
        String updatedText = "select item_data::text from inventory_data";
        CreateInventoryQueryResponse uqr = queriesApi.createOrUpdate(orgName, inventoryName, queryName, updatedText);
        assertTrue(uqr.isOk());
        assertNotNull(uqr.getId());

        // --- get
        InventoryQueryEntry e1 = queriesApi.get(orgName, inventoryName, queryName);
        assertNotNull(e1);
        assertNotNull(e1.getId());
        assertEquals(queryName, e1.getName());
        assertEquals(updatedText, e1.getText());

        // --- list
        List<InventoryQueryEntry> list = queriesApi.list(orgName, inventoryName);
        assertTrue(list.stream().anyMatch(e -> queryName.equals(e.getName()) && updatedText.equals(e.getText())));

        // --- exec
        @SuppressWarnings("unchecked")
        List<Object> result = queriesApi.exec(orgName, inventoryName, queryName, null);
        assertNotNull(result);
        Map<String, Object> m = (Map<String, Object>) result.get(0);
        assertEquals(Collections.singletonMap("k", "v"), m);

        // --- delete
        DeleteInventoryQueryResponse dqr = queriesApi.delete(orgName, inventoryName, queryName);
        assertNotNull(dqr);
        assertTrue(dqr.isOk());
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testInvalidQueryName() throws Exception {
        InventoryQueriesApi queriesApi = new InventoryQueriesApi(getApiClient());

        String orgName = "Default";
        String inventoryName = "inventory_" + randomString();
        String queryName = "queryName_" + randomString();

        InventoriesApi inventoriesApi = new InventoriesApi(getApiClient());
        inventoriesApi.createOrUpdate(orgName, new InventoryEntry().setName(inventoryName));

        // ---

        try {
            queriesApi.exec(orgName, inventoryName, queryName, null);
            fail("should fail");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("not found") && e.getMessage().contains(queryName));
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testDashes() throws Exception {
        String orgName = randomString() + "-test~";

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        orgApi.get(orgName);

        // ---

        String teamName = randomString() + "-test~";

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));

        teamsApi.get(orgName, teamName);

        // ---

        String projectName = randomString() + "-test~";

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName));

        projectsApi.get(orgName, projectName);

        // ---

        String secretName = randomString() + "-test~";
        addPlainSecret(orgName, secretName, true, null, new byte[]{0, 1, 2, 3});

        SecretsApi secretResource = new SecretsApi(getApiClient());
        secretResource.delete(orgName, secretName);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testTeam() throws Exception {
        String teamName = "team_" + randomString();
        String orgName = "Default";
        TeamsApi teamsApi = new TeamsApi(getApiClient());

        // Create
        CreateTeamResponse teamResponse = teamsApi.createOrUpdate(orgName, new TeamEntry().setName(teamName));
        assertTrue(teamResponse.isOk());
        assertNotNull(teamResponse.getId());
        assertEquals(teamResponse.getResult(), CreateTeamResponse.ResultEnum.CREATED);

        // Update Team by Name
        CreateTeamResponse updateTeamResponse = teamsApi.createOrUpdate(orgName, new TeamEntry()
                .setName(teamName)
                .setDescription("Update Description"));
        assertEquals(updateTeamResponse.getId(), teamResponse.getId());
        assertEquals(updateTeamResponse.getResult(), CreateTeamResponse.ResultEnum.UPDATED);

        // Update Team by ID
        String updatedTeamName = "UpdatedName_" + randomString();
        CreateTeamResponse updateTeamById = teamsApi.createOrUpdate(orgName, new TeamEntry()
                .setId(teamResponse.getId())
                .setName(updatedTeamName)
                .setDescription("Name is updated"));
        assertEquals(teamResponse.getId(), updateTeamById.getId());
        assertEquals(updateTeamResponse.getResult(), CreateTeamResponse.ResultEnum.UPDATED);

        // Get
        TeamEntry teamEntry = teamsApi.get(orgName, updatedTeamName);
        assertEquals(teamResponse.getId(), teamEntry.getId());
        assertEquals(updatedTeamName, teamEntry.getName());

    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testSecrets() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        String secretName = "secret_" + randomString();
        addPlainSecret(orgName, secretName, false, null, "hey".getBytes());

        // ---

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName)
                        .setUrl("git@test:/test")
                        .setSecretName(secretName))));

        // ---

        SecretsApi secretsApi = new SecretsApi(getApiClient());
        secretsApi.delete(orgName, secretName);

        /// ---

        ProjectEntry projectEntry = projectsApi.get(orgName, projectName);
        Map<String, RepositoryEntry> repos = projectEntry.getRepositories();
        assertEquals(1, repos.size());

        RepositoryEntry repo = repos.get(repoName);
        assertNotNull(repo);
        assertNull(repo.getSecretName());
    }

    private static ProjectEntry findProject(List<ProjectEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOrganization() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        String updatedOrgName = "updateOrg_" + randomString();

        // --- create

        CreateOrganizationResponse createOrganizationResponse = orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));
        assertTrue(createOrganizationResponse.isOk());
        assertNotNull(createOrganizationResponse.getId());

        // --- update

        CreateOrganizationResponse updateOrganizationResponse = orgApi.createOrUpdate(new OrganizationEntry()
                .setId(createOrganizationResponse.getId())
                .setName(updatedOrgName));
        assertEquals(updateOrganizationResponse.getResult(), CreateOrganizationResponse.ResultEnum.UPDATED);
        assertEquals(updateOrganizationResponse.getId(), createOrganizationResponse.getId());

        // --- get

        OrganizationEntry organizationEntry = orgApi.get(updatedOrgName);
        assertNotNull(organizationEntry);
        assertEquals(createOrganizationResponse.getId(), organizationEntry.getId());

        // --- list

        List<OrganizationEntry> organizationEntryList = orgApi.list(true);
        assertNotNull(organizationEntryList);
        organizationEntry = findOrganization(organizationEntryList, updatedOrgName);
        assertNotNull(organizationEntry);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOrgVisibility() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();

        // create private org

        CreateOrganizationResponse createOrganizationResponse = orgApi.createOrUpdate(new OrganizationEntry()
                .setName(orgName)
                .setVisibility(OrganizationEntry.VisibilityEnum.PRIVATE));
        assertTrue(createOrganizationResponse.isOk());
        assertNotNull(createOrganizationResponse.getId());

        // --- private org available for admin

        List<OrganizationEntry> orgs = orgApi.list(false);
        assertTrue(orgs.stream().anyMatch(e -> e.getId().equals(createOrganizationResponse.getId())));

        // add the user A

        UsersApi usersApi = new UsersApi(getApiClient());

        String userAName = "userA_" + randomString();
        usersApi.createOrUpdate(new CreateUserRequest().setUsername(userAName).setType(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyA = apiKeyResource.create(new CreateApiKeyRequest().setUsername(userAName));

        setApiKey(apiKeyA.getKey());

        orgs = orgApi.list(true);
        assertTrue(orgs.stream().noneMatch(e -> e.getId().equals(createOrganizationResponse.getId())));
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOrgMeta() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        Map<String, Object> meta = Collections.singletonMap("x", true);

        CreateOrganizationResponse cor = orgApi.createOrUpdate(new OrganizationEntry()
                .setName(orgName)
                .setMeta(meta));

        // ---

        OrganizationEntry e = orgApi.get(orgName);
        assertNotNull(e);

        Map<String, Object> meta2 = e.getMeta();
        assertNotNull(meta2);
        assertEquals(meta.get("x"), meta2.get("x"));

        // ---

        meta = Collections.singletonMap("y", 123.0);
        orgApi.createOrUpdate(new OrganizationEntry()
                .setId(cor.getId())
                .setMeta(meta));

        e = orgApi.get(orgName);
        assertNotNull(e);

        Map<String, Object> meta3 = e.getMeta();
        assertNotNull(meta3);
        assertEquals(1, meta3.size());
        assertEquals(meta.get("y"), meta3.get("y"));
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testPolicies() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String projectName = "project_" + randomString();
        projectsApi.createOrUpdate(orgName, new ProjectEntry().setName(projectName));

        // ---

        String userName = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse createUserResponse = usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(userName)
                .setType(CreateUserRequest.TypeEnum.LOCAL));

        // ---

        PolicyApi policyResource = new PolicyApi(getApiClient());

        String policyName = "policy_" + randomString();
        Map<String, Object> policyRules = Collections.singletonMap("x", 123);

        PolicyOperationResponse por = policyResource.createOrUpdate(new PolicyEntry()
                .setName(policyName)
                .setRules(policyRules));

        String newPolicyName = "policy2_" + randomString();
        policyResource.createOrUpdate(new PolicyEntry()
                .setId(por.getId())
                .setName(newPolicyName)
                .setRules(policyRules));

        String policyForUser = "policy3_" + randomString();
        policyResource.createOrUpdate(new PolicyEntry()
                .setName(policyForUser)
                .setRules(policyRules));

        policyResource.createOrUpdate(new PolicyEntry()
                .setId(por.getId())
                .setName(policyName)
                .setRules(policyRules));

        // ---

        PolicyEntry pe = policyResource.get(policyName);
        assertNotNull(pe);

        // ---

        policyResource.link(policyName, new PolicyLinkEntry()
                .setOrgName(orgName)
                .setProjectName(projectName));

        List<PolicyEntry> l = policyResource.list(orgName, projectName, null, null, null);
        assertEquals(1, l.size());
        assertEquals(policyName, l.get(0).getName());

        // ---

        policyResource.link(policyName, new PolicyLinkEntry().setOrgName(orgName));

        l = policyResource.list(orgName, null, null, null, null);
        assertEquals(1, l.size());
        assertEquals(policyName, l.get(0).getName());

        // ---

        policyResource.link(policyName, new PolicyLinkEntry().setUserName(userName));

        l = policyResource.list(null, null, userName, null, null);
        assertEquals(1, l.size());
        assertEquals(policyName, l.get(0).getName());

        // ---

        policyResource.link(policyForUser, new PolicyLinkEntry()
                .setOrgName(orgName)
                .setProjectName(projectName)
                .setUserName(userName));

        l = policyResource.list(orgName, projectName, userName, null, null);
        assertEquals(1, l.size());
        assertEquals(policyForUser, l.get(0).getName());

        // ---

        policyResource.unlink(policyName, orgName, projectName, null, null, null);
        l = policyResource.list(orgName, projectName, null, null, null);
        assertEquals(1, l.size());
        l = policyResource.list(orgName, null, null, null, null);
        assertEquals(1, l.size());

        // ---

        policyResource.unlink(policyName, orgName, null, null, null, null);
        l = policyResource.list(orgName, projectName, null, null, null);
        assertEquals(0, l.size());
        l = policyResource.list(orgName, null, null, null, null);
        assertEquals(0, l.size());

        // ---

        policyResource.unlink(policyName, null, null, userName, null, null);
        l = policyResource.list(null, null, userName, null, null);
        assertEquals(0, l.size());
        l = policyResource.list(orgName, null, null, null, null);
        assertEquals(0, l.size());

        // ---

        policyResource.unlink(policyForUser, orgName, projectName, userName, null, null);
        l = policyResource.list(orgName, projectName, userName, null, null);
        assertEquals(0, l.size());
        l = policyResource.list(orgName, null, null, null, null);
        assertEquals(0, l.size());

        // ---

        policyResource.delete(policyName);
        l = policyResource.list(null, null, null, null, null);
        for (PolicyEntry e : l) {
            if (policyName.equals(e.getName())) {
                fail("Should've been removed: " + e.getName());
            }
        }

        usersApi.delete(createUserResponse.getId());
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testRoles() throws Exception {
        RolesApi rolesApi = new RolesApi(getApiClient());

        String roleName = "role_" + randomString();

        // --- create

        RoleOperationResponse createRoleResponse = rolesApi.createOrUpdate(new RoleEntry().setName(roleName));
        assertEquals(RoleOperationResponse.ResultEnum.CREATED, createRoleResponse.getResult());
        assertNotNull(createRoleResponse.getId());

        // --- update

        RoleOperationResponse updateRoleResponse = rolesApi.createOrUpdate(new RoleEntry()
                .setId(createRoleResponse.getId())
                .setName(roleName)
                .setPermissions(Collections.singletonList("getProcessQueueAllOrgs")));
        assertEquals(RoleOperationResponse.ResultEnum.UPDATED, updateRoleResponse.getResult());

        // --- get

        RoleEntry roleEntry = rolesApi.get(roleName);
        assertNotNull(roleEntry);
        assertEquals(createRoleResponse.getId(), roleEntry.getId());
        assertEquals("getProcessQueueAllOrgs", roleEntry.getPermissions().get(0));

        // --- list

        List<RoleEntry> roleEntryList = rolesApi.list();
        assertNotNull(roleEntryList);

        GenericOperationResult r = rolesApi.delete(roleName);
        assertEquals(GenericOperationResult.ResultEnum.DELETED, r.getResult());
    }

    private static OrganizationEntry findOrganization(List<OrganizationEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().get();
    }
}
