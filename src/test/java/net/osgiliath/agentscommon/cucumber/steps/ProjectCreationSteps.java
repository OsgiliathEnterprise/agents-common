package net.osgiliath.agentscommon.cucumber.steps;

import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.osgiliath.acplanggraphlangchainbridge.acp.AcpAgentSupportBridge;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.LangGraph4jAdapter;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectCreationSteps {

    private static final Path TEST_WORKSPACES_ROOT = Path.of("build", "test-workspaces");

    private static final List<String> REQUIRED_LAYOUT_FILES = List.of(
            "build.gradle.kts",
            "settings.gradle.kts",
            "jreleaser.yml",
            "gradle/libs.versions.toml",
            ".github/dependabot.yml",
            ".github/workflows/ci.yml",
            "ai/MEMORY.md"
    );
    private static final String DEFERRED_MARKER = "deferred:";

    private final List<String> streamedAnswers = new ArrayList<>();
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private LangGraph4jAdapter langGraph4jAdapter;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private InMemoryChatMemoryStore sessionChatMemoryStore;
    private String workspacePath;
    private String workspaceDataset;
    private Path tempWorkspaceRoot;

    @Given("the project creation state is initialized")
    public void the_project_creation_state_is_initialized() {
        workspacePath = null;
        workspaceDataset = null;
        tempWorkspaceRoot = null;
        streamedAnswers.clear();
    }

    @Given("a workspace {string}")
    public void a_workspace(String workspace) throws IOException {
        // Copy the dataset to a temporary directory to avoid modifying the original
        Path sourceDataset = Path.of("src/test/resources/dataset/projectcreation/" + workspace);
        Files.createDirectories(TEST_WORKSPACES_ROOT);
        Path tempDir = Files.createTempDirectory(TEST_WORKSPACES_ROOT, "test-workspace-");
        Path testWorkspace = tempDir.resolve(workspace);

        // Copy recursively
        copyRecursively(sourceDataset, testWorkspace);

        this.workspacePath = testWorkspace.toAbsolutePath().toString();
        this.workspaceDataset = workspace;
        this.tempWorkspaceRoot = tempDir;
    }

    private void copyRecursively(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(sourcePath -> {
                try {
                    Path destPath = destination.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(destPath);
                    } else {
                        Files.copy(sourcePath, destPath);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy " + sourcePath + " to " + destination, e);
                }
            });
        }
    }

    @Given("the project layout is {int} days old")
    public void the_project_layout_is_days_old(int days) throws IOException {
        Path layoutPath = Path.of(workspacePath).resolve("ai/tasks/001-Project_layout");
        if (!Files.exists(layoutPath)) {
            throw new IllegalStateException("Directory does not exist: " + layoutPath + " — check the workspace dataset");
        }
        Files.setLastModifiedTime(layoutPath, FileTime.from(Instant.now().minus(days, ChronoUnit.DAYS)));
    }

    @Given("the project layout is fresh")
    public void the_project_layout_is_fresh() throws IOException {
        Path layoutPath = Path.of(workspacePath).resolve("ai/tasks/001-Project_layout");
        if (!Files.exists(layoutPath)) {
            throw new IllegalStateException("Directory does not exist: " + layoutPath + " — check the workspace dataset");
        }
        Files.setLastModifiedTime(layoutPath, FileTime.from(Instant.now()));
    }

    @When("the prompt {string} is streamed through the adapter")
    public void the_prompt_is_streamed_through_the_adapter(String prompt) {
        streamPrompt(prompt);
    }

    private String streamPrompt(String prompt) {
        int fromIndex = streamedAnswers.size();
        SessionContext sessionContext = new SessionContext("test-session", workspacePath != null ? workspacePath : ".", Map.of());
        AcpAgentSupportBridge.TokenConsumer consumer = new AcpAgentSupportBridge.TokenConsumer() {
            @Override
            public void onNext(String token) {
                streamedAnswers.add(token);
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable t) {
                throw new RuntimeException(t);
            }
        };
        langGraph4jAdapter.streamPrompt(sessionContext, prompt, List.of(), consumer, new AtomicBoolean(false));
        return String.join("", streamedAnswers.subList(fromIndex, streamedAnswers.size()));
    }

    @Then("the agent should answer with {string}")
    public void the_agent_should_answer_with(String expectedAnswer) {
        String fullAnswer = String.join("", streamedAnswers);
        assertThat(fullAnswer).contains(expectedAnswer);
    }

    @Then("the workspace should contain file {string}")
    public void the_workspace_should_contain_file(String filePath) {
        Path file = Path.of(workspacePath).resolve(filePath);
        assertThat(Files.exists(file))
                .as("File should exist at " + file)
                .isTrue();
    }

    @Then("the project layout should match the expected layout definition")
    public void the_project_layout_should_match_the_expected_layout_definition() {
        Path root = Path.of(workspacePath);
        String streamed = String.join("", streamedAnswers);

        List<String> missingFiles = REQUIRED_LAYOUT_FILES.stream()
                .filter(relativeFile -> !Files.exists(root.resolve(relativeFile)))
                .toList();

        Path taskDir = root.resolve("ai/tasks/001-Project_layout");
        boolean taskDirExists = Files.isDirectory(taskDir);

        assertThat(!streamed.contains(DEFERRED_MARKER) && missingFiles.isEmpty() && taskDirExists)
                .withFailMessage(buildLayoutFailureMessage(root, missingFiles, taskDirExists, streamed))
                .isTrue();

    }

    private String buildLayoutFailureMessage(Path root, List<String> missingFiles, boolean taskDirExists, String streamed) {
        StringBuilder message = new StringBuilder();
        message.append("Project layout was not applied correctly for workspace: ")
                .append(root)
                .append(System.lineSeparator());

        if (!missingFiles.isEmpty()) {
            message.append("Missing required files: ")
                    .append(missingFiles)
                    .append(System.lineSeparator());
        }

        if (!taskDirExists) {
            message.append("Missing required task directory: ")
                    .append(root.resolve("ai/tasks/001-Project_layout"))
                    .append(System.lineSeparator());
        }
        return message.toString();
    }

    @Then("the layout should be effectively in place and active")
    public void the_layout_should_be_effectively_in_place_and_active() {
        assertThat(String.join("", streamedAnswers)).contains("Project layout updated");
    }

    @Given("I reset the project layout to the original state")
    public void i_reset_the_project_layout_to_the_original_state() throws IOException {
        if (workspaceDataset == null || tempWorkspaceRoot == null) {
            return;
        }
        Path sourceDataset = Path.of("src/test/resources/dataset/projectcreation/" + workspaceDataset);
        Path targetWorkspace = tempWorkspaceRoot.resolve(workspaceDataset);
        if (Files.exists(targetWorkspace)) {
            deleteRecursively(targetWorkspace);
        }
        copyRecursively(sourceDataset, targetWorkspace);
        this.workspacePath = targetWorkspace.toAbsolutePath().toString();
        streamedAnswers.clear();
    }

    @After
    public void cleanup_workspace() throws IOException {
        // Clear any pending chat memory left in the store so scenarios don't bleed into each other
        if (sessionChatMemoryStore != null) {
            sessionChatMemoryStore.deleteMessages("test-session");
        }
        workspacePath = null;
        workspaceDataset = null;
        tempWorkspaceRoot = null;
        streamedAnswers.clear();
    }

    private void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete " + path, e);
                        }
                    });
        }
    }

}
