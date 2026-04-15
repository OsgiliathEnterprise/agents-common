package net.osgiliath.agentscommon.langgraph.node;

import net.osgiliath.agentscommon.langgraph.node.auditor.ProposalKindToUserAnswerResolver;
import net.osgiliath.agentscommon.langgraph.node.model.UpdateProposalKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProposalKindToUserAnswerResolverTest {

    private final ProposalKindToUserAnswerResolver resolver = new ProposalKindToUserAnswerResolver();

    @Test
    void resolveUpdateProposalReturnsSetupMessageForMissingKind() {
        assertThat(resolver.resolveUpdateProposal(UpdateProposalKind.MISSING))
                .isEqualTo(ProposalKindToUserAnswerResolver.PROJECT_STRUCTURE_SETUP_PROPOSAL);
    }

    @Test
    void resolveUpdateProposalReturnsStaleMessageForStaleKind() {
        assertThat(resolver.resolveUpdateProposal(UpdateProposalKind.STALE))
                .isEqualTo(ProposalKindToUserAnswerResolver.PROJECT_STRUCTURE_STALE_UPDATE);
    }

    @Test
    void resolveUpdateProposalReturnsStaleMessageForUnknownKind() {
        assertThat(resolver.resolveUpdateProposal(UpdateProposalKind.UNKNOWN))
                .isEqualTo(ProposalKindToUserAnswerResolver.PROJECT_STRUCTURE_STALE_UPDATE);
    }

    @Test
    void resolveUpdateProposalRejectsNoUpdateNeededKind() {
        assertThatThrownBy(() -> resolver.resolveUpdateProposal(UpdateProposalKind.NO_UPDATE_NEEDED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no update is needed");
    }
}

