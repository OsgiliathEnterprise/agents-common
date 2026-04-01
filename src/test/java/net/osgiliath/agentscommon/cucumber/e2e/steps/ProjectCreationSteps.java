package net.osgiliath.agentscommon.cucumber.e2e.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.osgiliath.acplanggraphlangchainbridge.acp.AcpAgentSupportBridge;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.LangGraph4jAdapter;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
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
    public void a_workspace(String workspace) {
        File file = new File("src/test/resources/dataset/projectcreation/" + workspace);
        this.workspacePath = file.getAbsolutePath();
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
}
