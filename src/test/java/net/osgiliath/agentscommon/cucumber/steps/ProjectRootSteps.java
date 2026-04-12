package net.osgiliath.agentscommon.cucumber.steps;

import dev.langchain4j.data.message.ChatMessage;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.message.ResourceLinkContent;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
import net.osgiliath.agentscommon.langgraph.node.ProjectRootResolverNode;
import net.osgiliath.agentscommon.langgraph.state.WorspaceState;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectRootSteps {

    private static final Path DATASET_WORKSPACE_ROOT =
            Path.of("src/test/resources/dataset/projectroot").toAbsolutePath().normalize();
    private final List<Path> tempDirectories = new ArrayList<>();
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private ProjectRootResolverNode projectRootResolverNode;
    private String cwd;
    private boolean hasSessionContext;
    private Map<String, Object> nodeResult = Map.of();
    private Throwable nodeInvocationError;
    private boolean nodeInvoked;
    private ResourceLinkContent fileInContext;

    @After
    public void cleanupScenarioFiles() {
        for (Path tempDir : tempDirectories) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best-effort cleanup for test directories.
                    }
                });
            } catch (IOException ignored) {
                // Best-effort cleanup for test directories.
            }
        }
        tempDirectories.clear();
    }

    @Given("a cwd is provided as an ACP session attribute")
    public void a_cwd_is_provided_as_an_acp_session_attribute() {
        hasSessionContext = true;
        cwd = DATASET_WORKSPACE_ROOT.toString();
        fileInContext = null;
        nodeResult = Map.of();
        nodeInvocationError = null;
        nodeInvoked = false;
    }

    @Given("a fresh workspace without git is provided as cwd")
    public void a_fresh_workspace_without_git_is_provided_as_cwd() throws IOException {
        hasSessionContext = true;
        cwd = createTempDirectory("project-root-no-git-cwd-").toString();
        fileInContext = null;
        nodeResult = Map.of();
        nodeInvocationError = null;
        nodeInvoked = false;
    }

    @Given("a file under the workspace with a git repository is provided in the context")
    public void a_file_under_the_workspace_with_a_git_repository_is_provided_in_the_context() {
        Path nestedFile = DATASET_WORKSPACE_ROOT.resolve("net/anestedfile");
        fileInContext = new ResourceLinkContent("anestedfile", nestedFile.toUri(), null, null, null, null, null, null);
    }

    @Given("a file under that workspace is provided in the context")
    public void a_file_under_that_workspace_is_provided_in_the_context() throws IOException {
        Path tempCwd = Path.of(cwd);
        Path tempFile = Files.createFile(tempCwd.resolve("testfile.txt"));
        fileInContext = new ResourceLinkContent("testfile.txt", tempFile.toUri(), null, null, null, null, null, null);
    }

    @Given("a file outside the workspace is provided in the context")
    public void a_file_outside_the_workspace_is_provided_in_the_context() throws IOException {
        Path externalDir = createTempDirectory("external-workspace-");
        Path externalFile = Files.createFile(externalDir.resolve("externalfile.txt"));
        fileInContext = new ResourceLinkContent("externalfile.txt", externalFile.toUri(), null, null, null, null, null, null);
    }

    @When("the file is located in the same folder as a git repository")
    public void the_file_is_located_in_the_same_folder_as_a_git_repository() throws IOException {
        cwd = DATASET_WORKSPACE_ROOT.toString();
        Files.createDirectories(DATASET_WORKSPACE_ROOT.resolve(".git"));
        invokeProjectRootAgent();
    }

    @Then("the project root should return that folder")
    public void the_project_root_should_return_that_folder() {
        assertThat(nodeInvoked).isTrue();
        assertThat(nodeInvocationError).isNull();
        assertThat(nodeResult).containsKey(WorspaceState.WORKSPACE_ROOT_CHANNEL);
        assertThat(nodeResult.get(WorspaceState.WORKSPACE_ROOT_CHANNEL))
                .isEqualTo(DATASET_WORKSPACE_ROOT.toUri());
    }

    @When("one of the parent folders contains a git repository")
    public void one_of_the_parent_folders_contains_a_git_repository() throws IOException {
        Files.createDirectories(DATASET_WORKSPACE_ROOT.resolve(".git"));
        cwd = DATASET_WORKSPACE_ROOT.resolve("net").toString();
        invokeProjectRootAgent();
    }

    @Then("the project root should return the folder containing the repository")
    public void the_project_root_should_return_the_folder_containing_the_repository() {
        assertThat(nodeInvoked).isTrue();
        assertThat(nodeInvocationError).isNull();
        assertThat(nodeResult).containsKey(WorspaceState.WORKSPACE_ROOT_CHANNEL);
        assertThat(nodeResult.get(WorspaceState.WORKSPACE_ROOT_CHANNEL))
                .isEqualTo(DATASET_WORKSPACE_ROOT.toUri());
    }

    @When("no parent folder contains a git repository")
    public void no_parent_folder_contains_a_git_repository() throws IOException {
        Path rootWithoutGit = createTempDirectory("project-root-no-git-");
        Path nestedFolder = Files.createDirectories(rootWithoutGit.resolve("a/b/c"));
        cwd = nestedFolder.toString();
        invokeProjectRootAgent();
    }

    @Then("the workspace URI should be Optional.empty")
    public void the_workspace_uri_should_be_optional_empty() {
        assertThat(nodeInvoked).isTrue();
        assertThat(nodeInvocationError).isNull();
        assertThat(nodeResult).doesNotContainKey(WorspaceState.WORKSPACE_ROOT_CHANNEL);
    }

    @Given("no cwd is provided as an ACP session attribute")
    public void no_cwd_is_provided_as_an_acp_session_attribute() {
        hasSessionContext = false;
        cwd = null;
        fileInContext = null;
        nodeResult = Map.of();
        nodeInvocationError = null;
        nodeInvoked = false;
    }

    @When("the project root agent is invoked")
    public void the_project_root_agent_is_invoked() {
        invokeProjectRootAgent();
    }

    @When("the cwd points to a wrong path that does not exist")
    public void the_cwd_points_to_a_wrong_path_that_does_not_exist() {
        hasSessionContext = true;
        cwd = DATASET_WORKSPACE_ROOT.resolve("missing-folder").toString();
        invokeProjectRootAgent();
    }

    @Then("the agent should log an error message")
    public void the_agent_should_log_an_error_message() {
        assertThat(nodeInvoked).isTrue();
        assertThat(nodeInvocationError).isNull();
        assertThat(nodeResult).doesNotContainKey(WorspaceState.WORKSPACE_ROOT_CHANNEL);
    }

    @Then("the project root should return the cwd folder")
    public void the_project_root_should_return_the_cwd_folder() {
        assertThat(nodeInvoked).isTrue();
        assertThat(nodeInvocationError).isNull();
        assertThat(nodeResult).containsKey(WorspaceState.WORKSPACE_ROOT_CHANNEL);
        assertThat(nodeResult.get(WorspaceState.WORKSPACE_ROOT_CHANNEL))
                .isEqualTo(Path.of(cwd).toUri());
    }

    @Then("the agent should return an error message stating the paths are disjoint")
    public void the_agent_should_return_an_error_message_stating_the_paths_are_disjoint() {
        assertThat(nodeInvoked).isTrue();
        assertThat(nodeInvocationError).isNull();
        assertThat(nodeResult).containsKey(MessagesState.MESSAGES_STATE);
    }

    private Path createTempDirectory(String prefix) throws IOException {
        Path tempDir = Files.createTempDirectory(prefix);
        tempDirectories.add(tempDir);
        return tempDir;
    }

    private void invokeProjectRootAgent() {
        Map<String, Object> initData = new HashMap<>();
        if (hasSessionContext) {
            initData.put(AcpState.SESSION_CONTEXT, SessionContext.of("session-under-test", cwd, Map.of()));
        }
        if (fileInContext != null) {
            initData.put(AcpState.ATTACHMENTS_META, List.of(fileInContext));
        }
        WorspaceState<ChatMessage> state = new WorspaceState<>(initData);
        try {
            nodeResult = projectRootResolverNode.apply(state);
            nodeInvocationError = null;
        } catch (Throwable throwable) {
            nodeResult = Map.of();
            nodeInvocationError = throwable;
        }
        nodeInvoked = true;
    }

}
