package net.osgiliath.agentscommon.langgraph.node.model;

public record LayoutAuditResult(boolean needsUpdate,
                                UpdateProposalKind proposalKind) {
}

