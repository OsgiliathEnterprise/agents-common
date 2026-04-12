package net.osgiliath.agentscommon.cucumber.model.node;

import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.SessionContext;
import net.osgiliath.agentscommon.langgraph.state.ProjectCreationState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Node that scans the workspace to populate project creation state.
 */
@Component
public class WorkspaceScannerNode implements NodeAction<ProjectCreationState> {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceScannerNode.class);
    private static final String PROJECT_LAYOUT_PATH = "ai/tasks/001-Project_layout";

    @Override
    public Map<String, Object> apply(ProjectCreationState state) {
        Optional<SessionContext> sessionContext = state.value(AcpState.SESSION_CONTEXT);
        if (sessionContext.isEmpty() || sessionContext.get().cwd().equals(".")) {
            log.debug("Session context is missing or points to current directory, skipping workspace scan to preserve state overrides");
            return Map.of();
        }

        String cwd = sessionContext.get().cwd();
        Path projectLayoutPath = Path.of(cwd).resolve(PROJECT_LAYOUT_PATH);
        Map<String, Object> updates = new HashMap<>();

        if (Files.exists(projectLayoutPath)) {
            updates.put(ProjectCreationState.PROJECT_LAYOUT_DONE_CHANNEL, true);
            try {
                BasicFileAttributes attrs = Files.readAttributes(projectLayoutPath, BasicFileAttributes.class);
                OffsetDateTime lastModified = OffsetDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                updates.put(ProjectCreationState.PROJECT_LAYOUT_UPDATE_DATE_CHANNEL, lastModified);
                log.debug("Project layout found, last modified: {}", lastModified);
            } catch (IOException e) {
                log.warn("Unable to read project layout attributes", e);
                updates.put(ProjectCreationState.PROJECT_LAYOUT_UPDATE_DATE_CHANNEL, OffsetDateTime.now());
            }
        } else {
            updates.put(ProjectCreationState.PROJECT_LAYOUT_DONE_CHANNEL, false);
            log.debug("Project layout not found at {}", projectLayoutPath);
        }

        return updates;
    }
}
