package net.osgiliath.agentscommon.cucumber.mocked;

import com.agentclientprotocol.model.ContentBlock;
import dev.langchain4j.mcp.McpToolProvider;
import io.cucumber.spring.CucumberContextConfiguration;
import net.osgiliath.acplanggraphlangchainbridge.acp.AcpAgentSupportBridge;
import net.osgiliath.agentscommon.AgentsCommonApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.osgiliath.agentsdk.configuration.LangChain4jConfig.TOOL_PROVIDER_FULL;
import static net.osgiliath.agentsdk.configuration.LangChain4jConfig.TOOL_PROVIDER_NONE;

/**
 * Cucumber Spring configuration that sets up the Spring Boot context for BDD tests.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = {AgentsCommonApplication.class})
public class CucumberSpringConfiguration {

    /**
     * Mock CommandLineRunners to prevent them from starting and blocking stdin.
     */
    @MockitoBean
    private CommandLineRunner commandLineRunner;

    @MockitoBean(TOOL_PROVIDER_FULL)
    private McpToolProvider toolProviderFull;
    @MockitoBean(TOOL_PROVIDER_NONE)
    private McpToolProvider toolProviderNo;


    /**
     * Provide a mock AcpAgentSupportBridge bean for testing.
     */
    @Bean
    public AcpAgentSupportBridge acpAgentSupportBridge() {
        return new AcpAgentSupportBridge() {
            @Override
            public AgentInfoBridge getAgentInfo() {
                return new AgentInfoBridge("Test Agent", "1.0");
            }


            @Override
            public AcpSessionBridge createSession(String sessionId, String cwd, Map<String, String> mcpServers) {
                return new AcpSessionBridge() {
                    @Override
                    public String getSessionId() {
                        return sessionId;
                    }

                    @Override
                    public AtomicBoolean cancelledFlag() {
                        return new AtomicBoolean(false);
                    }

                    @Override
                    public CompletableFuture<String> processPrompt(String promptText, java.util.List<ContentBlock.ResourceLink> resourceLinks) {
                        String response = buildMockResponse(promptText, resourceLinks);
                        return CompletableFuture.completedFuture(response);
                    }

                    @Override
                    public void streamPrompt(String promptText, java.util.List<ContentBlock.ResourceLink> resourceLinks, TokenConsumer consumer) {
                        String response = buildMockResponse(promptText, resourceLinks);
                        for (String token : response.split(" ")) {
                            consumer.onNext(token + " ");
                        }
                        consumer.onComplete();
                    }

                    private String buildMockResponse(String promptText, java.util.List<ContentBlock.ResourceLink> resourceLinks) {
                        if (resourceLinks != null && !resourceLinks.isEmpty()) {
                            // Include "attachment considered" when processing ResourceLinks
                            return "Mock response for: " + promptText +
                                    ". Attachment considered. Resource analysis complete. Content reviewed.";
                        }
                        return "Mock response: " + promptText;
                    }
                };
            }
        };
    }
}
