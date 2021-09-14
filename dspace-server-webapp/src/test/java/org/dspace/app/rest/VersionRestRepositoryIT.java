/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;
import static com.jayway.jsonpath.JsonPath.read;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.dspace.app.rest.matcher.ItemMatcher;
import org.dspace.app.rest.matcher.VersionMatcher;
import org.dspace.app.rest.model.patch.AddOperation;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.RemoveOperation;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.VersionBuilder;
import org.dspace.builder.WorkflowItemBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.eperson.EPerson;
import org.dspace.services.ConfigurationService;
import org.dspace.versioning.Version;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.RestMediaTypes;
import org.springframework.http.MediaType;

/**
 * Integration test class for the version endpoint.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
public class VersionRestRepositoryIT extends AbstractControllerIntegrationTest {

    private Item item;

    private Version version;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private InstallItemService installItemService;

    @Autowired
    private WorkspaceItemService workspaceItemService;

    @Before
    public void setup() throws SQLException, AuthorizeException {
        //disable file upload mandatory
        configurationService.setProperty("webui.submit.upload.required", false);
        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community with sub-community and two collections.
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                                           .withName("Sub Community")
                                           .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();

        //2. Three public items that are readable by Anonymous with different subjects
        item = ItemBuilder.createItem(context, col1)
                          .withTitle("Public item 1")
                          .withIssueDate("2017-10-17")
                          .withAuthor("Smith, Donald").withAuthor("Doe, John")
                          .withSubject("ExtraEntry")
                          .build();

        version = VersionBuilder.createVersion(context, item, "Fixing some typos").build();
        context.restoreAuthSystemState();
    }

    @Test
    public void findOneTest() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/versioning/versions/" + version.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.is(VersionMatcher.matchEntry(version))))
                             .andExpect(jsonPath("$._links.versionhistory.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + version.getID() + "/versionhistory"))))
                             .andExpect(jsonPath("$._links.item.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + version.getID() + "/item"))))
                             .andExpect(jsonPath("$._links.self.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + version.getID()))));
    }

    @Test
    public void findOneSubmitterNameVisisbleTest() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/versioning/versions/" + version.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.is(VersionMatcher.matchEntry(version))))
                             .andExpect(jsonPath("$.submitterName", Matchers.is(version.getEPerson().getFullName())));
    }

    @Test
    public void findOneSubmitterNameConfigurationPropertyFalseAdminUserLinkVisibleTest() throws Exception {

        configurationService.setProperty("versioning.item.history.include.submitter", false);

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/versioning/versions/" + version.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.is(VersionMatcher.matchEntry(version))))
                             .andExpect(jsonPath("$.submitterName", Matchers.is(version.getEPerson().getFullName())));

        configurationService.setProperty("versioning.item.history.include.submitter", true);

    }

    @Test
    public void findOneSubmitterNameConfigurationPropertyTrueNormalUserLinkVisibleTest() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/versioning/versions/" + version.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.is(VersionMatcher.matchEntry(version))))
                             .andExpect(jsonPath("$.submitterName", Matchers.is(version.getEPerson().getFullName())));

    }

    @Test
    public void findOneSubmitterNameConfigurationPropertyTrueAnonUserLinkVisibleTest() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/versioning/versions/" + version.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.is(VersionMatcher.matchEntry(version))))
                             .andExpect(jsonPath("$.submitterName", Matchers.is(version.getEPerson().getFullName())));

    }

    @Test
    public void findOneSubmitterNameConfigurationPropertyFalseNormalUserLinkInvisibleTest() throws Exception {

        configurationService.setProperty("versioning.item.history.include.submitter", false);

        String adminToken = getAuthToken(eperson.getEmail(), password);

        getClient(adminToken).perform(get("/api/versioning/versions/" + version.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.is(VersionMatcher.matchEntry(version))))
                             .andExpect(jsonPath("$.submitterName").doesNotExist());

        configurationService.setProperty("versioning.item.history.include.submitter", true);

    }
    @Test
    public void findOneUnauthorizedTest() throws Exception {

        getClient().perform(get("/api/versioning/versions/" + version.getID()))
                   .andExpect(status().isUnauthorized());
    }

    @Test
    public void findOneForbiddenTest() throws Exception {

        configurationService.setProperty("versioning.item.history.view.admin", true);

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/versioning/versions/" + version.getID()))
                        .andExpect(status().isForbidden());
        configurationService.setProperty("versioning.item.history.view.admin", false);
    }

    @Test
    public void versionForItemTest() throws Exception {

        context.turnOffAuthorisationSystem();
        WorkspaceItem workspaceItem = workspaceItemService.findByItem(context, version.getItem());
        installItemService.installItem(context, workspaceItem);
        context.restoreAuthSystemState();
        getClient().perform(get("/api/core/items/" + version.getItem().getID() + "/version"))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$", Matchers.is(VersionMatcher.matchEntry(version))));
    }

    @Test
    public void versionItemTest() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/versioning/versions/" + version.getID() + "/item"))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.is(ItemMatcher.matchItemProperties(version.getItem()))));
    }

    @Test
    public void versionItemTestWrongId() throws Exception {

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/versioning/versions/" + ((version.getID() + 5) * 57) + "/item"))
                             .andExpect(status().isNotFound());
    }

    @Test
    public void createFirstVersionItemTest() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test")
                                          .build();

        Item item = ItemBuilder.createItem(context, col)
                               .withTitle("Public test item")
                               .withIssueDate("2021-04-27")
                               .withAuthor("Doe, John")
                               .withSubject("ExtraEntry")
                               .build();

        context.restoreAuthSystemState();

        AtomicReference<Integer> idRef = new AtomicReference<Integer>();
        String adminToken = getAuthToken(admin.getEmail(), password);

        try {
            getClient(adminToken).perform(post("/api/versioning/versions")
                                 .param("summary", "test summary!")
                                 .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                                 .content("/api/core/items/" + item.getID()))
                                 .andExpect(status().isCreated())
                                 .andExpect(jsonPath("$", Matchers.allOf(
                                            hasJsonPath("$.version", is(2)),
                                            hasJsonPath("$.summary", is("test summary!")),
                                            hasJsonPath("$.submitterName", is("first (admin) last (admin)")),
                                            hasJsonPath("$.type", is("version"))
                                            )))
                                 .andDo(result -> idRef.set(read(result.getResponse().getContentAsString(), "$.id")));
        } finally {
            VersionBuilder.delete(idRef.get());
        }
    }

    @Test
    public void createFirstVersionItemBadRequestTest() throws Exception {
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(post("/api/versioning/versions")
                             .param("summary", "test summary!")
                             .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                             .content("/api/core/test/" + UUID.randomUUID()))
                             .andExpect(status().isBadRequest());
    }

    @Test
    public void createFirstVersionItemForbiddenTest() throws Exception {
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(post("/api/versioning/versions")
                               .param("summary", "test summary!")
                               .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                               .content("/api/core/items/" + item.getID()))
                               .andExpect(status().isForbidden());
    }

    @Test
    public void createFirstVersionItemUnauthorizedTest() throws Exception {
        getClient().perform(post("/api/versioning/versions")
                   .param("summary", "test summary!")
                   .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                   .content("/api/core/items/" + item.getID()))
                   .andExpect(status().isUnauthorized());
    }

    @Test
    public void createVersionFromVersionedItemTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test")
                                          .withSubmitterGroup(admin)
                                          .build();

        Item item = ItemBuilder.createItem(context, col)
                          .withTitle("Public test item")
                          .withIssueDate("2021-04-27")
                          .withAuthor("Doe, John")
                          .withSubject("ExtraEntry")
                          .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();
        Item lastVersionItem = v2.getItem();

        context.restoreAuthSystemState();

        AtomicReference<Integer> idRef = new AtomicReference<Integer>();
        String adminToken = getAuthToken(admin.getEmail(), password);

        // item that linked last version is not archived
        getClient(adminToken).perform(get("/api/core/items/" + lastVersionItem.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.inArchive", Matchers.is(false)));

        // retrieve the workspace item
        getClient(adminToken).perform(get("/api/submission/workspaceitems/search/item")
                             .param("uuid", String.valueOf(lastVersionItem.getID())))
                             .andExpect(status().isOk())
                             .andDo(result -> idRef.set(read(result.getResponse().getContentAsString(), "$.id")));

        // submit the workspaceitem to complete the deposit
        getClient(adminToken).perform(post(BASE_REST_SERVER_URL + "/api/workflow/workflowitems")
                             .content("/api/submission/workspaceitems/" + idRef.get())
                             .contentType(textUriContentType))
                             .andExpect(status().isCreated());

        // now the item is archived
        getClient(adminToken).perform(get("/api/core/items/" + lastVersionItem.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.inArchive", Matchers.is(true)));

        try {
            getClient(adminToken).perform(post("/api/versioning/versions")
                                 .param("summary", "test summary.")
                                 .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                                 .content("/api/core/items/" + v2.getItem().getID()))
                                 .andExpect(status().isCreated())
                                 .andExpect(jsonPath("$", Matchers.allOf(
                                            hasJsonPath("$.version", is(3)),
                                            hasJsonPath("$.summary", is("test summary.")),
                                            hasJsonPath("$.submitterName", is("first (admin) last (admin)")),
                                            hasJsonPath("$.type", is("version"))
                                            )))
                                 .andDo(result -> idRef.set(read(result.getResponse().getContentAsString(), "$.id")));
        } finally {
            VersionBuilder.delete(idRef.get());
        }
    }

    @Test
    public void createVersionByPreviousVersionRespectCurrentVersionTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test")
                                          .build();

        Item item = ItemBuilder.createItem(context, col)
                               .withTitle("Public test item")
                               .withIssueDate("2021-03-20")
                               .withAuthor("Doe, John")
                               .withSubject("ExtraEntry")
                               .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();
        Item lastVersionItem = v2.getItem();

        context.restoreAuthSystemState();

        AtomicReference<Integer> idRef = new AtomicReference<Integer>();
        String adminToken = getAuthToken(admin.getEmail(), password);

        // the first version item is archived
        getClient(adminToken).perform(get("/api/core/items/" + item.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.inArchive", Matchers.is(true)));

        // item that linked last version is not archived
        getClient(adminToken).perform(get("/api/core/items/" + lastVersionItem.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.inArchive", Matchers.is(false)));

        // if there is item not archived, we can not create new version
        getClient(adminToken).perform(post("/api/versioning/versions")
                             .param("summary", "check first version")
                             .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                             .content("/api/core/items/" + item.getID()))
                             .andExpect(status().isUnprocessableEntity());

        // retrieve the workspace item
        getClient(adminToken).perform(get("/api/submission/workspaceitems/search/item")
                             .param("uuid", String.valueOf(lastVersionItem.getID())))
                             .andExpect(status().isOk())
                             .andDo(result -> idRef.set(read(result.getResponse().getContentAsString(), "$.id")));

        // submit the workspaceitem to complete the deposit
        getClient(adminToken).perform(post(BASE_REST_SERVER_URL + "/api/workflow/workflowitems")
                             .content("/api/submission/workspaceitems/" + idRef.get())
                             .contentType(textUriContentType))
                             .andExpect(status().isCreated());

        // now the item is archived
        getClient(adminToken).perform(get("/api/core/items/" + lastVersionItem.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.inArchive", Matchers.is(true)));

        try {
            getClient(adminToken).perform(post("/api/versioning/versions")
                                 .param("summary", "check first version")
                                 .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                                 .content("/api/core/items/" + item.getID()))
                                 .andExpect(status().isCreated())
                                 .andExpect(jsonPath("$", Matchers.allOf(
                                            hasJsonPath("$.version", is(3)),
                                            hasJsonPath("$.summary", is("check first version")),
                                            hasJsonPath("$.submitterName", is("first (admin) last (admin)")),
                                            hasJsonPath("$.type", is("version"))
                                            )));
        } finally {
            VersionBuilder.delete(idRef.get());
        }
    }

    @Test
    public void createVersionWithLastVersionInSubmissionTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test")
                                          .build();

        Item item = ItemBuilder.createItem(context, col)
                               .withTitle("Public test item")
                               .withIssueDate("2021-03-20")
                               .withAuthor("Doe, John")
                               .withSubject("ExtraEntry")
                               .build();

        Version v = VersionBuilder.createVersion(context, item, "test summary").build();
        Item item2 = v.getItem();

        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        // check that the item is deposited
        getClient(adminToken).perform(get("/api/core/items/" + item.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.uuid", Matchers.is(item.getID().toString())))
                             .andExpect(jsonPath("$.withdrawn", Matchers.is(false)))
                             .andExpect(jsonPath("$.inArchive", Matchers.is(true)));

        // check that the item2 is not deposited yet
        getClient(adminToken).perform(get("/api/core/items/" + item2.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.uuid", Matchers.is(item2.getID().toString())))
                             .andExpect(jsonPath("$.withdrawn", Matchers.is(false)))
                             .andExpect(jsonPath("$.inArchive", Matchers.is(false)));

        // we can not create a new version because the item2 is in submission
        getClient(adminToken).perform(post("/api/versioning/versions")
                             .param("summary", "check first version")
                             .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                             .content("/api/core/items/" + item.getID()))
                             .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void createVersionFromWorkflowItemTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        @SuppressWarnings("deprecation")
        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withWorkflowGroup(1, admin)
                                          .withName("Collection test").build();

        XmlWorkflowItem workflowItem = WorkflowItemBuilder.createWorkflowItem(context, col)
                                                          .withTitle("Workflow Item 1")
                                                          .withIssueDate("2017-10-17")
                                                          .build();

        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(post("/api/versioning/versions")
                             .param("summary", "fix workspaceitem")
                             .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                             .content("/api/core/items/" + workflowItem.getItem().getID()))
                             .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void createVersionFromWorkspaceItemTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test").build();

        WorkspaceItem witem = WorkspaceItemBuilder.createWorkspaceItem(context, col)
                                                  .withTitle("Workspace Item 1")
                                                  .withIssueDate("2017-10-17")
                                                  .withAuthor("Smith, Donald").withAuthor("Doe, John")
                                                  .withSubject("ExtraEntry")
                                                  .build();

        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(post("/api/versioning/versions")
                             .param("summary", "fix workspaceitem")
                             .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                             .content("/api/core/items/" + witem.getItem().getID()))
                             .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void createFirstVersionItemWithSubmitterTest() throws Exception {
        configurationService.setProperty("versioning.submitterCanCreateNewVersion", true);
        context.turnOffAuthorisationSystem();
        Community rootCommunity = CommunityBuilder.createCommunity(context)
                                                  .withName("Parent Community")
                                                  .build();

        Collection col = CollectionBuilder.createCollection(context, rootCommunity)
                                          .withName("Collection 1")
                                          .withSubmitterGroup(eperson)
                                          .build();

        Item itemA = ItemBuilder.createItem(context, col)
                               .withTitle("Public item")
                               .withIssueDate("2021-04-19")
                               .withAuthor("Doe, John")
                               .withSubject("ExtraEntry")
                               .build();

        itemA.setSubmitter(eperson);

        context.restoreAuthSystemState();

        AtomicReference<Integer> idRef = new AtomicReference<Integer>();
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        try {
            getClient(epersonToken).perform(post("/api/versioning/versions")
                                   .param("summary", "test summary!")
                                   .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                                   .content("/api/core/items/" + itemA.getID()))
                                   .andExpect(status().isCreated())
                                   .andExpect(jsonPath("$", Matchers.allOf(
                                              hasJsonPath("$.version", is(2)),
                                              hasJsonPath("$.summary", is("test summary!")),
                                              hasJsonPath("$.type", is("version"))
                                              )))
                                   .andDo(result -> idRef.set(read(result.getResponse().getContentAsString(), "$.id")));
        } finally {
            VersionBuilder.delete(idRef.get());
        }
        configurationService.setProperty("versioning.submitterCanCreateNewVersion", false);
    }

    @Test
    public void createFirstVersionItemWithSubmitterAndPropertyForSubmitterDisabledTest() throws Exception {
        configurationService.setProperty("versioning.submitterCanCreateNewVersion", false);
        context.turnOffAuthorisationSystem();
        Community rootCommunity = CommunityBuilder.createCommunity(context)
                                                  .withName("Parent Community")
                                                  .build();

        Collection col = CollectionBuilder.createCollection(context, rootCommunity)
                                          .withName("Collection 1")
                                          .withSubmitterGroup(eperson)
                                          .build();

        Item itemA = ItemBuilder.createItem(context, col)
                               .withTitle("Public item")
                               .withIssueDate("2021-04-19")
                               .withAuthor("Doe, John")
                               .withSubject("ExtraEntry")
                               .build();

        itemA.setSubmitter(eperson);

        context.restoreAuthSystemState();

        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(post("/api/versioning/versions")
                               .param("summary", "test summary!")
                               .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                               .content("/api/core/items/" + itemA.getID()))
                               .andExpect(status().isForbidden());
    }

    @Test
    public void patchReplaceSummaryTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test").build();

        Item item = ItemBuilder.createItem(context, col)
                          .withTitle("Public test item")
                          .withIssueDate("2021-04-27")
                          .withAuthor("Doe, John")
                          .withSubject("ExtraEntry")
                          .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test")
                                    .build();

        context.restoreAuthSystemState();

        String newSummary = "New Summary";
        List<Operation> ops = new ArrayList<Operation>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/summary", newSummary);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(patch("/api/versioning/versions/" + v2.getID())
                             .content(patchBody)
                             .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.allOf(
                                     hasJsonPath("$.version", is(v2.getVersionNumber())),
                                     hasJsonPath("$.summary", is(newSummary)),
                                     hasJsonPath("$.submitterName", is("first last")),
                                     hasJsonPath("$.type", is("version"))
                                     )))
                             .andExpect(jsonPath("$._links.self.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + v2.getID())
                                                 )))
                             .andExpect(jsonPath("$._links.item.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + v2.getID() + "/item")
                                                 )));

    }

    @Test
    public void patchRemoveSummaryTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test").build();

        Item item = ItemBuilder.createItem(context, col)
                          .withTitle("Public test item")
                          .withIssueDate("2021-04-27")
                          .withAuthor("Doe, John")
                          .withSubject("ExtraEntry")
                          .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test")
                                   .build();

        context.restoreAuthSystemState();

        List<Operation> ops = new ArrayList<Operation>();
        RemoveOperation replaceOperation = new RemoveOperation("/summary");
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(patch("/api/versioning/versions/" + v2.getID())
                             .content(patchBody)
                             .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.allOf(
                                     hasJsonPath("$.version", is(v2.getVersionNumber())),
                                     hasJsonPath("$.summary", emptyString()),
                                     hasJsonPath("$.submitterName", is("first last")),
                                     hasJsonPath("$.type", is("version"))
                                     )))
                           .andExpect(jsonPath("$._links.self.href", Matchers.allOf(Matchers.containsString(
                                               "api/versioning/versions/" + v2.getID())
                                               )))
                           .andExpect(jsonPath("$._links.item.href", Matchers.allOf(Matchers.containsString(
                                               "api/versioning/versions/" + v2.getID() + "/item")
                                               )));

    }

    @Test
    public void patchAddSummaryTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test").build();

        Item item = ItemBuilder.createItem(context, col)
                          .withTitle("Public test item")
                          .withIssueDate("2021-04-27")
                          .withAuthor("Doe, John")
                          .withSubject("ExtraEntry")
                          .build();

        Version v2 = VersionBuilder.createVersion(context, item, "").build();

        context.restoreAuthSystemState();

        String summary = "First Summary!";
        List<Operation> ops = new ArrayList<Operation>();
        AddOperation replaceOperation = new AddOperation("/summary", summary);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(patch("/api/versioning/versions/" + v2.getID())
                             .content(patchBody)
                             .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.allOf(
                                        hasJsonPath("$.version", is(v2.getVersionNumber())),
                                        hasJsonPath("$.summary", is(summary)),
                                        hasJsonPath("$.submitterName", is("first last")),
                                        hasJsonPath("$.type", is("version"))
                                        )))
                             .andExpect(jsonPath("$._links.self.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + v2.getID())
                                                 )))
                             .andExpect(jsonPath("$._links.item.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + v2.getID() + "/item")
                                                 )));
    }

    @Test
    public void patchVersionNotFoundTest() throws Exception {
        String summary = "Test Summary!";
        List<Operation> ops = new ArrayList<Operation>();
        AddOperation replaceOperation = new AddOperation("/summary", summary);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(patch("/api/versioning/versions/" + Integer.MAX_VALUE)
                             .content(patchBody)
                             .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                             .andExpect(status().isNotFound());
    }

    @Test
    public void patchAddSummaryBadRequestTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test").build();

        Item item = ItemBuilder.createItem(context, col)
                          .withTitle("Public test item")
                          .withIssueDate("2021-04-27")
                          .withAuthor("Doe, John")
                          .withSubject("ExtraEntry")
                          .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();

        context.restoreAuthSystemState();

        String summary = "First Summary!";
        List<Operation> ops = new ArrayList<Operation>();
        AddOperation replaceOperation = new AddOperation("/summary", summary);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(patch("/api/versioning/versions/" + v2.getID())
                             .content(patchBody)
                             .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                             .andExpect(status().isBadRequest());
    }

    @Test
    public void patchWrongPathUnprocessableEntityTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test").build();

        Item item = ItemBuilder.createItem(context, col)
                          .withTitle("Public test item")
                          .withIssueDate("2021-04-27")
                          .withAuthor("Doe, John")
                          .withSubject("ExtraEntry")
                          .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();

        context.restoreAuthSystemState();

        String summary = "First Summary!";
        List<Operation> ops = new ArrayList<Operation>();
        AddOperation replaceOperation = new AddOperation("/wrongPath", summary);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(patch("/api/versioning/versions/" + v2.getID())
                             .content(patchBody)
                             .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                             .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void patchReplaceVersionUnprocessableEntityTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test").build();

        Item item = ItemBuilder.createItem(context, col)
                               .withTitle("Public test item")
                               .withIssueDate("2021-04-27")
                               .withAuthor("Doe, John")
                               .withSubject("ExtraEntry")
                               .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();

        context.restoreAuthSystemState();

        String newVersion = "133";
        List<Operation> ops = new ArrayList<Operation>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/version", newVersion);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(patch("/api/versioning/versions/" + v2.getID())
                             .content(patchBody)
                             .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                             .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void patchReplaceSummaryUnauthorizedTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test")
                                          .build();

        Item item = ItemBuilder.createItem(context, col)
                               .withTitle("Public test item")
                               .withIssueDate("2021-04-27")
                               .withAuthor("Doe, John")
                               .withSubject("ExtraEntry")
                               .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();

        context.restoreAuthSystemState();

        String newSummary = "New Summary";
        List<Operation> ops = new ArrayList<Operation>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/summary", newSummary);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        getClient().perform(patch("/api/versioning/versions/" + v2.getID())
                   .content(patchBody)
                   .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                   .andExpect(status().isUnauthorized());
    }

    @Test
    public void patchRemoveSummaryUnauthorizedTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test").build();

        Item item = ItemBuilder.createItem(context, col)
                               .withTitle("Public test item")
                               .withIssueDate("2021-04-27")
                               .withAuthor("Doe, John")
                               .withSubject("ExtraEntry")
                               .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();

        context.restoreAuthSystemState();

        List<Operation> ops = new ArrayList<Operation>();
        RemoveOperation replaceOperation = new RemoveOperation("/summary");
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        getClient().perform(patch("/api/versioning/versions/" + v2.getID())
                   .content(patchBody)
                   .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                   .andExpect(status().isUnauthorized());
    }

    @Test
    public void patchRemoveSummaryForbiddenTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test").build();

        Item item = ItemBuilder.createItem(context, col)
                          .withTitle("Public test item")
                          .withIssueDate("2021-04-27")
                          .withAuthor("Doe, John")
                          .withSubject("ExtraEntry")
                          .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();

        context.restoreAuthSystemState();

        List<Operation> ops = new ArrayList<Operation>();
        RemoveOperation replaceOperation = new RemoveOperation("/summary");
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(patch("/api/versioning/versions/" + v2.getID())
                               .content(patchBody)
                               .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                               .andExpect(status().isForbidden());
    }

    @Test
    public void patchReplaceSummaryByCollectionAdminTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection title")
                                          .withAdminGroup(eperson)
                                          .build();

        Item item = ItemBuilder.createItem(context, col)
                               .withTitle("Public test item")
                               .withIssueDate("2021-03-08")
                               .withSubject("ExtraEntry")
                               .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();

        context.restoreAuthSystemState();

        String newSummary = "New Summary";
        List<Operation> ops = new ArrayList<Operation>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/summary", newSummary);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String colAdminToken = getAuthToken(eperson.getEmail(), password);
        getClient(colAdminToken).perform(patch("/api/versioning/versions/" + v2.getID())
                                .content(patchBody)
                                .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", Matchers.allOf(
                                        hasJsonPath("$.version", is(v2.getVersionNumber())),
                                        hasJsonPath("$.summary", is(newSummary)),
                                        hasJsonPath("$.type", is("version"))
                                        )));
    }

    @Test
    public void patchReplaceSummaryByCommunityAdminTest() throws Exception {
        context.turnOffAuthorisationSystem();

        EPerson adminCommA = EPersonBuilder.createEPerson(context)
                                           .withEmail("adminCommA@mail.com")
                                           .withPassword(password)
                                           .build();

        EPerson adminCommB = EPersonBuilder.createEPerson(context)
                                           .withEmail("adminCommB@mail.com")
                                           .withPassword(password)
                                           .build();

        Community rootCommunity = CommunityBuilder.createCommunity(context)
                                                  .withName("Parent Community")
                                                  .build();

        Community subCommunityA = CommunityBuilder.createSubCommunity(context, rootCommunity)
                                                  .withName("subCommunity A")
                                                  .withAdminGroup(adminCommA)
                                                  .build();

        CommunityBuilder.createSubCommunity(context, rootCommunity)
                        .withName("subCommunity B")
                        .withAdminGroup(adminCommB)
                        .build();

        Collection col = CollectionBuilder.createCollection(context, subCommunityA)
                                          .withName("Collection title")
                                          .build();

        Item item = ItemBuilder.createItem(context, col)
                               .withTitle("Public test item")
                               .withIssueDate("2021-03-08")
                               .withSubject("ExtraEntry")
                               .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();

        context.restoreAuthSystemState();

        String newSummary = "New Summary";
        List<Operation> ops = new ArrayList<Operation>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/summary", newSummary);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        String adminCommAToken = getAuthToken(adminCommA.getEmail(), password);
        String adminCommBToken = getAuthToken(adminCommB.getEmail(), password);

        getClient(adminCommBToken).perform(patch("/api/versioning/versions/" + v2.getID())
                                  .content(patchBody)
                                  .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                                  .andExpect(status().isForbidden());

        getClient(adminCommAToken).perform(get("/api/versioning/versions/" + v2.getID()))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$", Matchers.is(VersionMatcher.matchEntry(v2))));

        getClient(adminCommAToken).perform(patch("/api/versioning/versions/" + v2.getID())
                                  .content(patchBody)
                                  .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                                  .andExpect(status().isOk());

        getClient(adminCommAToken).perform(get("/api/versioning/versions/" + v2.getID()))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$", Matchers.allOf(
                                          hasJsonPath("$.version", is(v2.getVersionNumber())),
                                          hasJsonPath("$.summary", is(newSummary)),
                                          hasJsonPath("$.type", is("version"))
                                          )));
    }

    @Test
    public void deleteVersionTest() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                                          .withName("Collection test")
                                          .withSubmitterGroup(admin)
                                          .build();

        Item item = ItemBuilder.createItem(context, col)
                               .withTitle("Public test item")
                               .withIssueDate("2021-03-20")
                               .withAuthor("Doe, John")
                               .withSubject("ExtraEntry")
                               .build();

        Version v2 = VersionBuilder.createVersion(context, item, "test").build();
        Item lastVersionItem = v2.getItem();

        context.restoreAuthSystemState();
        AtomicReference<Integer> idRef = new AtomicReference<Integer>();
        String adminToken = getAuthToken(admin.getEmail(), password);
        Integer versionID = v2.getID();
        Item versionItem = v2.getItem();

        // item that linked last version is not archived
        getClient(adminToken).perform(get("/api/core/items/" + lastVersionItem.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.inArchive", Matchers.is(false)));

        // retrieve the workspace item
        getClient(adminToken).perform(get("/api/submission/workspaceitems/search/item")
                             .param("uuid", String.valueOf(lastVersionItem.getID())))
                             .andExpect(status().isOk())
                             .andDo(result -> idRef.set(read(result.getResponse().getContentAsString(), "$.id")));

        // submit the workspaceitem to complete the deposit
        getClient(adminToken).perform(post(BASE_REST_SERVER_URL + "/api/workflow/workflowitems")
                             .content("/api/submission/workspaceitems/" + idRef.get())
                             .contentType(textUriContentType))
                             .andExpect(status().isCreated());

        // now the item is archived
        getClient(adminToken).perform(get("/api/core/items/" + lastVersionItem.getID()))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$.inArchive", Matchers.is(true)));

        getClient(adminToken).perform(get("/api/versioning/versions/" + versionID))
                             .andExpect(status().isOk())
                             .andExpect(jsonPath("$", Matchers.is(VersionMatcher.matchEntry(v2))))
                             .andExpect(jsonPath("$._links.versionhistory.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + v2.getID() + "/versionhistory"))))
                             .andExpect(jsonPath("$._links.item.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + v2.getID() + "/item"))))
                             .andExpect(jsonPath("$._links.self.href", Matchers.allOf(Matchers.containsString(
                                                 "api/versioning/versions/" + v2.getID()))));

        // To delete a version you need to delete the item linked to it.
        getClient(adminToken).perform(delete("/api/core/items/" + versionItem.getID()))
                             .andExpect(status().is(204));

        getClient(adminToken).perform(get("/api/versioning/versions/" + versionID))
                             .andExpect(status().isNotFound());
    }

}