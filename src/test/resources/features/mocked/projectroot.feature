Feature: Project Root Resolution


  Scenario: SC1 - cwd is in the same folder as a git repository
    Given a cwd is provided as an ACP session attribute
    When the file is located in the same folder as a git repository
    Then the project root should return that folder

  Scenario: SC2 - cwd is inside a child folder and a parent folder has a git repository
    Given a cwd is provided as an ACP session attribute
    When one of the parent folders contains a git repository
    Then the project root should return the folder containing the repository

  Scenario: SC3 - no git repository in the parent tree
    Given a cwd is provided as an ACP session attribute
    When no parent folder contains a git repository
    Then the workspace URI should be Optional.empty

  Scenario: SC4 - no cwd provided
    Given no cwd is provided as an ACP session attribute
    When the project root agent is invoked
    Then the workspace URI should be Optional.empty

  Scenario: SC5 - cwd does not exist
    Given a cwd is provided as an ACP session attribute
    When the cwd points to a wrong path that does not exist
    Then the agent should log an error message
    And the workspace URI should be Optional.empty

  Scenario: SC6 - file in context, git repository found while traversing up to cwd
    Given a cwd is provided as an ACP session attribute
    And a file under the workspace with a git repository is provided in the context
    When the project root agent is invoked
    Then the project root should return that folder

  Scenario: SC7 - file in context, no git repository found while traversing up to cwd
    Given a fresh workspace without git is provided as cwd
    And a file under that workspace is provided in the context
    When the project root agent is invoked
    Then the project root should return the cwd folder

  Scenario: SC8 - file in context is disjoint from cwd
    Given a cwd is provided as an ACP session attribute
    And a file outside the workspace is provided in the context
    When the project root agent is invoked
    Then the agent should return an error message stating the paths are disjoint
    And the workspace URI should be Optional.empty

