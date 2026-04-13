package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Code Prompt Framework Application.
 * <p>
 * Koog ACP Server Frontend + LangChain4j Agent Orchestrator
 */
@SpringBootApplication(scanBasePackages = {"com.example.module"})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}




