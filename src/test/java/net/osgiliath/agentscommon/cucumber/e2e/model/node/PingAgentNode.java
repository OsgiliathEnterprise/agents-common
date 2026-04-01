package net.osgiliath.agentscommon.cucumber.e2e.model.node;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node that simply responds with "pong".
 */
@Component
public class PingAgentNode implements NodeAction<AcpState<ChatMessage>> {

    @Override
    public Map<String, Object> apply(AcpState<ChatMessage> state) {
        return Map.of("messages", AiMessage.from("Project audit passed"));
    }
}
