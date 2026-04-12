package net.osgiliath.agentscommon.langgraph.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import net.osgiliath.agentsdk.agent.parser.AgentChatRequestBuilder;
import net.osgiliath.agentsdk.agent.parser.AgentParser;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import net.osgiliath.agentsdk.utils.resource.ResourceLocationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ProjectStructureAuditorNodeTest {

    private ProjectStructureAuditorNode node;

    @BeforeEach
    void setUp() {
        CodepromptConfiguration configuration = mock(CodepromptConfiguration.class, RETURNS_DEEP_STUBS);
        when(configuration.getAgent().getAgentFolders()).thenReturn(List.of("/tmp"));

        ResourceLocationResolver resolver = mock(ResourceLocationResolver.class);
        when(resolver.resolveFirstExisting(anyList(), eq("foundational/templates/project-template-scaffolder.md")))
                .thenReturn(Optional.of(new ByteArrayResource(new byte[0])));

        node = new ProjectStructureAuditorNode(
                mock(AgentParser.class),
                configuration,
                resolver,
                mock(dev.langchain4j.memory.chat.ChatMemoryProvider.class),
                mock(dev.langchain4j.model.chat.ChatModel.class),
                mock(AgentChatRequestBuilder.class),
                new ProjectStructureAuditorResultInterpreter(new ObjectMapper()));
    }

    @Test
    void extractAuditResult_marksNeedsUpdateWhenMtbOrMemChecksFail() throws Exception {
        Optional<?> result = invokeExtractAuditResult("""
                {
                  "bkl001Exists": true,
                  "mtb001Exists": false,
                  "mem001Exists": true,
                  "projectLayoutTaskExists": true,
                  "projectStructureCompliant": true
                }
                """);

        assertTrue(result.isPresent());
        assertTrue(readNeedsUpdate(result.get()));
        assertEquals("MISSING", readProposalKind(result.get()));
    }

    @Test
    void extractAuditResult_marksNeedsUpdateWhenRequiredFilesAreMissing() throws Exception {
        Optional<?> result = invokeExtractAuditResult("""
                {
                  "bkl001Exists": true,
                  "projectLayoutTaskExists": true,
                  "missingRequiredFiles": ["build.gradle.kts"]
                }
                """);

        assertTrue(result.isPresent());
        assertTrue(readNeedsUpdate(result.get()));
        assertEquals("MISSING", readProposalKind(result.get()));
    }

    @Test
    void extractAuditResult_marksNeedsUpdateWhenDateIsInvalid() throws Exception {
        Optional<?> result = invokeExtractAuditResult("""
                {
                  "bkl001Exists": true,
                  "mtb001Exists": true,
                  "mem001Exists": true,
                  "projectLayoutTaskExists": true,
                  "projectStructureCompliant": true,
                  "projectLayoutLastUpdatedAt": "yesterday"
                }
                """);

        assertTrue(result.isPresent());
        assertTrue(readNeedsUpdate(result.get()));
    }

    @Test
    void extractAuditResult_detectsStaleLocalDateFormat() throws Exception {
        String staleDate = LocalDate.now(ZoneOffset.UTC).minusDays(11).toString();
        Optional<?> result = invokeExtractAuditResult("""
                {
                  "bkl001Exists": true,
                  "mtb001Exists": true,
                  "mem001Exists": true,
                  "projectLayoutTaskExists": true,
                  "projectStructureCompliant": true,
                  "projectLayoutLastUpdatedAt": "%s"
                }
                """.formatted(staleDate));

        assertTrue(result.isPresent());
        assertTrue(readNeedsUpdate(result.get()));
        assertEquals("STALE", readProposalKind(result.get()));
    }

    private Optional<?> invokeExtractAuditResult(String json) throws Exception {
        Method method = ProjectStructureAuditorNode.class.getDeclaredMethod("extractAuditResult", AiMessage.class);
        method.setAccessible(true);
        return (Optional<?>) method.invoke(node, AiMessage.from(json));
    }

    private boolean readNeedsUpdate(Object layoutAuditResult) throws Exception {
        Method method = layoutAuditResult.getClass().getDeclaredMethod("needsUpdate");
        method.setAccessible(true);
        return (boolean) method.invoke(layoutAuditResult);
    }

    private String readProposalKind(Object layoutAuditResult) throws Exception {
        Method method = layoutAuditResult.getClass().getDeclaredMethod("proposalKind");
        method.setAccessible(true);
        return method.invoke(layoutAuditResult).toString();
    }
}

