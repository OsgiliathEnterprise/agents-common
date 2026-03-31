package net.osgiliath.agentscommon.langgraph.node;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import net.osgiliath.agentscommon.langgraph.state.ProjectCreationState;
import net.osgiliath.agentsdk.agent.parser.Agent;
import net.osgiliath.agentsdk.agent.parser.AgentHandoff;
import net.osgiliath.agentsdk.agent.parser.AgentParser;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ProjectStructureCheckerNode implements NodeAction<ProjectCreationState> {

    private static final Logger log = LoggerFactory.getLogger(ProjectStructureCheckerNode.class);
    private final AgentParser agentParser;
    private final ChatModel chatModel;
    private final Resource agentFileResource;

    public ProjectStructureCheckerNode(
            AgentParser agentParser,
            @Qualifier("primaryChatModel") ChatModel chatModel,
            @Value("classpath:ai/agents/foundational/templates/project-template-scaffolder.md") Resource agentFileResource) {
        this.agentParser = agentParser;
        this.chatModel = chatModel;
        this.agentFileResource = agentFileResource;
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

        if (lastUserMessage.contains(agent.getName() + "/validate")) {
            SystemMessage systemMessage = agentParser.getSystemPrompt(agent);

            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            systemMessage,
                            UserMessage.from("mode=validate dryRun=true")
                    ))
                    .build();
            String response = chatModel.chat(request).aiMessage().text();
            log.debug("Validate LLM response: {}", response);

            Optional<AgentHandoff> handoff;
            if (response != null && (response.contains("\"FAIL\"") || response.contains("\"overall\": \"FAIL\""))) {
                handoff = agent.getHandoffs().stream().filter(h -> h.label().contains("failed")).findFirst();
                return Map.of("messages", AiMessage.from(handoff.map(AgentHandoff::prompt).orElse("Project audit failed")));
            }
            handoff = agent.getHandoffs().stream().filter(h -> h.label().contains("passed")).findFirst();
            return Map.of("messages", AiMessage.from(handoff.map(AgentHandoff::prompt).orElse("Project audit passed")));
        }

        Optional<Boolean> isDone = state.projectLayoutDone();
        Optional<OffsetDateTime> updateDate = state.projectLayoutUpdateDate();

        if (isDone.isEmpty() || !isDone.get()) {
            return Map.of("messages", AiMessage.from("The project structure task is not done. Would you like to setup the project?"));
        }

        if (updateDate.isPresent()) {
            long daysSinceUpdate = ChronoUnit.DAYS.between(updateDate.get(), OffsetDateTime.now());
            if (daysSinceUpdate > 10) {
                return Map.of("messages", AiMessage.from("The project structure was updated more than 10 days ago. Would you like to update it to the latest conventions?"));
            }
        }

        return Map.of();
    }
}
