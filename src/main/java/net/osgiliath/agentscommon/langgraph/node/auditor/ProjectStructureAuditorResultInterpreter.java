package net.osgiliath.agentscommon.langgraph.node.auditor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import net.osgiliath.agentscommon.langgraph.node.model.UpdateProposalKind;
import net.osgiliath.agentscommon.langgraph.state.ProjectCreationState;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProjectStructureAuditorResultInterpreter {

    public static final String BACKLOG_AUDIT_RETURNED_ERROR = "bkl001Exists";
    public static final String PROJECT_TEMPLATE_RETURNED_ERROR = "mtb001Exists";
    public static final String PROJECT_TEMPLATE_AUDIT_RETURNED_ERROR1 = PROJECT_TEMPLATE_RETURNED_ERROR;
    public static final String PROJECT_TEMPLATE_AUDIT_RETURNED_ERROR = PROJECT_TEMPLATE_AUDIT_RETURNED_ERROR1;
    public static final String MEMORY_AUDIT_RETURNED_ERROR = "mem001Exists";
    public static final String PROJECT_LAYOUT_BACKLOG_TASK_EXISTS = "projectLayoutTaskExists";
    public static final String PROJECT_NEEDS_UPDATE = "needsUpdate";
    private static final Logger log = LoggerFactory.getLogger(ProjectStructureAuditorResultInterpreter.class);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*})\\s*```", Pattern.CASE_INSENSITIVE);
    private final AuditPayloadAdapter payloadAdapter;
    private final ContractResultAdapter contractResultAdapter;

    /**
     * Constructor for ProjectStructureAuditorResultInterpreter.
     *
     * @param objectMapper the ObjectMapper to use for parsing JSON payloads from AI messages. This allows for flexible configuration of the JSON parsing behavior, such as handling of unknown properties or custom deserialization logic. The ObjectMapper is wrapped in a JsonAuditPayloadAdapter to abstract away the details of how the JSON is extracted and parsed from the AI message text.
     */
    public ProjectStructureAuditorResultInterpreter(ObjectMapper objectMapper) {
        this.payloadAdapter = new JsonAuditPayloadAdapter(objectMapper);
        this.contractResultAdapter = new DefaultContractResultAdapter();
    }

    /**
     * Extracts the layout audit proposal kind from the given AI message. The method attempts to parse the message text as JSON, looking for specific fields that indicate the presence of a layout audit result. It checks for required files, project structure compliance, and the last updated timestamp to determine what kind of update proposal, if any, should be made. If the necessary information is present and can be parsed successfully, it returns an Optional containing the resolved UpdateProposalKind. If the message does not contain valid JSON or does not have the expected fields, it returns an empty Optional.
     *
     * @param lastAiMessage the last AI message that may contain the layout audit result in its text. The method will attempt to extract and parse JSON from this message to determine the audit result. If the message is null, has no text, or the text is blank, the method will immediately return an empty Optional, indicating that no audit result could be extracted.
     * @return an Optional containing the UpdateProposalKind if it could be successfully extracted and parsed from the AI message, or an empty Optional if the message did not contain valid JSON or the expected fields for a layout audit result.
     */
    public @NonNull Optional<UpdateProposalKind> extractAuditResult(AiMessage lastAiMessage) {
        if (lastAiMessage == null || lastAiMessage.text() == null || lastAiMessage.text().isBlank()) {
            return Optional.empty();
        }

        try {
            Optional<JsonNode> parsedPayload = payloadAdapter.parsePayload(lastAiMessage.text(), this::extractJsonPayload);
            if (parsedPayload.isEmpty()) {
                return Optional.empty();
            }
            JsonNode json = parsedPayload.get();

            AuditFacts facts = readAuditFacts(json);
            boolean hasAgentContractResult = contractResultAdapter.hasContractResult(json);
            boolean agentContractNeedsUpdate = hasAgentContractResult && contractResultAdapter.requiresUpdate(json);

            if (!facts.hasCustomAuditFields() && !hasAgentContractResult) {
                return Optional.empty();
            }

            LastUpdatedAtStatus lastUpdatedAt = parseLastUpdatedAtStatus(json.path("projectLayoutLastUpdatedAt").asText(null));
            boolean isStale = isStale(lastUpdatedAt);
            boolean invalidLastUpdatedAt = lastUpdatedAt instanceof LastUpdatedAtStatus.Invalid;

            UpdateDecision decision = evaluateUpdateDecision(
                    json,
                    facts,
                    isStale,
                    invalidLastUpdatedAt,
                    agentContractNeedsUpdate,
                    json.path("reason").asText(""));
            return Optional.of(decision.kind());
        } catch (Exception e) {
            log.debug("Unable to parse layout-audit JSON from model output", e);
            return Optional.empty();
        }
    }

    /**
     * Extracts the final proposal kind for the node, including state-based fallback when the model output is absent or unparseable.
     * This is the first pass of the two-pass interpreter flow; callers can then resolve a user-facing message in a second pass.
     *
     * @param lastAiMessage the last AI message that may contain the layout audit result
     * @param state         the current project creation state used for fallback when the model output is absent or unparseable
     * @return the final proposal kind covering parsed and fallback cases
     */
    public @NonNull UpdateProposalKind extractAuditResult(AiMessage lastAiMessage, ProjectCreationState state) {
        Optional<UpdateProposalKind> parsedResult = extractAuditResult(lastAiMessage);
        if (parsedResult.isPresent()) {
            return parsedResult.get();
        }

        boolean needsUpdate = fallbackNeedsUpdateFromScannerState(state);
        if (!needsUpdate) {
            return UpdateProposalKind.NO_UPDATE_NEEDED;
        }

        return fallbackProposalKindFromState(state);
    }

    private AuditFacts readAuditFacts(JsonNode json) {
        JsonNode missingRequiredFilesNode = json.path("missingRequiredFiles");
        boolean missingRequiredFiles = missingRequiredFilesNode.isArray() && !missingRequiredFilesNode.isEmpty();
        boolean projectStructureCompliant = readOptionalBoolean(json, "projectStructureCompliant");
        if (!json.has("projectStructureCompliant") && missingRequiredFiles) {
            projectStructureCompliant = false;
        }
        return new AuditFacts(
                hasCustomAuditFields(json),
                readOptionalBoolean(json, BACKLOG_AUDIT_RETURNED_ERROR),
                readOptionalBoolean(json, PROJECT_TEMPLATE_AUDIT_RETURNED_ERROR),
                readOptionalBoolean(json, MEMORY_AUDIT_RETURNED_ERROR),
                readOptionalBoolean(json, PROJECT_LAYOUT_BACKLOG_TASK_EXISTS),
                projectStructureCompliant,
                missingRequiredFiles
        );
    }

    private boolean hasCustomAuditFields(JsonNode json) {
        return json.has(BACKLOG_AUDIT_RETURNED_ERROR)
                || json.has(PROJECT_TEMPLATE_AUDIT_RETURNED_ERROR)
                || json.has(MEMORY_AUDIT_RETURNED_ERROR)
                || json.has(PROJECT_LAYOUT_BACKLOG_TASK_EXISTS)
                || json.has("projectStructureCompliant")
                || json.has("missingRequiredFiles")
                || json.has("projectLayoutLastUpdatedAt")
                || json.has(PROJECT_NEEDS_UPDATE);
    }

    private boolean readOptionalBoolean(JsonNode json, String field) {
        if (!json.has(field) || json.path(field).isNull()) {
            return true;
        }
        return json.path(field).asBoolean(true);
    }

    private LastUpdatedAtStatus parseLastUpdatedAtStatus(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return new LastUpdatedAtStatus.Absent();
        }

        try {
            return new LastUpdatedAtStatus.Valid(OffsetDateTime.parse(value));
        } catch (DateTimeParseException ignored) {
            // Try additional common date formats used by tool outputs.
        }

        try {
            return new LastUpdatedAtStatus.Valid(Instant.parse(value).atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            // Continue with LocalDate fallback.
        }

        try {
            return new LastUpdatedAtStatus.Valid(LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return new LastUpdatedAtStatus.Invalid(value);
        }
    }

    private boolean isStale(LastUpdatedAtStatus status) {
        return switch (status) {
            case LastUpdatedAtStatus.Valid valid -> ChronoUnit.DAYS.between(valid.value(), OffsetDateTime.now()) > 10;
            case LastUpdatedAtStatus.Absent ignored -> false;
            case LastUpdatedAtStatus.Invalid ignored -> false;
        };
    }


    private UpdateProposalKind fallbackProposalKindFromState(ProjectCreationState state) {
        boolean layoutMissing = state.projectLayoutDone().isEmpty() || !state.projectLayoutDone().orElse(false);
        return layoutMissing ? UpdateProposalKind.MISSING : UpdateProposalKind.STALE;
    }

    private UpdateDecision evaluateUpdateDecision(JsonNode json,
                                                  AuditFacts facts,
                                                  boolean isStale,
                                                  boolean invalidLastUpdatedAt,
                                                  boolean agentContractNeedsUpdate,
                                                  String reasonText) {
        Optional<Boolean> explicitNeedsUpdate = readExplicitNeedsUpdate(json);
        if (explicitNeedsUpdate.isPresent() && !explicitNeedsUpdate.get()) {
            return new UpdateDecision(UpdateProposalKind.NO_UPDATE_NEEDED);
        }

        if (hasMissingLayoutSignals(facts)) {
            return new UpdateDecision(UpdateProposalKind.MISSING);
        }
        if (isStale) {
            return new UpdateDecision(UpdateProposalKind.STALE);
        }

        UpdateProposalKind reasonKind = classifyReasonKind(reasonText);
        if (reasonKind == UpdateProposalKind.MISSING || reasonKind == UpdateProposalKind.STALE) {
            return new UpdateDecision(reasonKind);
        }

        if (invalidLastUpdatedAt || agentContractNeedsUpdate || explicitNeedsUpdate.orElse(false)) {
            return new UpdateDecision(UpdateProposalKind.UNKNOWN);
        }

        return new UpdateDecision(UpdateProposalKind.NO_UPDATE_NEEDED);
    }

    private Optional<Boolean> readExplicitNeedsUpdate(JsonNode json) {
        if (!json.has(PROJECT_NEEDS_UPDATE) || json.path(PROJECT_NEEDS_UPDATE).isNull()) {
            return Optional.empty();
        }
        return Optional.of(json.path(PROJECT_NEEDS_UPDATE).asBoolean());
    }


    private boolean hasMissingLayoutSignals(AuditFacts facts) {
        return hasRequiredAuditFailures(facts) || facts.missingRequiredFiles();
    }

    private UpdateProposalKind classifyReasonKind(String reasonText) {

        String reason = reasonText == null ? "" : reasonText.toLowerCase(Locale.ROOT);
        if (reason.contains("missing")
                || reason.contains("not done")
                || reason.contains("bkl-001")
                || reason.contains("mtb-001")
                || reason.contains("mem-001")
                || reason.contains("projectstructurecompliant")) {
            return UpdateProposalKind.MISSING;
        }
        if (reason.contains("stale") || reason.contains("older") || reason.contains("10 day")) {
            return UpdateProposalKind.STALE;
        }
        return UpdateProposalKind.NO_UPDATE_NEEDED;
    }

    private boolean hasRequiredAuditFailures(AuditFacts facts) {
        return !facts.bkl001Exists()
                || !facts.mtb001Exists()
                || !facts.mem001Exists()
                || !facts.taskExists()
                || !facts.projectStructureCompliant();
    }

    public boolean fallbackNeedsUpdateFromScannerState(ProjectCreationState state) {
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


    private sealed interface LastUpdatedAtStatus permits LastUpdatedAtStatus.Absent, LastUpdatedAtStatus.Valid, LastUpdatedAtStatus.Invalid {
        record Absent() implements LastUpdatedAtStatus {
        }

        record Valid(OffsetDateTime value) implements LastUpdatedAtStatus {
        }

        record Invalid(String rawValue) implements LastUpdatedAtStatus {
        }
    }

    interface AuditPayloadAdapter {
        Optional<JsonNode> parsePayload(String messageText, JsonExtractor extractor) throws Exception;
    }

    interface ContractResultAdapter {
        boolean hasContractResult(JsonNode json);

        boolean requiresUpdate(JsonNode json);
    }

    @FunctionalInterface
    interface JsonExtractor {
        String extract(String text);
    }

    private record AuditFacts(boolean hasCustomAuditFields,
                              boolean bkl001Exists,
                              boolean mtb001Exists,
                              boolean mem001Exists,
                              boolean taskExists,
                              boolean projectStructureCompliant,
                              boolean missingRequiredFiles) {
    }

    private record UpdateDecision(UpdateProposalKind kind) {
    }

    private static final class JsonAuditPayloadAdapter implements AuditPayloadAdapter {

        private final ObjectMapper objectMapper;

        private JsonAuditPayloadAdapter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public Optional<JsonNode> parsePayload(String messageText, JsonExtractor extractor) throws Exception {
            String jsonPayload = extractor.extract(messageText);
            if (jsonPayload == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(jsonPayload));
        }
    }

    private static final class DefaultContractResultAdapter implements ContractResultAdapter {

        @Override
        public boolean hasContractResult(JsonNode json) {
            return json.has("overall") || (json.has("checks") && json.path("checks").isArray());
        }

        @Override
        public boolean requiresUpdate(JsonNode json) {
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
    }
}
