package net.osgiliath.agentscommon.langgraph.node;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class ProjectStructureAuditorNode implements NodeAction<ProjectCreationState> {

    /**
     * Phrase embedded in the AI message that prompts the user to confirm an update.
     */
    static final String UPDATE_QUESTION_PHRASE = "Would you like to update it to the latest conventions?";
    static final String SETUP_QUESTION_PHRASE = "Would you like to setup the project?";
    private static final Logger log = LoggerFactory.getLogger(ProjectStructureAuditorNode.class);
    private static final int MAX_TOOL_ITERATIONS = 12;
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*})\\s*```", Pattern.CASE_INSENSITIVE);

    private final AgentParser agentParser;
    private final Resource agentFileResource;
    private final ChatMemoryProvider sessionChatMemoryProvider;
    private final ChatModel chatModel;
    private final AgentChatRequestBuilder chatRequestBuilder;
    private final ObjectMapper objectMapper;

    public ProjectStructureAuditorNode(
            AgentParser agentParser,
            CodepromptConfiguration codepromptConfiguration,
            ResourceLocationResolver resourceLocationResolver,
            ChatMemoryProvider sessionChatMemoryProvider,
            @Qualifier("primaryChatModel") ChatModel chatModel,
            AgentChatRequestBuilder chatRequestBuilder,
            ObjectMapper objectMapper) {
        this.agentParser = agentParser;
        this.agentFileResource = resolveAgentResource(
                codepromptConfiguration.getAgent().getAgentFolders(),
                resourceLocationResolver);
        this.sessionChatMemoryProvider = sessionChatMemoryProvider;
        this.chatModel = chatModel;
        this.chatRequestBuilder = chatRequestBuilder;
        this.objectMapper = objectMapper;
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

        Optional<LayoutAuditResult> auditResult = runLayoutAudit(agent, sessionId, state);
        boolean needsUpdate = auditResult.map(LayoutAuditResult::needsUpdate)
                .orElseGet(() -> fallbackNeedsUpdateFromScannerState(state));

        if (needsUpdate) {
            log.debug("Project layout requires refresh for session {}, asking user to update", sessionId);
            String proposal = resolveUpdateProposal(auditResult, state);
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

    private Optional<LayoutAuditResult> runLayoutAudit(Agent agent, String sessionId, ProjectCreationState state) {
        SessionContext sessionContext = state.<SessionContext>value(AcpState.SESSION_CONTEXT)
                .orElse(SessionContext.empty());
        String cwd = sessionContext.cwd();
        if (".".equals(cwd) || cwd.isBlank()) {
            return Optional.empty();
        }

        String command = String.format(
                "mode=validate targetPath=%s dryRun=true%n%n"
                        + "Use tools to inspect the filesystem and return ONLY JSON with fields: "
                        + "bkl001Exists(boolean), projectLayoutTaskExists(boolean), projectLayoutLastUpdatedAt(string|null), projectStructureCompliant(boolean), missingRequiredFiles(string[]), needsUpdate(boolean), reason(string). "
                        + "Set projectStructureCompliant=false if required layout files are missing (build.gradle.kts, settings.gradle.kts, jreleaser.yml, gradle/libs.versions.toml, .github/dependabot.yml, .github/workflows/ci.yml, ai/MEMORY.md). "
                        + "Set needsUpdate=true if BKL-001 fails, if ai/tasks/001-Project_layout is missing, if projectStructureCompliant=false, or if last update is older than 10 days.",
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
            return Optional.empty();
        }

        if (lastAiMessage.text() == null || lastAiMessage.text().isBlank()) {
            return Optional.empty();
        }

        String jsonPayload = extractJsonPayload(lastAiMessage.text());
        if (jsonPayload == null) {
            return Optional.empty();
        }

        try {
            JsonNode json = objectMapper.readTree(jsonPayload);
            boolean hasCustomAuditFields = json.has("bkl001Exists")
                    || json.has("projectLayoutTaskExists")
                    || json.has("projectStructureCompliant")
                    || json.has("projectLayoutLastUpdatedAt")
                    || json.has("needsUpdate");
            boolean bkl001Exists = !json.has("bkl001Exists")
                    || json.path("bkl001Exists").isNull()
                    || json.path("bkl001Exists").asBoolean();
            boolean taskExists = !json.has("projectLayoutTaskExists")
                    || json.path("projectLayoutTaskExists").isNull()
                    || json.path("projectLayoutTaskExists").asBoolean();
            boolean projectStructureCompliant = !json.has("projectStructureCompliant")
                    || json.path("projectStructureCompliant").isNull()
                    || json.path("projectStructureCompliant").asBoolean();
            boolean hasAgentContractResult = hasAgentContractResult(json);
            boolean agentContractNeedsUpdate = hasAgentContractResult && doesAgentContractRequireUpdate(json);

            if (!hasCustomAuditFields && !hasAgentContractResult) {
                return Optional.empty();
            }

            OffsetDateTime lastUpdatedAt = null;
            String lastUpdatedAtValue = json.path("projectLayoutLastUpdatedAt").asText(null);
            if (lastUpdatedAtValue != null && !"null".equalsIgnoreCase(lastUpdatedAtValue)) {
                lastUpdatedAt = OffsetDateTime.parse(lastUpdatedAtValue);
            }

            boolean isStale = lastUpdatedAt != null
                    && ChronoUnit.DAYS.between(lastUpdatedAt, OffsetDateTime.now()) > 10;
            boolean needsUpdate = json.has("needsUpdate")
                    ? json.path("needsUpdate").asBoolean()
                    : !bkl001Exists || !taskExists || !projectStructureCompliant || isStale || agentContractNeedsUpdate;

            UpdateProposalKind kind = classifyUpdateKind(
                    bkl001Exists,
                    taskExists,
                    projectStructureCompliant,
                    agentContractNeedsUpdate,
                    isStale,
                    json.path("reason").asText(""));
            return Optional.of(new LayoutAuditResult(needsUpdate, kind));
        } catch (Exception e) {
            log.debug("Unable to parse layout-audit JSON from model output", e);
            return Optional.empty();
        }
    }

    private String resolveUpdateProposal(Optional<LayoutAuditResult> auditResult, ProjectCreationState state) {
        if (auditResult.isPresent() && auditResult.get().needsUpdate()) {
            UpdateProposalKind kind = auditResult.get().proposalKind();
            if (kind == UpdateProposalKind.MISSING) {
                return "The project structure task is not done. Would you like to setup the project?";
            }
            if (kind == UpdateProposalKind.STALE) {
                return "The project structure was updated more than 10 days ago. " + UPDATE_QUESTION_PHRASE;
            }
        }

        boolean layoutMissing = state.projectLayoutDone().isEmpty() || !state.projectLayoutDone().orElse(false);
        return layoutMissing
                ? "The project structure task is not done. Would you like to setup the project?"
                : "The project structure was updated more than 10 days ago. " + UPDATE_QUESTION_PHRASE;
    }

    private UpdateProposalKind classifyUpdateKind(boolean bkl001Exists,
                                                  boolean taskExists,
                                                  boolean projectStructureCompliant,
                                                  boolean agentContractNeedsUpdate,
                                                  boolean isStale,
                                                  String reasonText) {
        if (!bkl001Exists || !taskExists || !projectStructureCompliant || agentContractNeedsUpdate) {
            return UpdateProposalKind.MISSING;
        }
        if (isStale) {
            return UpdateProposalKind.STALE;
        }

        String reason = reasonText == null ? "" : reasonText.toLowerCase();
        if (reason.contains("missing")
                || reason.contains("not done")
                || reason.contains("bkl-001")
                || reason.contains("projectstructurecompliant")
                || reason.contains("build.gradle.kts")
                || reason.contains("settings.gradle.kts")
                || reason.contains("jreleaser.yml")
                || reason.contains("libs.versions.toml")
                || reason.contains("dependabot.yml")
                || reason.contains("ci.yml")
                || reason.contains("memory.md")) {
            return UpdateProposalKind.MISSING;
        }
        if (reason.contains("stale") || reason.contains("older") || reason.contains("10 day")) {
            return UpdateProposalKind.STALE;
        }
        return UpdateProposalKind.UNKNOWN;
    }

    private boolean hasAgentContractResult(JsonNode json) {
        return json.has("overall") || (json.has("checks") && json.path("checks").isArray());
    }

    private boolean doesAgentContractRequireUpdate(JsonNode json) {
        String overall = json.path("overall").asText("");
        if ("FAIL".equalsIgnoreCase(overall)) {
            return true;
        }

        JsonNode checks = json.path("checks");
        if (!checks.isArray()) {
            return false;
        }

        for (JsonNode check : checks) {
            String status = check.path("status").asText("");
            if ("FAIL".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
                return true;
            }
        }
        return false;
    }

    private boolean fallbackNeedsUpdateFromScannerState(ProjectCreationState state) {
        Optional<Boolean> isDone = state.projectLayoutDone();
        if (isDone.isEmpty() || !isDone.get()) {
            return true;
        }

        return state.projectLayoutUpdateDate()
                .map(updateDate -> ChronoUnit.DAYS.between(updateDate, OffsetDateTime.now()) > 10)
                .orElse(false);
    }

    private String extractJsonPayload(String text) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private enum UpdateProposalKind {
        MISSING,
        STALE,
        UNKNOWN
    }

    private record LayoutAuditResult(boolean needsUpdate, UpdateProposalKind proposalKind) {
    }
}
