package net.osgiliath.agentscommon.cucumber.e2e.steps;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import net.osgiliath.acplanggraphlangchainbridge.acp.AcpAgentSupportBridge;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.LangGraph4jAdapter;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
import net.osgiliath.agentscommon.cucumber.e2e.model.graph.ProjectCreationGraph;
import net.osgiliath.agentscommon.langgraph.node.ProjectStructureCheckerNode;
import net.osgiliath.agentscommon.langgraph.state.ProjectCreationState;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectCreationSteps {

    private final List<String> streamedAnswers = new ArrayList<>();
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private ProjectStructureCheckerNode projectStructureCheckerNode;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private ProjectCreationGraph projectCreationGraph;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private LangGraph4jAdapter langGraph4jAdapter;
    private Map<String, Object> stateData;
    private List<ChatMessage> messages;
    private Map<String, Object> nodeResult;
    private ProjectCreationState graphResult;
    private String workspacePath;

    @Given("the project creation state is initialized")
    public void the_project_creation_state_is_initialized() {
        stateData = new HashMap<>();
        messages = new ArrayList<>();
        nodeResult = null;
        graphResult = null;
        workspacePath = null;
        streamedAnswers.clear();
    }

    @Given("the project structure task is not done")
    public void the_project_structure_task_is_not_done() {
        stateData.put(ProjectCreationState.PROJECT_LAYOUT_DONE_CHANNEL, false);
    }

    @Given("the project structure task is done")
    public void the_project_structure_task_is_done() {
        stateData.put(ProjectCreationState.PROJECT_LAYOUT_DONE_CHANNEL, true);
    }

    @Given("the project structure update date is {int} days ago")
    public void the_project_structure_update_date_is_days_ago(int days) {
        stateData.put(ProjectCreationState.PROJECT_LAYOUT_UPDATE_DATE_CHANNEL, OffsetDateTime.now().minusDays(days));
    }

    @Given("the project structure update date is fresh")
    public void the_project_structure_update_date_is_fresh() {
        stateData.put(ProjectCreationState.PROJECT_LAYOUT_UPDATE_DATE_CHANNEL, OffsetDateTime.now());
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

    @When("a prompt {string} is called")
    public void a_prompt_is_called(String prompt) {
        messages.add(UserMessage.from(prompt));
        invokeNode();
    }

    @When("the prompt is called with {string} command")
    public void the_prompt_is_called_with_command(String command) {
        messages.add(UserMessage.from(command));
        invokeNode();
    }

    @When("the full project creation graph is called with prompt {string}")
    public void the_full_project_creation_graph_is_called_with_prompt(String prompt) throws GraphStateException {
        messages.add(UserMessage.from(prompt));
        stateData.put("messages", messages);
        if (workspacePath != null) {
            SessionContext sessionContext = new SessionContext("test-session", workspacePath, Map.of());
            stateData.put(AcpState.SESSION_CONTEXT, sessionContext);
        }
        graphResult = new ProjectCreationState(projectCreationGraph.buildGraph().compile().stream(stateData).stream().reduce((a, b) -> b).map(NodeOutput::state).orElseThrow().data());
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

    private void invokeNode() {
        stateData.put("messages", messages);
        ProjectCreationState state = new ProjectCreationState(stateData);
        nodeResult = projectStructureCheckerNode.apply(state);
    }

    @Then("the agent should answer with {string}")
    public void the_agent_should_answer_with(String expectedAnswer) {
        if (!streamedAnswers.isEmpty()) {
            String fullAnswer = String.join("", streamedAnswers);
            assertThat(fullAnswer).contains(expectedAnswer);
        } else {
            assertThat(graphResult.lastMessage()).isPresent();
            AiMessage aiMessage = (AiMessage) graphResult.lastMessage().get();
            assertThat(aiMessage.text()).contains(expectedAnswer);
        }
    }

    @Then("the agent should ask to setup the project")
    public void the_agent_should_ask_to_setup_the_project() {
        assertThat(nodeResult).containsKey("messages");
        AiMessage aiMessage = (AiMessage) nodeResult.get("messages");
        assertThat(aiMessage.text()).contains("The project structure task is not done. Would you like to setup the project?");
    }

    @Then("the agent should propose to update the project structure to latest conventions")
    public void the_agent_should_propose_to_update_the_project_structure_to_latest_conventions() {
        assertThat(nodeResult).containsKey("messages");
        AiMessage aiMessage = (AiMessage) nodeResult.get("messages");
        assertThat(aiMessage.text()).contains("The project structure was updated more than 10 days ago. Would you like to update it to the latest conventions?");
    }

    @Then("the project audit should pass")
    public void the_project_audit_should_pass() {
        assertThat(nodeResult).containsKey("messages");
        AiMessage aiMessage = (AiMessage) nodeResult.get("messages");
        assertThat(aiMessage.text()).isEqualTo("Project audit passed");
    }

    @Then("the project audit should fail")
    public void the_project_audit_should_fail() {
        assertThat(nodeResult).containsKey("messages");
        AiMessage aiMessage = (AiMessage) nodeResult.get("messages");
        assertThat(aiMessage.text()).isEqualTo("Project audit failed");
    }

    @Then("the next node in the graph should be called")
    public void the_next_node_in_the_graph_should_be_called() {
        // In this implementation, "next node" means the checker returns an empty map,
        // allowing the graph to proceed to the next node (which would be wired after it).
        assertThat(nodeResult).isEmpty();
    }
}
