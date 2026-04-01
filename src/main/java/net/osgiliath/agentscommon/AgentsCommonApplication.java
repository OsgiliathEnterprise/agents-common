package net.osgiliath.agentscommon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Code Prompt Framework Application.
 * <p>
 * Koog ACP Server Frontend + LangChain4j Agent Orchestrator
 */
@SpringBootApplication(scanBasePackages = {"net.osgiliath.agentscommon", "net.osgiliath.agentsdk", "net.osgiliath.acplanggraphlangchainbridge"})
public class AgentsCommonApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentsCommonApplication.class, args);
    }
}




