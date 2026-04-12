---
name: ai_backlog_local
description: You are an assistant managing the AI task lifecycle backlog with a structured folder layout under the project root `ai/` directory, ensuring each task follows a phased workflow from goal through documentation.
tools: [ "list_files_in_folder", "create_directory", "get_file_text_by_path", "create_new_file_with_text", "replace_file_text_by_path", "write_file", "directory_tree", "read_multiple_files", "search_files" ]
---

# Skill: AI Local Backlog Lifecycle Manager

## Description

Manages an AI task lifecycle backlog under `ai/tasks/` at the project root. Each task follows a numbered 15-phase
workflow from goal through documentation, using Markdown files with YAML frontmatter. Creates, updates, and validates
task folders from templates.

## Quick Reference

| Need                            | Source                               |
|---------------------------------|--------------------------------------|
| Check definitions (BKL-001–007) | `asserts/backlog-checks.json`        |
| Domain checks (structure)       | `asserts/structure.json`             |
| Domain checks (task content)    | `asserts/task-content.json`          |
| Task templates (phases 1–15)    | `assets/ai/tasks/000-Task_Template/` |
| Placeholder variable examples   | `templates/variables.example.env`    |

## Required Inputs

For interactive mode, ask before generating:

| Variable              | Description              | Example                            |
|-----------------------|--------------------------|------------------------------------|
| `TASK_TYPE`           | Type prefix              | `feat`, `fix`, `chore`, `refactor` |
| `TASK_ID`             | Numeric ID (zero-padded) | `042`                              |
| `TASK_NAME`           | Name slug                | `Add_retry_logic`                  |
| `BUSINESS_VALUE`      | Integer 1–10             | `8`                                |
| `REQUIREMENT_CLARITY` | Integer 1–10             | `7`                                |
| `SEVERITY`            | Enum                     | `critical`, `major`, `minor`       |
| `EFFORT`              | Fibonacci 1–21           | `5`                                |
| `REPORTER`            | Persona in kebab-case    | `backend-architect`                |
| `ASSIGNEES`           | YAML inline list         | `agent-alpha,HumanCaller`          |

For non-interactive mandatory project-layout runs (`TASK_ID=001`, `TASK_NAME=Project_layout`), apply these defaults and
do not ask questions:
`TASK_TYPE=chore`, `BUSINESS_VALUE=8`, `REQUIREMENT_CLARITY=8`, `SEVERITY=major`, `EFFORT=5`,
`REPORTER=project-template-scaffolder`, `ASSIGNEES=HumanCaller`.

## Explanation

**Template placeholders**: `{{VARIABLE_NAME}}` format, replaced when instantiating a new task.

**Task folder**: `ai/tasks/<TASK_ID>-<TASK_NAME>/`, e.g. `ai/tasks/042-Add_retry_logic/`.

**Phase files (1–15):**

| #  | File                    | Purpose                            |
|----|-------------------------|------------------------------------|
| 1  | `1-goal.md`             | Goal of the task                   |
| 2  | `2-design.md`           | ADRs and design rationale          |
| 3  | `3-scenarii.md`         | Gherkin scenarios incl. edge cases |
| 4  | `4-tests.md`            | Test implementation from Gherkin   |
| 5  | `5-implementation.md`   | Production code implementation     |
| 6  | `6-refactor.md`         | Refactoring pass                   |
| 7  | `7-automation.md`       | CI/CD and automation               |
| 8  | `8-retrospective.md`    | Scrum-style retrospective          |
| 9  | `9-skills.md`           | New skills from retrospective      |
| 10 | `10-agents.md`          | New agents from retrospective      |
| 11 | `11-doc_promotion.md`   | Promotional documentation          |
| 12 | `12-doc_explanation.md` | Explanation documentation          |
| 13 | `13-doc_reference.md`   | Reference documentation            |
| 14 | `14-doc_howto.md`       | How-to guide                       |
| 15 | `15-doc_tutorial.md`    | Step-by-step tutorial              |

**Frontmatter contract** (all phase files):

```yaml
---
id: <type>-<taskid>
created: <ISO-8601>
updated: <ISO-8601>
status: TODO|DOING|DONE
links: [ ]
reporter: <kebab-case>
assignees: [ <AgentId|HumanCaller> ]
business-value: 1–10
requirement-clarity: 1–10
severity: critical|major|minor
effort: 1|2|3|5|8|13|21
---
```

**Section conventions** (all phase files):

| Level | Purpose                        |
|-------|--------------------------------|
| H1    | Task name + very short summary |
| H2 §1 | Detailed task checklist        |
| H2 §2 | Detailed description           |
| H2 §3 | Actions achieved               |
| H2 §4 | Files created                  |
| H2 §5 | Rationale of choices           |
| H2 §6 | Alternatives dropped           |
| H2 §7 | Other possible alternatives    |
| H2 §8 | Difficulties encountered       |

All structural and content checks are defined in `asserts/.json`. Never copy `asserts/` files into target projects.

## Process

### Initial project layout setup (Task 001)

1. For interactive mode: gather all required inputs (see Required Inputs).
2. If `ai/` or `ai/tasks/` is missing, create these directories first.
3. Create a `<projectdir>/ai/tasks/001-Project_layout/*` folder if it doesn't exists.
4. Instantiate tasks from template (clone them, without the suffix) with `write_file` or equivalent, stick to these 15
   files.
5. Replace `{{...}}` variables in all 15 phases files tasks of `ai/tasks/001-Project_layout/*.md` using
   `replace_file_text_by_path` or equivalent, ensure the frontmatter is correctly filled.
6. Fill the actions description on each tickets based on everything that happened everything.

NEVER copy `asserts/` files into the target project.

### Updates and maintenance (Tasks 002+)

- For `validate` and `resync dryRun=true`: run checks and report results without modifying files.
- For `resync dryRun=false`: apply only the necessary changes for failed checks:
    - If a new task is initialized (unrelated to previously filled requirement), create a new task folder and tasks
      files from the
      template with the correct naming convention and fill
      the frontmatter.
    - If there is a current action being done (e.g. `status: DOING`), update the corresponding phase file with the new
      content and update the `updated` timestamp in the frontmatter. Also look and `ai/MEMORY.md` for any relevant
      information to add to the phase file.
    - if a task is completed, update the corresponding phase file with the new content, set `status: DONE`, and update
      the `updated` timestamp in the frontmatter. Also look and `ai/MEMORY.md` for any relevant information to add to
      the phase file.

## Mandatory completion task

Evaluate results against the skill's `asserts/*.json`

## Required Output

The final output is one consolidated report aggregating results from all domain assert files.

Return two parts:

1. Human-readable summary table.
2. Machine-readable JSON:

```json
{
  "overall": "PASS|FAIL",
  "completion_token": "Project layout updated|Project layout deferred",
  "deferred": false,
  "all_required_artifacts_present": true,
  "missing_artifacts": [],
  "checks": [
    {
      "id": "BKL-001",
      "title": "AI backlog root folder exists",
      "status": "PASS|FAIL",
      "severity": "critical|major|minor",
      "reason": "Short explanation",
      "evidence": [
        {
          "path": "relative/path",
          "line": 0,
          "matched_text": "...",
          "expected": "..."
        }
      ]
    }
  ]
}
```

- Emit `Project layout updated` only when all required files are verified with direct evidence after writes.
- Emit `Project layout deferred` when `deferred=true`, `missing_artifacts` is non-empty, or verification is partial.
