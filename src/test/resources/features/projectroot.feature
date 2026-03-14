Feature: Project Root Resolution

  Scenario: SC1 - file is in the same folder as a git repository
	Given a file path is provided as an attribute
	When the file is located in the same folder as a git repository
	Then the project root should return that folder

  Scenario: SC2 - file is inside a child folder and a parent folder has a git repository
	Given a file path is provided as an attribute
	When one of the parent folders contains a git repository
	Then the project root should return the folder containing the repository

  Scenario: SC3 - no git repository in the parent tree
	Given a file path is provided as an attribute
	When no parent folder contains a git repository
	Then the workspace URI should be null or empty

