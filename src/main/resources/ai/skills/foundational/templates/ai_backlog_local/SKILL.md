---
name: ai_backlog_local
description: You are an assistant managing the AI task lifecycle backlog with a structured folder layout under the project root `ai/` directory, ensuring each task follows a phased workflow from goal through documentation.
---

# Skill: AI Backlog Local Lifecycle Manager

## Quick Reference

| Need                                | Use                                                              |
|-------------------------------------|------------------------------------------------------------------|
| Check definitions and detection hints | `asserts/backlog-checks.json`                                  |
| Domain checks (structure)           | `asserts/structure.json`                                         |
| Domain checks (task content)        | `asserts/task-content.json`                                      |
| Task template: Goal                 | `templates/ai/tasks/000-Task_Template/1-goal.md.template`        |
| Task template: Design               | `templates/ai/tasks/000-Task_Template/2-design.md.template`      |
| Task template: Scenarii             | `templates/ai/tasks/000-Task_Template/3-scenarii.md.template`    |
| Task template: Tests                | `templates/ai/tasks/000-Task_Template/4-tests.md.template`       |
| Task template: Implementation       | `templates/ai/tasks/000-Task_Template/5-implementation.md.template` |
| Task template: Refactor             | `templates/ai/tasks/000-Task_Template/6-refactor.md.template`    |
| Task template: Automation           | `templates/ai/tasks/000-Task_Template/7-automation.md.template`  |
| Task template: Retrospective        | `templates/ai/tasks/000-Task_Template/8-retrospective.md.template` |
| Task template: Skills               | `templates/ai/tasks/000-Task_Template/9-skills.md.template`      |
| Task template: Agents               | `templates/ai/tasks/000-Task_Template/10-agents.md.template`     |
| Task template: Doc Promotion        | `templates/ai/tasks/000-Task_Template/11-doc_promotion.md.template` |
| Task template: Doc Explanation      | `templates/ai/tasks/000-Task_Template/12-doc_explanation.md.template` |
| Task template: Doc Reference        | `templates/ai/tasks/000-Task_Template/13-doc_reference.md.template` |
| Task template: Doc How-To           | `templates/ai/tasks/000-Task_Template/14-doc_howto.md.template`  |
| Task template: Doc Tutorial         | `templates/ai/tasks/000-Task_Template/15-doc_tutorial.md.template` |
| Placeholder variable examples       | `templates/variables.example.env`                                |

## Template and Asset Semantics

- Templates use `{{VARIABLE_NAME}}` placeholders, substituted when instantiating a new task.
- Each task is a numbered folder under `ai/tasks/` (e.g. `ai/tasks/042-Add_retry_logic/`).
- The `000-Task_Template/` folder is the canonical scaffold; copy and rename it to create a new task.

## Frontmatter Contract

Every task phase file **must** include a YAML frontmatter header:

```yaml
---
id: {{TASK_TYPE}}-{{TASK_ID}}
created: {{CREATED_DATETIME}}
updated: {{UPDATED_DATETIME}}
status: TODO|DOING|DONE
links: []
business-value: {{BUSINESS_VALUE}}
requirement-clarity: {{REQUIREMENT_CLARITY}}
severity: {{SEVERITY}}
effort: {{EFFORT}}
---
```

| Attribute             | Type / Constraint                         |
|-----------------------|-------------------------------------------|
| `id`                  | `<type>-<taskid>`, e.g. `feat-042`        |
| `created`             | ISO-8601 local datetime                   |
| `updated`             | ISO-8601 local datetime                   |
| `status`              | Enum: `TODO`, `DOING`, `DONE`             |
| `links`               | List of related task IDs                  |
| `business-value`      | Integer 1–10                              |
| `requirement-clarity` | Integer 1–10                              |
| `severity`            | Enum: `critical`, `major`, `minor`        |
| `effort`              | Fibonacci: 1, 2, 3, 5, 8, 13, 21         |

## Section Conventions (all phase files)

Keep every section **minimal** — only information useful for AI understanding.

| Level | Purpose                                |
|-------|----------------------------------------|
| H1    | Task name as title + very short summary |
| H2 §1 | Detailed task checklist                |
| H2 §2 | More detailed (still concise) description |
| H2 §3 | Actions achieved                       |
| H2 §4 | Files created                          |
| H2 §5 | Rationale of choices (if any)          |
| H2 §6 | Alternatives dropped and rationale     |
| H2 §7 | Other possible alternatives            |
| H2 §8 | Difficulties encountered               |

## Task Lifecycle Phases

| #  | File                  | Purpose                                              |
|----|-----------------------|------------------------------------------------------|
| 1  | `1-goal.md`           | Describe the goal of the task                        |
| 2  | `2-design.md`         | ADRs and design rationale                            |
| 3  | `3-scenarii.md`       | Gherkin scenarios incl. edge cases                   |
| 4  | `4-tests.md`          | Test implementation from Gherkin; failing at `Then`  |
| 5  | `5-implementation.md` | Production code implementation                       |
| 6  | `6-refactor.md`       | Refactoring pass                                     |
| 7  | `7-automation.md`     | CI/CD and automation additions                       |
| 8  | `8-retrospective.md`  | Scrum-style retrospective                            |
| 9  | `9-skills.md`         | New skills created from retrospective insights       |
| 10 | `10-agents.md`        | New agents created (incl. skill usage) from retro    |
| 11 | `11-doc_promotion.md` | Promotional / announcement documentation             |
| 12 | `12-doc_explanation.md` | Explanation-oriented documentation                 |
| 13 | `13-doc_reference.md` | Reference documentation                              |
| 14 | `14-doc_howto.md`     | How-to guide                                         |
| 15 | `15-doc_tutorial.md`  | Step-by-step tutorial                                |

## Required Inputs Before Rendering

Before generating a new task folder, ask:

- Task type prefix (`TASK_TYPE`), e.g. `feat`, `fix`, `chore`, `refactor`
- Numeric task ID (`TASK_ID`), e.g. `042`
- Task name slug (`TASK_NAME`), e.g. `Add_retry_logic`
- Business value 1–10 (`BUSINESS_VALUE`)
- Requirement clarity 1–10 (`REQUIREMENT_CLARITY`)
- Severity (`SEVERITY`): `critical`, `major`, or `minor`
- Effort Fibonacci 1–21 (`EFFORT`)

## Checks Organization

- Canonical check metadata: `asserts/backlog-checks.json`
- Domain checks:
  - `asserts/structure.json` — folder and file presence
  - `asserts/task-content.json` — frontmatter and section compliance

## Check Matrix

### BKL-001: AI backlog root folder exists

Pass when: `ai/` directory exists at project root.

### BKL-002: Tasks subfolder exists

Pass when: `ai/tasks/` directory exists.

### BKL-003: Task template folder present

Pass when: `ai/tasks/000-Task_Template/` exists and contains all 15 phase files.

### BKL-004: Frontmatter present in all phase files

Pass when: every `.md` file in a task folder starts with a valid YAML frontmatter block containing all required attributes.

### BKL-005: Section structure respected

Pass when: every phase file contains an H1 title and the expected H2 sections.

### BKL-006: Status values valid

Pass when: `status` in every phase file frontmatter is one of `TODO`, `DOING`, `DONE`.

### BKL-007: Effort uses Fibonacci scale

Pass when: `effort` is one of `1, 2, 3, 5, 8, 13, 21`.

## Severity Rules

- `critical`: BKL-001, BKL-003, BKL-004
- `major`: BKL-002, BKL-005
- `minor`: BKL-006, BKL-007

## Output Contract

Return results in two parts:

1. Human-readable summary table.
2. Machine-readable JSON block.

```json
{
  "overall": "PASS|FAIL",
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
          "matched_text": "exact or trimmed excerpt",
          "expected": "what should exist"
        }
      ]
    }
  ]
}
```

## Assessment Behavior

- Prefer exact file evidence over assumptions.
- If a check cannot be verified due to missing files, mark FAIL with explicit missing artifact reason.
- Do not infer PASS from indirect signals when direct required evidence is absent.

## Suggested Remediation Style

For each failed check, provide:

- Minimal patch target (exact file path).
- One concrete fix suggestion.
- A short validation command when applicable.

## Companion Assets and Templates

- Detection hints index: [asserts/backlog-checks.json](asserts/backlog-checks.json)
- Domain checks (structure): [asserts/structure.json](asserts/structure.json)
- Domain checks (task content): [asserts/task-content.json](asserts/task-content.json)
- Goal template: [templates/ai/tasks/000-Task_Template/1-goal.md.template](templates/ai/tasks/000-Task_Template/1-goal.md.template)
- Design template: [templates/ai/tasks/000-Task_Template/2-design.md.template](templates/ai/tasks/000-Task_Template/2-design.md.template)
- Scenarii template: [templates/ai/tasks/000-Task_Template/3-scenarii.md.template](templates/ai/tasks/000-Task_Template/3-scenarii.md.template)
- Tests template: [templates/ai/tasks/000-Task_Template/4-tests.md.template](templates/ai/tasks/000-Task_Template/4-tests.md.template)
- Implementation template: [templates/ai/tasks/000-Task_Template/5-implementation.md.template](templates/ai/tasks/000-Task_Template/5-implementation.md.template)
- Refactor template: [templates/ai/tasks/000-Task_Template/6-refactor.md.template](templates/ai/tasks/000-Task_Template/6-refactor.md.template)
- Automation template: [templates/ai/tasks/000-Task_Template/7-automation.md.template](templates/ai/tasks/000-Task_Template/7-automation.md.template)
- Retrospective template: [templates/ai/tasks/000-Task_Template/8-retrospective.md.template](templates/ai/tasks/000-Task_Template/8-retrospective.md.template)
- Skills template: [templates/ai/tasks/000-Task_Template/9-skills.md.template](templates/ai/tasks/000-Task_Template/9-skills.md.template)
- Agents template: [templates/ai/tasks/000-Task_Template/10-agents.md.template](templates/ai/tasks/000-Task_Template/10-agents.md.template)
- Doc Promotion template: [templates/ai/tasks/000-Task_Template/11-doc_promotion.md.template](templates/ai/tasks/000-Task_Template/11-doc_promotion.md.template)
- Doc Explanation template: [templates/ai/tasks/000-Task_Template/12-doc_explanation.md.template](templates/ai/tasks/000-Task_Template/12-doc_explanation.md.template)
- Doc Reference template: [templates/ai/tasks/000-Task_Template/13-doc_reference.md.template](templates/ai/tasks/000-Task_Template/13-doc_reference.md.template)
- Doc How-To template: [templates/ai/tasks/000-Task_Template/14-doc_howto.md.template](templates/ai/tasks/000-Task_Template/14-doc_howto.md.template)
- Doc Tutorial template: [templates/ai/tasks/000-Task_Template/15-doc_tutorial.md.template](templates/ai/tasks/000-Task_Template/15-doc_tutorial.md.template)
- Placeholder examples: [templates/variables.example.env](templates/variables.example.env)

Use this flow:

1. Ask for required rendering inputs (`TASK_TYPE`, `TASK_ID`, `TASK_NAME`, `BUSINESS_VALUE`, `REQUIREMENT_CLARITY`, `SEVERITY`, `EFFORT`).
2. Copy `000-Task_Template/` to `ai/tasks/{{TASK_ID}}-{{TASK_NAME}}/`.
3. Replace `{{...}}` variables in all phase files.
4. Load `asserts/backlog-checks.json`, then evaluate each domain file and all checks from `BKL-001` to `BKL-007` with file-backed evidence.

