Feature: Remote agent registration and reactive querying

  Background:
    Given ACP server shell command is available
    And a remote agent "ExternalAssistant" is available via ACP

  @sample @registration
  Scenario: Register ACP servers
    Given a local ACP server command is available
    When I register the local ACP server
    Then I can consume the remote agent "ExternalAssistant" in graph nodes

  @sample @streaming
  Scenario: Reactive query results are streamed incrementally
    Given a long-running query is sent to ExternalAssistant
    And the query executor is configured for streaming
    When ExternalAssistant generates results incrementally
    Then tokens are appended to state progressively


