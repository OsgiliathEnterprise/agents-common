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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectLayoutCommonSteps {

    private static final Path TEST_WORKSPACES_ROOT = Path.of("build", "test-workspaces");

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private LangGraph4jAdapter langGraph4jAdapter;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private InMemoryChatMemoryStore sessionChatMemoryStore;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private ProjectLayoutScenarioContext context;

    @Given("the project creation state is initialized")
    public void the_project_creation_state_is_initialized() {
        context.reset();
    }

    @Given("a workspace {string}")
    public void a_workspace(String workspace) throws IOException {
        // Copy the dataset to a temporary directory to avoid modifying the original
        Path sourceDataset = Path.of("src/test/resources/dataset/projectcreation/" + workspace);
        Files.createDirectories(TEST_WORKSPACES_ROOT);
        Path tempDir = Files.createTempDirectory(TEST_WORKSPACES_ROOT, "test-workspace-");
        Path testWorkspace = tempDir.resolve(workspace);

        copyRecursively(sourceDataset, testWorkspace);

        context.setWorkspacePath(testWorkspace.toAbsolutePath().toString());
        context.setWorkspaceDataset(workspace);
        context.setTempWorkspaceRoot(tempDir);
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
        Path layoutPath = Path.of(context.getWorkspacePath()).resolve("ai/tasks/001-Project_layout");
        if (!Files.exists(layoutPath)) {
            throw new IllegalStateException("Directory does not exist: " + layoutPath + " - check the workspace dataset");
        }
        Files.setLastModifiedTime(layoutPath, FileTime.from(Instant.now().minus(days, ChronoUnit.DAYS)));
    }

    @Given("the project layout is fresh")
    public void the_project_layout_is_fresh() throws IOException {
        Path layoutPath = Path.of(context.getWorkspacePath()).resolve("ai/tasks/001-Project_layout");
        if (!Files.exists(layoutPath)) {
            throw new IllegalStateException("Directory does not exist: " + layoutPath + " - check the workspace dataset");
        }
        Files.setLastModifiedTime(layoutPath, FileTime.from(Instant.now()));
    }

    @When("the prompt {string} is streamed through the adapter")
    public void the_prompt_is_streamed_through_the_adapter(String prompt) {
        streamPrompt(prompt);
    }

    private void streamPrompt(String prompt) {
        List<String> streamedAnswers = context.getStreamedAnswers();
        SessionContext sessionContext = new SessionContext(
                "test-session",
                context.getWorkspacePath() != null ? context.getWorkspacePath() : ".",
                Map.of()
        );
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
    }

    @Then("the agent should answer with {string}")
    public void the_agent_should_answer_with(String expectedAnswer) {
        assertThat(context.fullAnswer()).contains(expectedAnswer);
    }

    @Then("the workspace should contain file {string}")
    public void the_workspace_should_contain_file(String filePath) {
        Path file = Path.of(context.getWorkspacePath()).resolve(filePath);
        assertThat(Files.exists(file))
                .as("File should exist at " + file)
                .isTrue();
    }

    @Given("I reset the project layout to the original state")
    public void i_reset_the_project_layout_to_the_original_state() throws IOException {
        if (context.getWorkspaceDataset() == null || context.getTempWorkspaceRoot() == null) {
            return;
        }
        Path sourceDataset = Path.of("src/test/resources/dataset/projectcreation/" + context.getWorkspaceDataset());
        Path targetWorkspace = context.getTempWorkspaceRoot().resolve(context.getWorkspaceDataset());
        if (Files.exists(targetWorkspace)) {
            deleteRecursively(targetWorkspace);
        }
        copyRecursively(sourceDataset, targetWorkspace);
        context.setWorkspacePath(targetWorkspace.toAbsolutePath().toString());
        context.getStreamedAnswers().clear();
    }

    @After
    public void cleanup_workspace() {
        // Clear pending chat memory so scenarios do not bleed into each other.
        if (sessionChatMemoryStore != null) {
            sessionChatMemoryStore.deleteMessages("test-session");
        }
        context.reset();
    }

    private void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
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

