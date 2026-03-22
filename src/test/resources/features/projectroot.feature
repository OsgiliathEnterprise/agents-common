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
