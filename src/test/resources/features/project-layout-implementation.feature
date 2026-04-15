@LLMRequired @ToolsRequired @SlowTest
Feature: End-to-End Project Layout update

  Scenario: Apply new project layout fresh
    Given the project creation state is initialized
    And a workspace "done-old"
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
