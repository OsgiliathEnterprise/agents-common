package net.osgiliath.agentscommon.langgraph.node;

import dev.langchain4j.data.message.ChatMessage;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.message.ResourceLinkContent;
import net.osgiliath.agentscommon.langgraph.state.WorspaceState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class ProjectRootResolverNode implements NodeAction<WorspaceState<ChatMessage>> {

    @Override
    public Map<String, Object> apply(WorspaceState<ChatMessage> state) {
        List<ResourceLinkContent> attachmentsMetadata = state.attachmentsMetadata();
        for (ResourceLinkContent attachment : attachmentsMetadata) {
            if (null != attachment.uri()) {
                URI uri = attachment.uri();
                Path path = Path.of(uri);
                File file = new File(path.toUri());
                if (file.exists() && file.isFile()) {

                }
            }
        }
        return Map.of();
    }
}
