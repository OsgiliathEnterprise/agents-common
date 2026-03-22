package net.osgiliath.agentscommon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Code Prompt Framework Application.
 *
 * Koog ACP Server Frontend + LangChain4j Agent Orchestrator
 */
@SpringBootApplication(scanBasePackages = "net.osgiliath.agentscommon")
public class AgentsCommonApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentsCommonApplication.class, args);
    }
}




