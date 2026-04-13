package net.osgiliath.agentscommon.langgraph.node.model;

/**
 * Result of auditing the project layout, indicating whether an update is needed and the kind of update proposed.
 * This record encapsulates the outcome of a project layout audit, providing a clear indication of whether the current layout
 * complies with the expected standards and, if not, what kind of update is proposed to bring it into compliance.
 * The `needsUpdate` field is a boolean that indicates whether the project layout requires an update, while the `proposalKind` field specifies the nature of the proposed update, if any.
 *
 * @param needsUpdate  Indicates whether the project layout needs to be updated.
 * @param proposalKind The kind of update proposed for the project layout, if an update is needed.
 */
public record LayoutAuditResult(boolean needsUpdate,
                                UpdateProposalKind proposalKind) {
}

