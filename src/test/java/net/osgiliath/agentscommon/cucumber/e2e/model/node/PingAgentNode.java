package net.osgiliath.agentscommon.cucumber.e2e.model.node;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class PingAgentNode implements NodeAction<AcpState<ChatMessage>> {

    @Override
    public Map<String, Object> apply(AcpState<ChatMessage> state) {
        String lastUserMessage = state.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .reduce((first, second) -> second)
                .map(UserMessage::singleText)
                .orElse("")
                .toLowerCase();

        Path workspaceRoot = state.value(AcpState.SESSION_CONTEXT)
                .filter(SessionContext.class::isInstance)
                .map(SessionContext.class::cast)
                .map(SessionContext::cwd)
                .filter(cwd -> !".".equals(cwd))
                .map(Path::of)
                .orElse(null);

        if (workspaceRoot == null) {
            return Map.of("messages", AiMessage.from("Project audit failed"));
        }

        if (lastUserMessage.contains("/validate")) {
            return Map.of("messages", AiMessage.from(isCompliant(workspaceRoot) ? "Project audit passed" : "Project audit failed"));
        }

        if (lastUserMessage.contains("yes") || lastUserMessage.contains("update")) {
            return Map.of("messages", AiMessage.from("Project layout updated"));
        }

        return Map.of("messages", AiMessage.from(isCompliant(workspaceRoot) ? "Project audit passed" : "Project audit failed"));
    }

    private boolean isCompliant(Path root) {
        return existsAndNonEmpty(root.resolve("build.gradle.kts"))
                && existsAndNonEmpty(root.resolve("settings.gradle.kts"))
                && existsAndNonEmpty(root.resolve("jreleaser.yml"))
                && existsAndNonEmpty(root.resolve("gradle/libs.versions.toml"))
                && existsAndNonEmpty(root.resolve(".github/dependabot.yml"))
                && existsAndNonEmpty(root.resolve(".github/workflows/ci.yml"))
                && existsAndNonEmpty(root.resolve("ai/MEMORY.md"))
                && Files.isDirectory(root.resolve("ai/tasks/001-Project_layout"));
    }

    private boolean existsAndNonEmpty(Path path) {
        try {
            return Files.exists(path) && Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

}
