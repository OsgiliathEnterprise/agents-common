package net.osgiliath.agentscommon.langgraph.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import net.osgiliath.agentscommon.langgraph.node.auditor.ProjectStructureAuditorResultInterpreter;
import net.osgiliath.agentscommon.langgraph.node.model.UpdateProposalKind;
import net.osgiliath.agentscommon.langgraph.state.ProjectCreationState;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStructureAuditorResultInterpreterTest {

    private final ProjectStructureAuditorResultInterpreter interpreter =
            new ProjectStructureAuditorResultInterpreter(new ObjectMapper());

    @Test
    void extractAuditResultReturnsMissingWhenAuditFactsShowMissing() {
        String payload = """
                {
                  "bkl001Exists": false,
                  "mtb001Exists": true,
                  "mem001Exists": true,
                  "projectLayoutTaskExists": true,
                  "projectStructureCompliant": true
                }
                """;

        assertThat(interpreter.extractAuditResult(AiMessage.from(payload)))
                .contains(UpdateProposalKind.MISSING);
    }

    @Test
    void extractAuditResultReturnsMissingWhenRequiredFilesAreMissing() {
        String payload = """
                {
                  "projectStructureCompliant": true,
                  "missingRequiredFiles": ["BKL-001.md"]
                }
                """;

        assertThat(interpreter.extractAuditResult(AiMessage.from(payload)))
                .contains(UpdateProposalKind.MISSING);
    }

    @Test
    void extractAuditResultReturnsStaleWhenLastUpdateIsOlderThanTenDays() {
        String payload = """
                {
                  "projectLayoutLastUpdatedAt": "%s"
                }
                """.formatted(OffsetDateTime.now().minusDays(11).withNano(0));

        assertThat(interpreter.extractAuditResult(AiMessage.from(payload)))
                .contains(UpdateProposalKind.STALE);
    }

    @Test
    void extractAuditResultReturnsUnknownForContractFailureWithoutMissingOrStaleSignals() {
        String payload = """
                {
                  "overall": "FAIL",
                  "checks": []
                }
                """;

        assertThat(interpreter.extractAuditResult(AiMessage.from(payload)))
                .contains(UpdateProposalKind.UNKNOWN);
    }

    @Test
    void extractAuditResultUsesReasonTextAsFallbackForMissingClassification() {
        String payload = """
                {
                  "needsUpdate": true,
                  "reason": "BKL-001 is missing from the project structure."
                }
                """;

        assertThat(interpreter.extractAuditResult(AiMessage.from(payload)))
                .contains(UpdateProposalKind.MISSING);
    }

    @Test
    void extractAuditResultUsesReasonTextAsFallbackForStaleClassification() {
        String payload = """
                {
                  "needsUpdate": true,
                  "reason": "The project structure is older than 10 days."
                }
                """;

        assertThat(interpreter.extractAuditResult(AiMessage.from(payload)))
                .contains(UpdateProposalKind.STALE);
    }

    @Test
    void extractAuditResultReturnsNoUpdateWhenExplicitNeedsUpdateIsFalse() {
        String payload = """
                {
                  "needsUpdate": false,
                  "reason": "Everything is compliant"
                }
                """;

        assertThat(interpreter.extractAuditResult(AiMessage.from(payload)))
                .contains(UpdateProposalKind.NO_UPDATE_NEEDED);
    }

    @Test
    void extractAuditResultLetsExplicitNeedsUpdateFalseOverrideMissingSignals() {
        String payload = """
                {
                  "needsUpdate": false,
                  "bkl001Exists": false,
                  "projectStructureCompliant": false
                }
                """;

        assertThat(interpreter.extractAuditResult(AiMessage.from(payload)))
                .contains(UpdateProposalKind.NO_UPDATE_NEEDED);
    }

    @Test
    void extractAuditResultWithStateReturnsParsedMissingKind() {
        ProjectCreationState state = new ProjectCreationState(Map.of());
        String payload = """
                {
                  "bkl001Exists": false,
                  "mtb001Exists": true,
                  "mem001Exists": true,
                  "projectLayoutTaskExists": true,
                  "projectStructureCompliant": true
                }
                """;

        UpdateProposalKind result = interpreter.extractAuditResult(AiMessage.from(payload), state);

        assertThat(result).isEqualTo(UpdateProposalKind.MISSING);
    }

    @Test
    void extractAuditResultWithStateFallsBackToMissingKindWhenPayloadIsUnparseableAndLayoutMissing() {
        ProjectCreationState state = new ProjectCreationState(Map.of());

        UpdateProposalKind result = interpreter.extractAuditResult(AiMessage.from("not-json"), state);

        assertThat(result).isEqualTo(UpdateProposalKind.MISSING);
    }

    @Test
    void extractAuditResultWithStateFallsBackToStaleKindWhenLayoutIsOld() {
        ProjectCreationState state = new ProjectCreationState(Map.of(
                ProjectCreationState.PROJECT_LAYOUT_DONE_CHANNEL, true,
                ProjectCreationState.PROJECT_LAYOUT_UPDATE_DATE_CHANNEL, OffsetDateTime.now().minusDays(11)
        ));

        UpdateProposalKind result = interpreter.extractAuditResult(null, state);

        assertThat(result).isEqualTo(UpdateProposalKind.STALE);
    }

    @Test
    void extractAuditResultWithStateReturnsNoUpdateNeededWhenNoUpdateIsNeeded() {
        ProjectCreationState state = new ProjectCreationState(Map.of(
                ProjectCreationState.PROJECT_LAYOUT_DONE_CHANNEL, true,
                ProjectCreationState.PROJECT_LAYOUT_UPDATE_DATE_CHANNEL, OffsetDateTime.now().minusDays(1)
        ));

        UpdateProposalKind result = interpreter.extractAuditResult(null, state);

        assertThat(result).isEqualTo(UpdateProposalKind.NO_UPDATE_NEEDED);
    }

}


