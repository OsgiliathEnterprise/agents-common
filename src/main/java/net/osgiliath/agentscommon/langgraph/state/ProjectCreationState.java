package net.osgiliath.agentscommon.langgraph.state;

import dev.langchain4j.data.message.ChatMessage;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.serializer.AcpLangChain4jStateSerializer;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ProjectCreationState extends WorspaceState<ChatMessage> {

    public static final String PROJECT_LAYOUT_DONE_CHANNEL = "projectLayoutDone";
    public static final String PROJECT_LAYOUT_UPDATE_DATE_CHANNEL = "projectLayoutUpdateDate";

    public static final Map<String, Channel<?>> SCHEMA = getSchema();

    public ProjectCreationState(Map<String, Object> initData) {
        super(initData);
    }

    public static StateSerializer<ProjectCreationState> projectCreationSerializer() {
        return new AcpLangChain4jStateSerializer<>(ProjectCreationState::new);
    }

    private static Map<String, Channel<?>> getSchema() {
        Map<String, Channel<?>> schema = new HashMap<>(WorspaceState.SCHEMA);
        schema.put(PROJECT_LAYOUT_DONE_CHANNEL, Channels.base((Boolean currentValue, Boolean newValue) -> newValue, () -> false));
        schema.put(PROJECT_LAYOUT_UPDATE_DATE_CHANNEL, Channels.base((OffsetDateTime currentValue, OffsetDateTime newValue) -> newValue));
        return schema;
    }

    public Optional<Boolean> projectLayoutDone() {
        return this.value(PROJECT_LAYOUT_DONE_CHANNEL);
    }

    public Optional<OffsetDateTime> projectLayoutUpdateDate() {
        return this.value(PROJECT_LAYOUT_UPDATE_DATE_CHANNEL);
    }
}
