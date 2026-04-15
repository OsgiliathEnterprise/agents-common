package net.osgiliath.agentscommon.langgraph.node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
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

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Graph node that applies (or refreshes) the canonical project layout by calling the
 * {@code project-template-scaffolder} agent through the LLM with full tool support.
 *
 * <p>Execution flow:</p>
 * <ol>
 *   <li>Parses {@code project-template-scaffolder.md} via {@link AgentParser} to build the
 *       full system prompt (agent instructions + all linked skill content).</li>
 *   <li>Builds a {@link ToolProviderRequest} with the user message,
 *       pass-scoped UUID chat memory id, and invocation parameters, then filters tools from
 *       the full MCP tool provider to those declared by the agent.</li>
 *   <li>Runs a synchronous tool-calling loop — sends a
 *       {@code mode=resync dryRun=false} command and keeps executing tool calls until
 *       the model stops issuing them.</li>
 *   <li>Returns the deterministic confirmation {@code "Project layout updated"} so the graph
 *       always streams a predictable reply to the user regardless of the LLM's own text.</li>
 * </ol>
 */
@Component
public class ProjectLayoutApplierNode implements NodeAction<ProjectCreationState> {

    private static final Logger log = LoggerFactory.getLogger(ProjectLayoutApplierNode.class);
    private static final ObjectMapper TOOL_SIGNATURE_MAPPER = new ObjectMapper();
    private static final int MAX_TOOL_ITERATIONS = 200;
    private static final int MAX_REPEAT_PER_TOOL_CALL = 2;
    private static final int TOOL_CALL_HISTORY_LIMIT = 200;
    // Retry cap per command execution loop.
    private static final int MAX_APPLY_PASSES = 50;
    private static final String DEFERRED_MARKER = "deferred:";
    private static final String DEFERRED_PREFIX = "project layout deferred:";
    private static final String NEED_MORE_ITERATION_MARKER = "need-more-iteration:";
    private static final String SUCCESS_TOKEN = "project layout updated";
    private static final String ASSERTS_RETRY_REASON = "skill asserts verification still requires more passes";
    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "(?i)(?:project-template|project_template_scaffolder)/(apply(?:-[a-z-]+)?|resync(?:-[a-z-]+)?|verify|schedule)\\b");

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

    private static String deferredReply(String reason) {
        return "Project layout deferred: " + reason;
    }

    private static String buildApplyCommand(String cwd, int pass) {
        String passHint = pass > 1
                ? "Retry pass " + pass + ": complete only remaining project layout updates immediately. "
                : "";
        return String.format(
                "mode=apply targetPath=%s dryRun=false reporter=project-template-scaffolder assignees=[HumanCaller] businessValue=8 requirementClarity=8 severity=major effort=5%n%n"
                        + "Apply all project layout skills now (module template, AI backlog, and AI memory), and Ensure skills agent assertions are passing.. "
                        + "%s"
                        + "If work remains after this pass, respond with '" + NEED_MORE_ITERATION_MARKER + " <reason>'. "
                        + "Do not ask for additional inputs - apply all changes immediately.",
                cwd,
                passHint);
    }

    private static String buildResyncCommand(String cwd, int pass) {
        String passHint = pass > 1
                ? "Retry pass " + pass + ": complete only remaining project layout updates immediately. "
                : "";
        return String.format(
                "mode=resync targetPath=%s dryRun=false%n%n"
                        + "Update this project to the latest conventions. "
                        + "Ensure all required module-template-base files,"
                        + "ai backlog structure, "
                        + "and ai memory are created or refreshed, and Ensure skills agent assertions are passing."
                        + "%s"
                        + "If work remains after this pass, respond with '" + NEED_MORE_ITERATION_MARKER + " <reason>'. "
                        + "Do not ask for additional inputs – apply all changes immediately.",
                cwd,
                passHint);
    }

    private static String buildVerifySkillAssertsCommand(String cwd, int pass) {
        String passHint = pass > 1
                ? "Retry pass " + pass + ": complete only remaining missing skill assert requirements immediately. "
                : "";
        return String.format(
                "mode=verify targetPath=%s dryRun=true writeMissingAsserts=false%n%n"
                        + "Verify every referenced skill contains valid asserts/*.json files with direct evidence. "
                        + "Do not write files in verify mode; report missing artifacts with a patch plan only. "
                        + "%s"
                        + "If verification still needs another pass, respond with '" + NEED_MORE_ITERATION_MARKER + " <reason>'. "
                        + "Do not ask for additional inputs - run all checks immediately.",
                cwd,
                passHint);
    }

    @Override
    public Map<String, Object> apply(ProjectCreationState state) {
        SessionContext sessionContext = state.<SessionContext>value(AcpState.SESSION_CONTEXT)
                .orElse(SessionContext.empty());

        String cwd = sessionContext.cwd();
        if (".".equals(cwd)) {
            log.warn("No workspace cwd in session context – skipping layout apply");
            return Map.of("messages", AiMessage.from(deferredReply("no workspace cwd in session context")));
        }

        log.info("Applying project layout via LLM agent for workspace: {}", cwd);

        Agent agent;
        try {
            agent = agentParser.getAgent(agentFileResource);
        } catch (Exception e) {
            log.error("Unable to load project-template-scaffolder agent", e);
            return Map.of("messages", AiMessage.from(deferredReply("unable to load project-template-scaffolder agent")));
        }

        InvocationParameters invocationParameters = InvocationParameters.from("cwd", cwd);
        ProjectLayoutCommand command = resolveCommand(state);
        log.info("Project layout command '{}' selected for workspace {}", command.externalName(), cwd);

        String chatMemoryId = sessionContext.sessionId() + "-" + UUID.randomUUID();
        log.debug("Starting {} with fresh chat memory id {}", command.externalName(), chatMemoryId);

        String pendingReason = "layout update still pending";
        for (int applyPass = 1; applyPass <= MAX_APPLY_PASSES; applyPass++) {
            log.debug("Starting {} loop {}", command.externalName(), applyPass);
            IterationResult result = runIteration(agent, chatMemoryId, invocationParameters, cwd, applyPass, command);
            if (result instanceof IterationResult.Done(var stateUpdate)) {
                return stateUpdate;
            }
            pendingReason = ((IterationResult.Continue) result).pendingReason();
        }

        return Map.of("messages", AiMessage.from(deferredReply(
                "project layout apply did not converge after " + MAX_APPLY_PASSES + " pass(es): " + pendingReason)));
    }

    private IterationResult runIteration(Agent agent,
                                         String chatMemoryId,
                                         InvocationParameters invocationParameters,
                                         String cwd,
                                         int applyPass,
                                         ProjectLayoutCommand command) {
        boolean needsMoreIteration = false;
        String pendingReason = "layout update still pending";

        if (command.requiresApplyPhase()) {
            PassOutcome applyOutcome = runCommandPass(agent, chatMemoryId, invocationParameters, cwd, applyPass, command);
            log.info("Apply loop pass {} command '{}' outcome: [status={}, reason={}]",
                    applyPass, command.externalName(), applyOutcome.status(), applyOutcome.reason());
            if (applyOutcome.isDeferred()) {
                pendingReason = applyOutcome.reason().isBlank()
                        ? "layout apply pass deferred without reason"
                        : applyOutcome.reason();
                needsMoreIteration = true;
                log.info("Apply loop pass {} command '{}' deferred; retrying within outer pass budget: {}",
                        applyPass, command.externalName(), pendingReason);
            }
            if (applyOutcome.needsMoreIteration()) {
                pendingReason = applyOutcome.reason();
                needsMoreIteration = true;
            }
        }

        if (!needsMoreIteration) {
            log.debug("Running skill assert verification after {} tool calls for pass {}", command.externalName(), applyPass);
            PassOutcome assertsOutcome = runSkillAssertsVerification(agent, chatMemoryId, invocationParameters, cwd, applyPass);
            log.info("Apply loop pass {} asserts verification outcome: [status={}, reason={}]",
                    applyPass, assertsOutcome.status(), assertsOutcome.reason());
            if (assertsOutcome.isSuccess()) {
                if (command == ProjectLayoutCommand.VERIFY) {
                    return new IterationResult.Done(Map.of("messages", AiMessage.from("Project layout updated")));
                }
                return new IterationResult.Done(Map.of(
                        "messages", AiMessage.from("Project layout updated"),
                        ProjectCreationState.PROJECT_LAYOUT_UPDATE_DATE_CHANNEL, OffsetDateTime.now()));
            }
            if (assertsOutcome.isDeferred()) {
                if (command == ProjectLayoutCommand.VERIFY) {
                    return new IterationResult.Done(
                            Map.of("messages", AiMessage.from(deferredReply(assertsOutcome.reason()))));
                }
                pendingReason = assertsOutcome.reason().isBlank()
                        ? ASSERTS_RETRY_REASON
                        : assertsOutcome.reason();
                log.info("Project layout apply pass {} deferred during assert verification, retrying full apply: {}",
                        applyPass, pendingReason);
                return new IterationResult.Continue(pendingReason);
            }
            pendingReason = assertsOutcome.reason().isBlank() ? ASSERTS_RETRY_REASON : assertsOutcome.reason();
            log.info("Project layout apply pass {} failed assert verification, retrying full apply: {}",
                    applyPass, pendingReason);
        }

        return new IterationResult.Continue(pendingReason);
    }

    private PassOutcome runCommandPass(Agent agent,
                                       String chatMemoryId,
                                       InvocationParameters invocationParameters,
                                       String cwd,
                                       int pass,
                                       ProjectLayoutCommand command) {
        log.debug("Starting {} apply pass {} for workspace {}", command.externalName(), pass, cwd);
        UserMessage userMessage = UserMessage.from(buildCommandRequest(command, cwd, pass));
        ChatRequest baseRequest = buildRequest(
                agent, userMessage, chatMemoryId, invocationParameters);
        ToolProviderResult toolProviderResult = chatRequestBuilder.buildToolProviderResult(
                agent, userMessage, chatMemoryId, invocationParameters);
        PassOutcome passOutcome = runToolLoop(baseRequest, toolProviderResult, chatMemoryId, cwd);
        log.info("{} apply pass {} tool loop returned [status={}, reason={}]",
                command.externalName(), pass, passOutcome.status(), passOutcome.reason());
        if (passOutcome.needsMoreIteration()) {
            log.info("{} apply pass {} needs another iteration: {}", command.externalName(), pass, passOutcome.reason());
        }
        return passOutcome;
    }

    private ProjectLayoutCommand resolveCommand(ProjectCreationState state) {
        String lastUserMessage = state.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .reduce((first, second) -> second)
                .map(UserMessage::singleText)
                .orElse("");

        Matcher matcher = COMMAND_PATTERN.matcher(lastUserMessage);
        if (matcher.find()) {
            return ProjectLayoutCommand.from(matcher.group(1));
        }

        Optional<String> stateCommand = state.projectLayoutCommand();
        if (stateCommand.isPresent()) {
            return ProjectLayoutCommand.from(stateCommand.get());
        }
        return ProjectLayoutCommand.RESYNC;
    }

    private String buildCommandRequest(ProjectLayoutCommand command, String cwd, int pass) {
        return switch (command) {
            case APPLY -> buildApplyCommand(cwd, pass);
            case RESYNC, SCHEDULE ->
                    buildResyncCommand(cwd, pass); // Schedule uses same command as resync, but with different intent and reporting expectations to the model.
            case VERIFY -> buildVerifySkillAssertsCommand(cwd, pass);
        };
    }

    private PassOutcome runSkillAssertsVerification(Agent agent,
                                                    String chatMemoryId,
                                                    InvocationParameters invocationParameters,
                                                    String cwd,
                                                    int pass) {
        log.debug("Starting skill assert verification pass {} for workspace {}", pass, cwd);
        UserMessage userMessage = UserMessage.from(buildVerifySkillAssertsCommand(cwd, pass));
        ChatRequest baseRequest = buildRequest(
                agent, userMessage, chatMemoryId, invocationParameters);
        ToolProviderResult toolProviderResult = chatRequestBuilder.buildToolProviderResult(
                agent, userMessage, chatMemoryId, invocationParameters);
        PassOutcome passOutcome = runToolLoop(baseRequest, toolProviderResult, chatMemoryId, cwd);
        log.info("Skill assert verification pass {} tool loop returned [status={}, reason={}]",
                pass, passOutcome.status(), passOutcome.reason());
        if (passOutcome.isSuccess() && indicatesSkillAssertFailure(passOutcome.reason())) {
            String reason = passOutcome.reason().isBlank()
                    ? ASSERTS_RETRY_REASON
                    : passOutcome.reason();
            log.info("Skill assert verification pass {} reported failure signal, retry requested: {}", pass, reason);
            return PassOutcome.needMoreIteration(reason);
        }
        if (passOutcome.needsMoreIteration() && passOutcome.reason().isBlank()) {
            log.info("Skill assert verification pass {} needs more iteration (blank reason), using default: {}",
                    pass, ASSERTS_RETRY_REASON);
            return PassOutcome.needMoreIteration(ASSERTS_RETRY_REASON);
        }
        log.info("Skill assert verification pass {} returning [status={}, reason={}]",
                pass, passOutcome.status(), passOutcome.reason());
        return passOutcome;
    }

    private boolean indicatesSkillAssertFailure(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase();
        return normalized.contains("project audit failed")
                || normalized.contains("audit failed")
                || normalized.contains("verification failed")
                || normalized.contains("missing assert")
                || normalized.matches(".*\\bfalse\\b.*");
    }

    private PassOutcome runToolLoop(ChatRequest baseRequest,
                                    ToolProviderResult toolProviderResult,
                                    String chatMemoryId,
                                    String cwd) {
        List<ChatMessage> messages = new ArrayList<>(baseRequest.messages());
        LinkedHashSet<String> seenToolCallOrder = new LinkedHashSet<>();
        LinkedHashMap<String, Integer> toolCallCounts = new LinkedHashMap<>();
        LinkedHashMap<String, String> lastToolResultsBySignature = new LinkedHashMap<>();
        try {
            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                log.debug("Starting tool-calling iteration {} for workspace {}", iteration, cwd);
                ChatResponse response = chatModel.chat(
                        ChatRequest.builder()
                                .messages(messages)
                                .toolSpecifications(baseRequest.toolSpecifications())
                                .build());

                AiMessage aiMessage = response.aiMessage();
                messages.add(aiMessage);

                if (!aiMessage.hasToolExecutionRequests()) {
                    log.debug("LLM finished after {} tool-calling iteration(s)", iteration + 1);
                    return classifyTerminalMessage(aiMessage, messages);
                }

                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    String toolCallSignature = toolCallSignature(request);
                    seenToolCallOrder.add(toolCallSignature);

                    int callCount = toolCallCounts.getOrDefault(toolCallSignature, 0) + 1;
                    putBounded(toolCallCounts, toolCallSignature, callCount);

                    String previousResult = lastToolResultsBySignature.getOrDefault(toolCallSignature, "");
                    if (callCount > MAX_REPEAT_PER_TOOL_CALL && indicatesBlockingToolFailure(previousResult)) {
                        String reason = "repeated tool call blocked after %d attempts for '%s': %s"
                                .formatted(callCount - 1, request.name(), previousResult);
                        log.warn("{} (tool call order={})", reason, seenToolCallOrder);
                        return PassOutcome.needMoreIteration(reason);
                    }

                    log.debug("Executing tool '{}' args={} (attempt #{})", request.name(), request.arguments(), callCount);
                    ToolExecutor executor = toolProviderResult.toolExecutorByName(request.name());
                    String result = executor != null
                            ? executor.execute(request, chatMemoryId)
                            : "Tool not found: " + request.name();
                    log.debug("Tool '{}' result: {}", request.name(), result);
                    putBounded(lastToolResultsBySignature, toolCallSignature, result);
                    messages.add(ToolExecutionResultMessage.from(request, result));
                }
            }
        } catch (Exception e) {
            log.warn("LLM tool-calling loop failed for workspace {} – continuing without model-driven file writes: {}",
                    cwd, e.getMessage());
            log.trace("Exception during tool-calling loop", e);
            return PassOutcome.deferred(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }

        String reason = "max tool iterations reached (" + MAX_TOOL_ITERATIONS + ")";
        log.warn("LLM tool-calling loop reached iteration cap for workspace {}: {}", cwd, reason);
        return PassOutcome.deferred(reason);
    }

    private PassOutcome classifyTerminalMessage(AiMessage aiMessage, List<ChatMessage> messages) {
        String text = aiMessage.text();
        log.info("LLM response without tool calls: {}", text);
        if (text == null || text.isBlank()) {
            String lastToolResult = findLastToolResultText(messages);
            if (!lastToolResult.isBlank()) {
                String normalizedToolResult = lastToolResult.toLowerCase();
                if (normalizedToolResult.contains(DEFERRED_MARKER)) {
                    return PassOutcome.deferred(lastToolResult);
                }
                if (normalizedToolResult.contains(DEFERRED_PREFIX)) {
                    return PassOutcome.deferred(lastToolResult);
                }
                if (normalizedToolResult.contains(NEED_MORE_ITERATION_MARKER)) {
                    return PassOutcome.needMoreIteration(lastToolResult);
                }
                if (indicatesSuccessfulCompletion(normalizedToolResult)) {
                    return PassOutcome.success(lastToolResult);
                }

                // Empty final model text is ambiguous: retry rather than accepting an implicit success.
                return PassOutcome.needMoreIteration(lastToolResult);
            }

            return PassOutcome.needMoreIteration("empty terminal model response without tool result");
        }

        String normalized = text.toLowerCase();
        if (normalized.contains(DEFERRED_MARKER) || normalized.contains(DEFERRED_PREFIX)) {
            log.debug("LLM response indicates deferred outcome: {}", text);
            return PassOutcome.deferred(text);
        }
        if (normalized.contains(NEED_MORE_ITERATION_MARKER)) {
            log.debug("LLM response indicates need for more iteration: {}", text);
            return PassOutcome.needMoreIteration(text);
        }
        if (!indicatesSuccessfulCompletion(normalized)) {
            return PassOutcome.needMoreIteration(text);
        }
        log.debug("LLM response indicates successful outcome: {}", text);
        return PassOutcome.success(text);
    }

    private ChatRequest buildRequest(Agent agent,
                                     UserMessage userMessage,
                                     String chatMemoryId,
                                     InvocationParameters invocationParameters) {
        return chatRequestBuilder.buildChatRequest(agent, userMessage, chatMemoryId, invocationParameters);
    }

    private boolean indicatesSuccessfulCompletion(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        return normalizedText.contains(SUCCESS_TOKEN)
                || normalizedText.contains("project audit passed")
                || normalizedText.contains("\"overall\":\"pass\"")
                || normalizedText.contains("\"overall\": \"pass\"");
    }

    private String toolCallSignature(ToolExecutionRequest request) {
        return request.name() + "|" + normalizeArguments(request.arguments());
    }

    private String normalizeArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "";
        }
        try {
            Object parsed = TOOL_SIGNATURE_MAPPER.readValue(arguments, Object.class);
            return TOOL_SIGNATURE_MAPPER.writeValueAsString(canonicalizeArgumentObject(parsed));
        } catch (JsonProcessingException e) {
            // Fallback keeps legacy behavior for non-JSON argument payloads.
            return arguments.replaceAll("\\s+", "");
        }
    }

    private Object canonicalizeArgumentObject(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
            rawMap.entrySet().stream()
                    .map(entry -> Map.entry(String.valueOf(entry.getKey()), entry.getValue()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> ordered.put(entry.getKey(), canonicalizeArgumentObject(entry.getValue())));
            return ordered;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::canonicalizeArgumentObject)
                    .toList();
        }
        return value;
    }

    private boolean indicatesBlockingToolFailure(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase();
        return normalized.contains("parent folder")
                || normalized.contains("parent directory")
                || normalized.contains("no such file")
                || normalized.contains("does not exist")
                || normalized.contains("not found")
                || normalized.contains("enoent");
    }

    private <K, V> void putBounded(LinkedHashMap<K, V> target, K key, V value) {
        if (!target.containsKey(key) && target.size() >= TOOL_CALL_HISTORY_LIMIT) {
            Iterator<K> iterator = target.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        target.put(key, value);
    }

    private String findLastToolResultText(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
                String text = toolExecutionResultMessage.text();
                return text == null ? "" : text;
            }
        }
        return "";
    }

    private enum ProjectLayoutCommand {
        APPLY("apply"),
        RESYNC("resync"),
        VERIFY("verify"),
        SCHEDULE("schedule");

        private final String externalName;

        ProjectLayoutCommand(String externalName) {
            this.externalName = externalName;
        }

        static ProjectLayoutCommand from(String raw) {
            if (raw == null || raw.isBlank()) {
                return RESYNC;
            }
            String normalized = raw.trim().toLowerCase();
            if (normalized.startsWith("apply-")) {
                return APPLY;
            }
            if (normalized.startsWith("resync-")) {
                return RESYNC;
            }
            return switch (normalized) {
                case "apply" -> APPLY;
                case "resync" -> RESYNC;
                case "verify" -> VERIFY;
                case "schedule" -> SCHEDULE;
                default -> RESYNC;
            };
        }

        boolean requiresApplyPhase() {
            return this != VERIFY;
        }

        String externalName() {
            return externalName;
        }
    }

    private enum PassStatus {
        SUCCESS,
        NEED_MORE_ITERATION,
        DEFERRED
    }

    private sealed interface IterationResult permits IterationResult.Done, IterationResult.Continue {
        record Done(Map<String, Object> stateUpdate) implements IterationResult {
        }

        record Continue(String pendingReason) implements IterationResult {
        }
    }

    private record PassOutcome(PassStatus status, String reason) {
        static PassOutcome success(String reason) {
            return new PassOutcome(PassStatus.SUCCESS, reason == null ? "" : reason);
        }

        static PassOutcome needMoreIteration(String reason) {
            return new PassOutcome(PassStatus.NEED_MORE_ITERATION, reason);
        }

        static PassOutcome deferred(String reason) {
            return new PassOutcome(PassStatus.DEFERRED, reason);
        }

        boolean isSuccess() {
            return status == PassStatus.SUCCESS;
        }

        boolean needsMoreIteration() {
            return status == PassStatus.NEED_MORE_ITERATION;
        }

        boolean isDeferred() {
            return status == PassStatus.DEFERRED;
        }
    }
}
