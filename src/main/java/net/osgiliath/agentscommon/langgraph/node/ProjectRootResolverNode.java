package net.osgiliath.agentscommon.langgraph.node;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.message.ResourceLinkContent;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
import net.osgiliath.agentscommon.langgraph.state.WorspaceState;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ProjectRootResolverNode implements NodeAction<WorspaceState<ChatMessage>> {

    private static final Logger log = LoggerFactory.getLogger(ProjectRootResolverNode.class);

    @Override
    public Map<String, Object> apply(WorspaceState<ChatMessage> state) {
        Optional<SessionContext> sessionContext = state.value(AcpState.SESSION_CONTEXT);
        if (sessionContext.isEmpty()) {
            log.debug("Session context is missing, unable to resolve project root");
            return Map.of();
        }
        Path cwdPath;
        try {
            cwdPath = Path.of(sessionContext.get().cwd()).normalize();
        } catch (InvalidPathException exception) {
            log.warn("Session cwd is not a valid path: {}", sessionContext.get().cwd(), exception);
            return Map.of();
        }
        if (!Files.exists(cwdPath)) {
            log.warn("Session cwd does not exist: {}", cwdPath);
            return Map.of();
        }

        List<ResourceLinkContent> attachments = state.attachmentsMetadata();
        if (!attachments.isEmpty()) {
            return resolveWithFile(cwdPath, attachments.getFirst());
        }

        return findGitRoot(cwdPath)
                .<Map<String, Object>>map(uri -> Map.of(WorspaceState.WORKSPACE_ROOT_CHANNEL, uri))
                .orElseGet(Map::of);
    }

    private Map<String, Object> resolveWithFile(Path cwdPath, ResourceLinkContent fileContent) {
        Path filePath;
        try {
            filePath = Path.of(fileContent.uri()).normalize();
        } catch (Exception e) {
            log.warn("File URI in context is not a valid path: {}", fileContent.uri(), e);
            return Map.of();
        }

        if (!filePath.startsWith(cwdPath)) {
            String message = String.format(
                    "The file '%s' and the working directory '%s' are disjoint and should be under the same root.",
                    filePath, cwdPath);
            log.warn(message);
            return Map.of(MessagesState.MESSAGES_STATE, AiMessage.from(message));
        }

        Optional<URI> gitRoot = findGitRootUpToCwd(filePath, cwdPath);
        return gitRoot
                .<Map<String, Object>>map(uri -> Map.of(WorspaceState.WORKSPACE_ROOT_CHANNEL, uri))
                .orElseGet(() -> Map.of(WorspaceState.WORKSPACE_ROOT_CHANNEL, cwdPath.toUri()));
    }

    private Optional<URI> findGitRootUpToCwd(Path filePath, Path cwdPath) {
        Path dir = Files.isDirectory(filePath) ? filePath : filePath.getParent();
        while (dir != null && dir.startsWith(cwdPath)) {
            if (Files.exists(dir.resolve(".git"))) {
                return Optional.of(dir.toUri());
            }
            if (dir.equals(cwdPath)) {
                break;
            }
            dir = dir.getParent();
        }
        return Optional.empty();
    }

    private Optional<URI> findGitRoot(Path startPath) {
        Path dir = Files.isDirectory(startPath) ? startPath : startPath.getParent();
        while (dir != null) {
            if (Files.exists(dir.resolve(".git"))) {
                return Optional.of(dir.toUri());
            }
            dir = dir.getParent();
        }
        return Optional.empty();
    }
}
