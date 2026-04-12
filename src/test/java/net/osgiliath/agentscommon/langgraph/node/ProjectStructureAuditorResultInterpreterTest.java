package net.osgiliath.agentscommon.langgraph.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import net.osgiliath.agentscommon.langgraph.node.model.LayoutAuditResult;
import net.osgiliath.agentscommon.langgraph.node.model.UpdateProposalKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProjectStructureAuditorResultInterpreterTest {

    private ProjectStructureAuditorResultInterpreter interpreter;

    @BeforeEach
    void setUp() {
        interpreter = new ProjectStructureAuditorResultInterpreter(new ObjectMapper());
    }

    @Test
    void returnsEmptyWhenMessageIsBlank() {
        assertTrue(interpreter.extractAuditResult(AiMessage.from("   ")).isEmpty());
    }

    @Test
    void marksMissingWhenAnyCoreCheckFails() {
        Optional<LayoutAuditResult> result = interpreter.extractAuditResult(AiMessage.from("""
                {
                  "bkl001Exists": true,
                  "mtb001Exists": false,
                  "mem001Exists": true,
                  "projectLayoutTaskExists": true,
                  "projectStructureCompliant": true
                }
                """));

        assertTrue(result.isPresent());
        assertTrue(result.get().needsUpdate());
        assertEquals(UpdateProposalKind.MISSING, result.get().proposalKind());
    }

    @Test
    void marksStaleWhenLastUpdateIsOlderThanTenDays() {
        String staleDate = LocalDate.now(ZoneOffset.UTC).minusDays(11).toString();
        Optional<LayoutAuditResult> result = interpreter.extractAuditResult(AiMessage.from("""
                {
                  "bkl001Exists": true,
                  "mtb001Exists": true,
                  "mem001Exists": true,
                  "projectLayoutTaskExists": true,
                  "projectStructureCompliant": true,
                  "projectLayoutLastUpdatedAt": "%s"
                }
                """.formatted(staleDate)));

        assertTrue(result.isPresent());
        assertTrue(result.get().needsUpdate());
        assertEquals(UpdateProposalKind.STALE, result.get().proposalKind());
    }

    @Test
    void marksNeedsUpdateWhenDateValueIsInvalid() {
        Optional<LayoutAuditResult> result = interpreter.extractAuditResult(AiMessage.from("""
                {
                  "bkl001Exists": true,
                  "mtb001Exists": true,
                  "mem001Exists": true,
                  "projectLayoutTaskExists": true,
                  "projectStructureCompliant": true,
                  "projectLayoutLastUpdatedAt": "yesterday"
                }
                """));

        assertTrue(result.isPresent());
        assertTrue(result.get().needsUpdate());
    }

    @Test
    void acceptsAgentContractPayloadAndFlagsFailingStatus() {
        Optional<LayoutAuditResult> result = interpreter.extractAuditResult(AiMessage.from("""
                {
                  "overall": "PASS",
                  "checks": [
                    { "id": "AC-1", "status": "PASS" },
                    { "id": "AC-2", "status": "FAILED" }
                  ]
                }
                """));

        assertTrue(result.isPresent());
        assertTrue(result.get().needsUpdate());
        assertEquals(UpdateProposalKind.MISSING, result.get().proposalKind());
    }

    @Test
    void returnsEmptyWhenNoRecognizedAuditOrContractFieldsExist() {
        Optional<LayoutAuditResult> result = interpreter.extractAuditResult(AiMessage.from("""
                {
                  "foo": "bar"
                }
                """));

        assertTrue(result.isEmpty());
    }
}

