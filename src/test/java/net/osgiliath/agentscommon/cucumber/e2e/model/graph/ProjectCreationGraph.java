package net.osgiliath.agentscommon.cucumber.e2e.model.graph;

import dev.langchain4j.data.message.ChatMessage;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.graph.PromptGraph;
import net.osgiliath.acplanggraphlangchainbridge.langgraph.state.AcpState;
import net.osgiliath.agentscommon.cucumber.e2e.model.node.PingAgentNode;
import net.osgiliath.agentscommon.cucumber.e2e.model.node.WorkspaceScannerNode;
import net.osgiliath.agentscommon.langgraph.node.ProjectLayoutApplierNode;
import net.osgiliath.agentscommon.langgraph.node.ProjectRootResolverNode;
import net.osgiliath.agentscommon.langgraph.node.ProjectStructureAuditorNode;
import net.osgiliath.agentscommon.langgraph.state.ProjectCreationState;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Graph for project creation workflow.
 * <pre>
 * START → resolver → scanner → checker ──┬─(pendingLayoutUpdate)──→ applier → END
 *                                         ├─(AiMessage / question)──→ END
 *                                         └─(pass-through)──────────→ pong → END
 * </pre>
 */
@Component
public class ProjectCreationGraph implements PromptGraph<AcpState<ChatMessage>> {

    private final ProjectRootResolverNode projectRootResolverNode;
    private final WorkspaceScannerNode workspaceScannerNode;
    private final ProjectStructureAuditorNode projectStructureCheckerNode;
    private final ProjectLayoutApplierNode projectLayoutApplierNode;
    private final PingAgentNode pingAgentNode;

    public ProjectCreationGraph(
            ProjectRootResolverNode projectRootResolverNode,
            WorkspaceScannerNode workspaceScannerNode,
            ProjectStructureAuditorNode projectStructureMonitorNode,
            ProjectLayoutApplierNode projectLayoutApplierNode,
            PingAgentNode pingAgentNode) {
        this.projectRootResolverNode = projectRootResolverNode;
        this.workspaceScannerNode = workspaceScannerNode;
        this.projectStructureCheckerNode = projectStructureMonitorNode;
        this.projectLayoutApplierNode = projectLayoutApplierNode;
        this.pingAgentNode = pingAgentNode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StateGraph<AcpState<ChatMessage>> buildGraph() throws GraphStateException {
        return (StateGraph<AcpState<ChatMessage>>) (StateGraph<?>) new StateGraph<>(ProjectCreationState.SCHEMA, ProjectCreationState.projectCreationSerializer())
                .addNode("resolver", node_async(state -> projectRootResolverNode.apply(new ProjectCreationState(state.data()))))
                .addNode("scanner", node_async(state -> workspaceScannerNode.apply(new ProjectCreationState(state.data()))))
                .addNode("checker", node_async(state -> projectStructureCheckerNode.apply(new ProjectCreationState(state.data()))))
                .addNode("applier", node_async(state -> projectLayoutApplierNode.apply(new ProjectCreationState(state.data()))))
                .addNode("pong", node_async(pingAgentNode::apply))
                .addEdge(START, "resolver")
                .addEdge("resolver", "scanner")
                .addEdge("scanner", "checker")
                .addConditionalEdges("checker",
                        edge_async(state -> {
                            ProjectCreationState pcs = new ProjectCreationState(state.data());
                            if (pcs.pendingLayoutUpdate().orElse(false)) {
                                return "apply";
                            }
                            if (pcs.layoutUpdateProposal().orElse(false)) {
                                return "exit";
                            }
                            return "next";
                        }),
                        Map.of("apply", "applier", "exit", END, "next", "pong"))
                .addEdge("applier", END)
                .addEdge("pong", END);
    }
}
