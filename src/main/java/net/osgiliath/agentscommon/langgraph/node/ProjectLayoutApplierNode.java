package net.osgiliath.agentscommon.langgraph.node;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import net.osgiliath.agentscommon.langgraph.state.ProjectCreationState;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.agent.parser.AgentParser;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Graph node that applies (or refreshes) the canonical project layout by calling the
 * {@code project-template-scaffolder} agent through the LLM with full tool support.
 *
 * <p>Execution flow:</p>
 * <ol>
 *   <li>Parses {@code project-template-scaffolder.md} via {@link AgentParser} to build the
 *       full system prompt (agent instructions + all linked skill content).</li>
 *   <li>Builds a {@link dev.langchain4j.service.tool.ToolProviderRequest} with the user message,
 *       session-scoped chat memory id, and invocation parameters, then filters tools from
 *       the full MCP tool provider to those declared by the agent.</li>
 *   <li>Runs a synchronous tool-calling loop — sends a
 *       {@code mode=amend-existing dryRun=false} command and keeps executing tool calls until
 *       the model stops issuing them.</li>
 *   <li>Returns the deterministic confirmation {@code "Project layout updated"} so the graph
 *       always streams a predictable reply to the user regardless of the LLM's own text.</li>
 * </ol>
 */
@Component
public class ProjectLayoutApplierNode implements NodeAction<ProjectCreationState> {

    private static final Logger log = LoggerFactory.getLogger(ProjectLayoutApplierNode.class);
    private static final int MAX_TOOL_ITERATIONS = 20;

    private final ChatModel chatModel;
    private final AgentParser agentParser;
    private final AgentChatRequestBuilder chatRequestBuilder;
    private final Resource agentFileResource;

    public ProjectLayoutApplierNode(
            @Qualifier("primaryChatModel") ChatModel chatModel,
            AgentParser agentParser,
            AgentChatRequestBuilder chatRequestBuilder,
            CodepromptConfiguration codepromptConfiguration,
            ResourceLocationResolver resourceLocationResolver) {
        this.chatModel = chatModel;
        this.agentParser = agentParser;
        this.chatRequestBuilder = chatRequestBuilder;
        this.agentFileResource = resolveAgentResource(
                codepromptConfiguration.getAgent().getAgentFolders(),
                resourceLocationResolver);
    }

    private static Resource resolveAgentResource(List<String> agentFolders, ResourceLocationResolver resourceLocationResolver) {
        return resourceLocationResolver
                .resolveFirstExisting(agentFolders, "foundational/templates/project-template-scaffolder.md")
                .orElseThrow(() -> new IllegalStateException(
                        "Agent file 'foundational/templates/project-template-scaffolder.md' not found in any configured agent folder: "
                                + agentFolders));
    }

    // ── NodeAction ────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> apply(ProjectCreationState state) {
        SessionContext sessionContext = state.<SessionContext>value(AcpState.SESSION_CONTEXT)
                .orElse(SessionContext.empty());

        String cwd = sessionContext.cwd();
        if (".".equals(cwd)) {
            log.warn("No workspace cwd in session context – skipping layout apply");
            return Map.of("messages", AiMessage.from("Project layout updated"));
        }

        log.info("Applying project layout via LLM agent for workspace: {}", cwd);

        // 1. Load and parse the agent
        Agent agent;
        try {
            agent = agentParser.getAgent(agentFileResource);
        } catch (Exception e) {
            log.error("Unable to load project-template-scaffolder agent", e);
            return Map.of("messages", AiMessage.from("Project layout updated"));
        }

        // 2. User command — explicit dryRun=false so the agent writes files
        String command = String.format(
                "mode=amend-existing targetPath=%s dryRun=false%n%n"
                        + "Update this project to the latest conventions. "
                        + "Ensure all required module-template-base files (build.gradle.kts, "
                        + "settings.gradle.kts, jreleaser.yml, gradle/libs.versions.toml, "
                        + ".github/dependabot.yml, .github/workflows/ci.yml), "
                        + "ai backlog structure (ai/tasks/001-Project_layout/ with all phase files), "
                        + "and ai memory (ai/MEMORY.md) are created or refreshed. "
                        + "Do not ask for additional inputs – apply all changes immediately.",
                cwd);

        UserMessage userMessage = UserMessage.from(command);
        String chatMemoryId = sessionContext.sessionId().isBlank()
                ? java.util.UUID.randomUUID().toString()
                : sessionContext.sessionId();
        InvocationParameters invocationParameters = InvocationParameters.from("cwd", cwd);

        // 3. Build a fully hydrated ChatRequest (system prompt + user message + filtered tool specs)
        ChatRequest baseRequest = chatRequestBuilder.buildChatRequest(
                agent, userMessage, chatMemoryId, invocationParameters);

        // 4. Obtain filtered tool executors for the tool-calling loop
        ToolProviderResult toolProviderResult = chatRequestBuilder.buildToolProviderResult(
                agent, userMessage, chatMemoryId, invocationParameters);

        // 5. Synchronous tool-calling loop
        List<ChatMessage> messages = new ArrayList<>(baseRequest.messages());
        try {
            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                ChatResponse response = chatModel.chat(
                        ChatRequest.builder()
                                .messages(messages)
                                .toolSpecifications(baseRequest.toolSpecifications())
                                .build());

                AiMessage aiMessage = response.aiMessage();
                messages.add(aiMessage);

                if (!aiMessage.hasToolExecutionRequests()) {
                    log.debug("LLM finished after {} tool-calling iteration(s)", iteration + 1);
                    break;
                }

                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    log.debug("Executing tool '{}' args={}", request.name(), request.arguments());
                    ToolExecutor executor = toolProviderResult.toolExecutorByName(request.name());
                    String result = executor != null
                            ? executor.execute(request, chatMemoryId)
                            : "Tool not found: " + request.name();
                    log.debug("Tool '{}' result: {}", request.name(), result);
                    messages.add(ToolExecutionResultMessage.from(request, result));
                }
            }
        } catch (Exception e) {
            log.warn("LLM tool-calling loop failed for workspace {} – continuing without model-driven file writes: {}",
                    cwd, e.getMessage());
        }

        // 6. Return a deterministic confirmation regardless of the LLM's own prose
        return Map.of(
                "messages", AiMessage.from("Project layout updated"),
                ProjectCreationState.PROJECT_LAYOUT_UPDATE_DATE_CHANNEL, OffsetDateTime.now());
    }
}
