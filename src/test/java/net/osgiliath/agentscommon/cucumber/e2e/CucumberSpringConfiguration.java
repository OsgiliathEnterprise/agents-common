package net.osgiliath.agentscommon.cucumber.e2e;

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
}
