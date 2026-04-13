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

/**
 * State for project creation process, tracking the status of project layout and related user interactions.
 */
public class ProjectCreationState extends WorspaceState<ChatMessage> {

    public static final String PROJECT_LAYOUT_DONE_CHANNEL = "projectLayoutDone";
    public static final String PROJECT_LAYOUT_UPDATE_DATE_CHANNEL = "projectLayoutUpdateDate";
    public static final String PROJECT_LAYOUT_COMMAND_CHANNEL = "projectLayoutCommand";
    /**
     * Set to {@code true} by {@code ProjectStructureMonitorNode} when the user has confirmed
     * an update request, so the graph can route to the applier node in the same turn.
     */
    public static final String PENDING_LAYOUT_UPDATE_CHANNEL = "pendingLayoutUpdate";
    /**
     * Set to {@code true} by {@code ProjectStructureMonitorNode} when it asks the user
     * whether to refresh the project layout.
     */
    public static final String LAYOUT_UPDATE_PROPOSAL_CHANNEL = "layoutUpdateProposal";

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
        schema.put(PROJECT_LAYOUT_COMMAND_CHANNEL, Channels.base((String currentValue, String newValue) -> newValue));
        schema.put(PENDING_LAYOUT_UPDATE_CHANNEL, Channels.base((Boolean currentValue, Boolean newValue) -> newValue, () -> false));
        schema.put(LAYOUT_UPDATE_PROPOSAL_CHANNEL, Channels.base((Boolean currentValue, Boolean newValue) -> newValue, () -> false));
        return schema;
    }

    public Optional<Boolean> projectLayoutDone() {
        return this.value(PROJECT_LAYOUT_DONE_CHANNEL);
    }

    public Optional<OffsetDateTime> projectLayoutUpdateDate() {
        return this.value(PROJECT_LAYOUT_UPDATE_DATE_CHANNEL);
    }

    public Optional<String> projectLayoutCommand() {
        return this.value(PROJECT_LAYOUT_COMMAND_CHANNEL);
    }

    public Optional<Boolean> pendingLayoutUpdate() {
        return this.value(PENDING_LAYOUT_UPDATE_CHANNEL);
    }

    public Optional<Boolean> layoutUpdateProposal() {
        return this.value(LAYOUT_UPDATE_PROPOSAL_CHANNEL);
    }
}
