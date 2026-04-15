package net.osgiliath.agentscommon.langgraph.node.auditor;

import net.osgiliath.agentscommon.langgraph.node.model.UpdateProposalKind;
import org.springframework.stereotype.Component;

@Component
public class ProposalKindToUserAnswerResolver {

    public static final String PROJECT_STRUCTURE_STALE_UPDATE = "The project structure was updated more than 10 days ago. Would you like to update it to the latest conventions?";
    public static final String PROJECT_STRUCTURE_SETUP_PROPOSAL = "The project structure task is not done. Would you like to setup the project?";

    /**
     * Resolves the update proposal message based on the already-classified proposal kind.
     *
     * @param proposalKind the proposal kind resolved by the audit interpreter.
     * @return a message proposing the appropriate project structure update.
     */
    public String resolveUpdateProposal(UpdateProposalKind proposalKind) {
        return switch (proposalKind) {
            case MISSING -> PROJECT_STRUCTURE_SETUP_PROPOSAL;
            case STALE, UNKNOWN -> PROJECT_STRUCTURE_STALE_UPDATE;
            case NO_UPDATE_NEEDED -> throw new IllegalArgumentException(
                    "A proposal message cannot be resolved when no update is needed");
        };
    }
}
