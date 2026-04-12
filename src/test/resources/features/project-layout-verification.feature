@LLMRequired @ToolsRequired
Feature: End-to-End Project Layout update

  Scenario: Project structure is not done in the workspace
    Given the project creation state is initialized
    And a workspace "not-done"
    When the prompt "Hello, check my project" is streamed through the adapter
    Then the agent should answer with "The project structure task is not done. Would you like to setup the project?"

  Scenario: Project structure is old in the workspace
    Given the project creation state is initialized
    And a workspace "done-old"
    And the project layout is 30 days old
    When the prompt "Hello, check my project" is streamed through the adapter
    Then the agent should answer with "The project structure was updated more than 10 days ago. Would you like to update it to the latest conventions?"

  Scenario: Project structure is fresh in the workspace
    Given the project creation state is initialized
    And a workspace "done-fresh"
    And the project layout is fresh
    When the prompt "Hello, check my project" is streamed through the adapter
    Then the agent should answer with "Project audit passed"

  Scenario: Project audit passed using agent validation
    Given the project creation state is initialized
    And a workspace "done-fresh"
    And the project layout is fresh
    When the prompt "project_template_scaffolder/validate" is streamed through the adapter
    Then the agent should answer with "Project audit passed"

  Scenario: Project audit failed using agent validation
    Given the project creation state is initialized
    And a workspace "done-invalid"
    And the project layout is fresh
    When the prompt "project_template_scaffolder/validate" is streamed through the adapter
    Then the agent should answer with "Project audit failed"

  Scenario: Apply new project layout fresh
    Given the project creation state is initialized
    And a workspace "done-fresh"
    And the project layout is 30 days old
    When the prompt "Hello, check my project" is streamed through the adapter
    Then the agent should answer with "Would you like to update it to the latest conventions?"
    When the prompt "Yes, please update the project layout" is streamed through the adapter
    Then the agent should answer with "Project layout updated"
    And the project layout should match the expected layout definition
    And the layout should be effectively in place and active

  Scenario: Apply new project layout on not-done workspace
    Given the project creation state is initialized
    And a workspace "not-done"
    When the prompt "Hello, check my project" is streamed through the adapter
    Then the agent should answer with "The project structure task is not done. Would you like to setup the project?"
    When the prompt "Yes, please update the project layout" is streamed through the adapter
    Then the agent should answer with "Project layout updated"
    And the project layout should match the expected layout definition
    And the layout should be effectively in place and active
