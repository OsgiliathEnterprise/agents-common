package net.osgiliath.agentscommon.cucumber.e2e.steps;

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

    private final List<String> streamedAnswers = new ArrayList<>();
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private LangGraph4jAdapter langGraph4jAdapter;
    private String workspacePath;

    @Given("the project creation state is initialized")
    public void the_project_creation_state_is_initialized() {
        workspacePath = null;
        streamedAnswers.clear();
    }

    @Given("a workspace {string}")
    public void a_workspace(String workspace) throws IOException {
        // Copy the dataset to a temporary directory to avoid modifying the original
        Path sourceDataset = Path.of("src/test/resources/dataset/projectcreation/" + workspace);
        Path tempDir = Files.createTempDirectory("test-workspace-");
        Path testWorkspace = tempDir.resolve(workspace);
        
        // Copy recursively
        copyRecursively(sourceDataset, testWorkspace);
        
        this.workspacePath = testWorkspace.toAbsolutePath().toString();
    }

    private void copyRecursively(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(sourcePath -> {
                try {
                    Path destPath = destination.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(destPath);
                    } else {
                        Files.createDirectories(destPath.getParent());
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
}
