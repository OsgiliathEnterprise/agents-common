package net.osgiliath.agentscommon.langgraph.node.auditor;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
import net.osgiliath.agentscommon.langgraph.node.model.UpdateProposalKind;
import net.osgiliath.agentscommon.langgraph.state.ProjectCreationState;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import net.osgiliath.agentsdk.agent.parser.AgentParser;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Component
public class ProjectStructureAuditorNode implements NodeAction<ProjectCreationState> {

    /**
     * Phrase embedded in the AI message that prompts the user to confirm an update.
     */
    static final String UPDATE_QUESTION_PHRASE = "Would you like to update it to the latest conventions?";
    static final String SETUP_QUESTION_PHRASE = "Would you like to setup the project?";
    private static final Logger log = LoggerFactory.getLogger(ProjectStructureAuditorNode.class);
    private static final int MAX_TOOL_ITERATIONS = 12;

    private final AgentParser agentParser;
    private final ProposalKindToUserAnswerResolver proposalKindToUserAnswerResolver;
    private final Resource agentFileResource;
    private final ChatMemoryProvider sessionChatMemoryProvider;
    private final ChatModel chatModel;
    private final AgentChatRequestBuilder chatRequestBuilder;
    private final ProjectStructureAuditorResultInterpreter resultInterpreter;

    public ProjectStructureAuditorNode(
            AgentParser agentParser,
            CodepromptConfiguration codepromptConfiguration,
            ResourceLocationResolver resourceLocationResolver,
            ChatMemoryProvider sessionChatMemoryProvider,
            @Qualifier("primaryChatModel") ChatModel chatModel,
            AgentChatRequestBuilder chatRequestBuilder,
            ProjectStructureAuditorResultInterpreter resultInterpreter,
            ProposalKindToUserAnswerResolver proposalKindToUserAnswerResolver) {
        this.agentParser = agentParser;
        this.proposalKindToUserAnswerResolver = proposalKindToUserAnswerResolver;
        this.agentFileResource = resolveAgentResource(
                codepromptConfiguration.getAgent().getAgentFolders(),
                resourceLocationResolver);
        this.sessionChatMemoryProvider = sessionChatMemoryProvider;
        this.chatModel = chatModel;
        this.chatRequestBuilder = chatRequestBuilder;
        this.resultInterpreter = resultInterpreter;
    }

    private static Resource resolveAgentResource(List<String> agentFolders, ResourceLocationResolver resourceLocationResolver) {
        return resourceLocationResolver
                .resolveFirstExisting(agentFolders, "foundational/templates/project-template-scaffolder.md")
                .orElseThrow(() -> new IllegalStateException(
                        "Agent file 'foundational/templates/project-template-scaffolder.md' not found in any configured agent folder: "
                                + agentFolders));
    }

    @Override
    public Map<String, Object> apply(ProjectCreationState state) {
        Agent agent;
        try {
            agent = agentParser.getAgent(agentFileResource);
        } catch (Exception e) {
            log.error("Unable to load agent file", e);
            throw new RuntimeException(e);
        }

        String lastUserMessage = state.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .reduce((first, second) -> second)
                .map(UserMessage::singleText)
                .orElse("");

        // If user explicitly invoked agent validation mode
        if (lastUserMessage.contains(agent.getName() + "/validate")) {
            return Map.of();
        }

        String sessionId = state.<SessionContext>value(AcpState.SESSION_CONTEXT)
                .map(SessionContext::sessionId)
                .orElse("default");
        // Check LangChain4j session memory: is there a prior AI message asking about an update?
        ChatMemory sessionMemory = sessionChatMemoryProvider.get(sessionId);
        boolean hasPendingUpdateQuestion = sessionMemory.messages().stream()
                .filter(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .map(AiMessage::text)
                .anyMatch(text -> text.contains(UPDATE_QUESTION_PHRASE) || text.contains(SETUP_QUESTION_PHRASE));
        boolean hasPendingProposalFlag = state.layoutUpdateProposal().orElse(false);
        boolean hasPendingConfirmation = hasPendingUpdateQuestion || hasPendingProposalFlag;

        String lastUserMessageLower = lastUserMessage.toLowerCase();
        boolean isAffirmative = lastUserMessageLower.contains("yes") || lastUserMessageLower.contains("update");
        boolean isNegative = lastUserMessageLower.contains("no") || lastUserMessageLower.contains("not now");

        if (isAffirmative && hasPendingConfirmation) {
            log.debug("User confirmed layout update for session {}, clearing session memory", sessionId);
            sessionMemory.clear();
            return Map.of(
                    ProjectCreationState.PENDING_LAYOUT_UPDATE_CHANNEL, true,
                    ProjectCreationState.LAYOUT_UPDATE_PROPOSAL_CHANNEL, false);
        }

        if (isNegative && hasPendingConfirmation) {
            sessionMemory.clear();
            return Map.of(
                    ProjectCreationState.PENDING_LAYOUT_UPDATE_CHANNEL, false,
                    ProjectCreationState.LAYOUT_UPDATE_PROPOSAL_CHANNEL, false);
        }

        if (isAffirmative) {
            sessionMemory.clear();
            return Map.of(
                    ProjectCreationState.PENDING_LAYOUT_UPDATE_CHANNEL, false,
                    ProjectCreationState.LAYOUT_UPDATE_PROPOSAL_CHANNEL, false);
        }

        UpdateProposalKind proposalKind = runLayoutAudit(agent, sessionId, state);
        boolean needsUpdate = proposalKind != UpdateProposalKind.NO_UPDATE_NEEDED;

        if (needsUpdate) {
            log.debug("Project layout requires refresh for session {}, asking user to update", sessionId);
            String proposal = proposalKindToUserAnswerResolver.resolveUpdateProposal(proposalKind);
            AiMessage question = AiMessage.from(proposal);
            // Persist question so a subsequent "yes" can be routed to the applier.
            sessionMemory.add(question);
            return Map.of(
                    "messages", question,
                    ProjectCreationState.PENDING_LAYOUT_UPDATE_CHANNEL, false,
                    ProjectCreationState.LAYOUT_UPDATE_PROPOSAL_CHANNEL, true);
        }

        return Map.of(
                ProjectCreationState.PENDING_LAYOUT_UPDATE_CHANNEL, false,
                ProjectCreationState.LAYOUT_UPDATE_PROPOSAL_CHANNEL, false);
    }

    private UpdateProposalKind runLayoutAudit(Agent agent, String sessionId, ProjectCreationState state) {
        SessionContext sessionContext = state.<SessionContext>value(AcpState.SESSION_CONTEXT)
                .orElse(SessionContext.empty());
        String cwd = sessionContext.cwd();
        if (".".equals(cwd) || cwd.isBlank()) {
            return resultInterpreter.extractAuditResult(null, state);
        }

        String command = String.format(
                "mode=validate targetPath=%s dryRun=true%n%n"
                        + "Use tools to inspect the filesystem and return ONLY JSON with fields: "
                        + "bkl001Exists(boolean), mtb001Exists(boolean), mem001Exists(boolean), projectLayoutTaskExists(boolean), projectLayoutLastUpdatedAt(string|null), projectStructureCompliant(boolean), missingRequiredFiles(string[]), needsUpdate(boolean), reason(string). "
                        + "Set projectStructureCompliant=false if required layout files are missing. "
                        + "Set needsUpdate=true if BKL-001 fails, if MTB-001 fails, if MEM-001 fails, if projectStructureCompliant=false, or if last update is older than 10 days.",
                cwd);

        UserMessage userMessage = UserMessage.from(command);
        String chatMemoryId = sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
        InvocationParameters invocationParameters = InvocationParameters.from("cwd", cwd);

        ChatRequest baseRequest = chatRequestBuilder.buildChatRequest(agent, userMessage, chatMemoryId, invocationParameters);
        ToolProviderResult toolProviderResult = chatRequestBuilder.buildToolProviderResult(agent, userMessage, chatMemoryId, invocationParameters);

        List<ChatMessage> messages = new ArrayList<>(baseRequest.messages());
        AiMessage lastAiMessage = null;

        try {
            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                ChatResponse response = chatModel.chat(ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(baseRequest.toolSpecifications())
                        .build());

                AiMessage aiMessage = response.aiMessage();
                lastAiMessage = aiMessage;
                messages.add(aiMessage);

                if (!aiMessage.hasToolExecutionRequests()) {
                    break;
                }

                aiMessage.toolExecutionRequests().forEach(request -> {
                    ToolExecutor executor = toolProviderResult.toolExecutorByName(request.name());
                    String result = executor != null
                            ? executor.execute(request, chatMemoryId)
                            : "Tool not found: " + request.name();
                    messages.add(dev.langchain4j.data.message.ToolExecutionResultMessage.from(request, result));
                });
            }
        } catch (Exception e) {
            log.warn("Layout audit via LLM tools failed for {}: {}", cwd, e.getMessage());
            return resultInterpreter.extractAuditResult(null, state);
        }

        return resultInterpreter.extractAuditResult(lastAiMessage, state);
    }

}
