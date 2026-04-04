package net.osgiliath.agentscommon.langgraph.node;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import net.osgiliath.agentscommon.langgraph.state.ProjectCreationState;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.agent.parser.AgentParser;
import net.osgiliath.agentsdk.configuration.CodepromptConfiguration;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;


@Component
public class ProjectStructureMonitorNode implements NodeAction<ProjectCreationState> {

    private static final Logger log = LoggerFactory.getLogger(ProjectStructureMonitorNode.class);
    private final AgentParser agentParser;
    private final Resource agentFileResource;
    private final CodepromptConfiguration codePromptConfiguration;

    public ProjectStructureMonitorNode(
            AgentParser agentParser,
            CodepromptConfiguration codepromptConfiguration,
            ResourceLoader resourceLoader) {
        this.agentParser = agentParser;
        this.codePromptConfiguration = codepromptConfiguration;
        this.agentFileResource = resourceLoader.getResource(this.codePromptConfiguration.getAgent().getAgentFolders().stream().findFirst().get() + "foundational/templates/project-template-scaffolder.md");
    }

    @Override
    public Map<String, Object> apply(ProjectCreationState state) {
        Agent agent;
        try {
            agent = agentParser.getAgent(agentFileResource.getFile().toPath());
        } catch (IOException e) {
            log.error("Unable to load agent file", e);
            throw new RuntimeException(e);
        }

        String lastUserMessage = state.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .reduce((first, second) -> second)
                .map(UserMessage::singleText)
                .orElse("");

        // If user explicitly invoked agent validation mode
        if (lastUserMessage.contains(agent.getName() + "/validate")) {
            return Map.of();
        }

        // If user is responding affirmatively to an update prompt
        if (lastUserMessage.toLowerCase().contains("yes") || lastUserMessage.toLowerCase().contains("update")) {
            return Map.of();
        }

        // Check project layout status (not done vs done)
        Optional<Boolean> isDone = state.projectLayoutDone();
        Optional<OffsetDateTime> updateDate = state.projectLayoutUpdateDate();

        if (isDone.isEmpty() || !isDone.get()) {
            return Map.of("messages", AiMessage.from("The project structure task is not done. Would you like to setup the project?"));
        }

        // Check if layout is older than 10 days; if so, ask user to review
        if (updateDate.isPresent()) {
            long daysSinceUpdate = ChronoUnit.DAYS.between(updateDate.get(), OffsetDateTime.now());
            if (daysSinceUpdate > 10) {
                return Map.of("messages", AiMessage.from("The project structure was updated more than 10 days ago. Would you like to update it to the latest conventions?"));
            }
        }

        return Map.of();
    }
}
