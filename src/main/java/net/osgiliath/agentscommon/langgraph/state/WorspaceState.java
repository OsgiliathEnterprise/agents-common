package net.osgiliath.agentscommon.langgraph.state;

import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.net.URI;
import java.util.*;

public class WorspaceState<T> extends AcpState<T> {

    public static final String WORKSPACE_ROOT_CHANNEL = "workspaceRoot";
    /**
     * State schema for the {@link AcpState}. This defines the channels that are used in the state and their types. The schema is a map where the keys are the channel names and the values are the channel definitions. In this case, we have three channels: MESSAGES_STATE, ATTACHMENTS_META, and ATTACHMENTS. The MESSAGES_STATE channel is defined in the parent class and is used to store the chat messages. The ATTACHMENTS_META channel is used to store the metadata of the attachments sent by the user, and the ATTACHMENTS channel is used to store the content of the attachments sent by the user.
     */
    public static final Map<String, Channel<?>> SCHEMA = getSchema();

    private static Map<String, Channel<?>> getSchema() {
        Map<String, Channel<?>> schema = Map.copyOf(AcpState.SCHEMA);
        schema.put(WORKSPACE_ROOT_CHANNEL, Channels.appender(ArrayList::new));
        return schema;
    }

    /**
     * Constructor for the {@link AcpState}. It takes a map of initial data, which is used to initialize the state. The map should contain the initial values for the channels defined in the state schema.
     *
     * @param initData A map of initial data for the state. The keys should correspond to the channel names defined in the state schema, and the values should be the initial values for those channels.
     */
    public WorspaceState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<URI> workspaceRoot() {
        return this.value(WORKSPACE_ROOT_CHANNEL);
    }

}
