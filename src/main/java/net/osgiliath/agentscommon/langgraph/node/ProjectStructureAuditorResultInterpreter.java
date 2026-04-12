package net.osgiliath.agentscommon.langgraph.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import net.osgiliath.agentscommon.langgraph.node.model.LayoutAuditResult;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProjectStructureAuditorResultInterpreter {

    static final String UPDATE_QUESTION_PHRASE = "Would you like to update it to the latest conventions?";
    private static final Logger log = LoggerFactory.getLogger(ProjectStructureAuditorResultInterpreter.class);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*})\\s*```", Pattern.CASE_INSENSITIVE);
    private final AuditPayloadAdapter payloadAdapter;
    private final ContractResultAdapter contractResultAdapter;

    public ProjectStructureAuditorResultInterpreter(ObjectMapper objectMapper) {
        this.payloadAdapter = new JsonAuditPayloadAdapter(objectMapper);
        this.contractResultAdapter = new DefaultContractResultAdapter();
    }

    public @NonNull Optional<LayoutAuditResult> extractAuditResult(AiMessage lastAiMessage) {
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

            boolean needsUpdate = computeNeedsUpdate(json, facts, isStale, invalidLastUpdatedAt, agentContractNeedsUpdate);

            UpdateProposalKind kind = classifyUpdateKind(
                    facts,
                    agentContractNeedsUpdate,
                    isStale,
                    json.path("reason").asText(""));
            return Optional.of(new LayoutAuditResult(needsUpdate, kind));
        } catch (Exception e) {
            log.debug("Unable to parse layout-audit JSON from model output", e);
            return Optional.empty();
        }
    }

    private AuditFacts readAuditFacts(JsonNode json) {
        boolean missingRequiredFiles = json.path("missingRequiredFiles").isArray() && json.path("missingRequiredFiles").size() > 0;
        boolean projectStructureCompliant = readOptionalBoolean(json, "projectStructureCompliant", true);
        if (!json.has("projectStructureCompliant") && missingRequiredFiles) {
            projectStructureCompliant = false;
        }
        return new AuditFacts(
                hasCustomAuditFields(json),
                readOptionalBoolean(json, "bkl001Exists", true),
                readOptionalBoolean(json, "mtb001Exists", true),
                readOptionalBoolean(json, "mem001Exists", true),
                readOptionalBoolean(json, "projectLayoutTaskExists", true),
                projectStructureCompliant,
                missingRequiredFiles
        );
    }

    private boolean hasCustomAuditFields(JsonNode json) {
        return json.has("bkl001Exists")
                || json.has("mtb001Exists")
                || json.has("mem001Exists")
                || json.has("projectLayoutTaskExists")
                || json.has("projectStructureCompliant")
                || json.has("missingRequiredFiles")
                || json.has("projectLayoutLastUpdatedAt")
                || json.has("needsUpdate");
    }

    private boolean readOptionalBoolean(JsonNode json, String field, boolean defaultValue) {
        if (!json.has(field) || json.path(field).isNull()) {
            return defaultValue;
        }
        return json.path(field).asBoolean(defaultValue);
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

    private boolean computeNeedsUpdate(JsonNode json,
                                       AuditFacts facts,
                                       boolean isStale,
                                       boolean invalidLastUpdatedAt,
                                       boolean agentContractNeedsUpdate) {
        if (json.has("needsUpdate")) {
            return json.path("needsUpdate").asBoolean();
        }
        return !facts.bkl001Exists()
                || !facts.mtb001Exists()
                || !facts.mem001Exists()
                || !facts.taskExists()
                || !facts.projectStructureCompliant()
                || facts.missingRequiredFiles()
                || isStale
                || invalidLastUpdatedAt
                || agentContractNeedsUpdate;
    }

    public String resolveUpdateProposal(Optional<LayoutAuditResult> auditResult, ProjectCreationState state) {
        if (auditResult.isPresent() && auditResult.get().needsUpdate()) {
            return switch (auditResult.get().proposalKind()) {
                case MISSING -> "The project structure task is not done. Would you like to setup the project?";
                case STALE -> "The project structure was updated more than 10 days ago. " + UPDATE_QUESTION_PHRASE;
                case UNKNOWN -> fallbackProposalFromState(state);
            };
        }

        return fallbackProposalFromState(state);
    }

    private String fallbackProposalFromState(ProjectCreationState state) {
        boolean layoutMissing = state.projectLayoutDone().isEmpty() || !state.projectLayoutDone().orElse(false);
        return layoutMissing
                ? "The project structure task is not done. Would you like to setup the project?"
                : "The project structure was updated more than 10 days ago. " + UPDATE_QUESTION_PHRASE;
    }

    private UpdateProposalKind classifyUpdateKind(AuditFacts facts,
                                                  boolean agentContractNeedsUpdate,
                                                  boolean isStale,
                                                  String reasonText) {
        if (!facts.mtb001Exists()
                || !facts.mem001Exists()
                || !facts.bkl001Exists()
                || !facts.taskExists()
                || !facts.projectStructureCompliant()) {
            return UpdateProposalKind.MISSING;
        }
        if (isStale) {
            return UpdateProposalKind.STALE;
        }

        return switch (classifyReasonHint(reasonText)) {
            case MISSING -> UpdateProposalKind.MISSING;
            case STALE -> UpdateProposalKind.STALE;
            case NONE -> UpdateProposalKind.UNKNOWN;
        };
    }

    private ReasonHint classifyReasonHint(String reasonText) {
        String reason = reasonText == null ? "" : reasonText.toLowerCase();
        if (reason.contains("missing")
                || reason.contains("not done")
                || reason.contains("bkl-001")
                || reason.contains("mtb-001")
                || reason.contains("mem-001")
                || reason.contains("projectstructurecompliant")) {
            return ReasonHint.MISSING;
        }
        if (reason.contains("stale") || reason.contains("older") || reason.contains("10 day")) {
            return ReasonHint.STALE;
        }
        return ReasonHint.NONE;
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

    private enum ReasonHint {
        MISSING,
        STALE,
        NONE
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
