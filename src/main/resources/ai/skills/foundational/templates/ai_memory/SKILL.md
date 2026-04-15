---
name: ai_memory
description: You are an assistant with a personal memory stored in `<projectroot>/ai/MEMORY.md`. You use this memory to keep track of session status, failed attempts, explicit corrections, and completed tasks.
tools: [ "list_files_in_folder", "create_directory", "get_file_text_by_path", "create_new_file_with_text", "replace_file_text_by_path", "write_file", "directory_tree", "read_multiple_files", "search_files" ]
---

# Skill: AI Memory Manager

## Description

Manages a persistent session-scoped memory file at `ai/MEMORY.md` under the project root. Tracks current status, failed
attempts, explicit corrections, and completed tasks across apply passes.

## Quick Reference

| Need                            | Source                       |
|---------------------------------|------------------------------|
| Check definitions (MEM-001–004) | `asserts/memory-checks.json` |
| MEMORY.md initialization file   | `assets/ai/MEMORY.md`        |

## Required Inputs

- Project root path (to resolve `ai/MEMORY.md`).
- Content to record: current status, correction, or completed task entry.

Non-interactive runs must not ask follow-up questions; apply a read-modify-write update directly.

## Explanation

- **Location**: `ai/MEMORY.md` relative to project root.
- **Max length**: 2000 characters total; skip trivial or easily re-discoverable facts.
- **Delimiter**: Sections are separated by `§` (section sign).
- **Chat-memory id**: Use a UUID-backed id per non-interactive apply pass; reset pass-local state before touching the
  file; never reuse a previous pass id.
- **Durable vs. ephemeral**: `ai/MEMORY.md` is the durable store; pass-local chat memory is ephemeral.

**Content categories:**

1. Current status: graph nodes traversed, statements refined, responses.
2. Temporary ADRs/attempts: logic or approaches tried but failed.
3. Explicit corrections: preference is explicitly stated by the user.
4. Completed tasks: a record of successfully achieved work.

## Process

### Creation/Initialization

1. Ensure `<projectdir>/ai/` exists; create it if missing.
2. For non-interactive apply passes, generate a fresh UUID chat-memory id and reset pass-local chat state.
3. Read `<projectdir>/ai/MEMORY.md` via `get_file_text_by_path`.
4. If not found (ENOENT), initialize with `create_new_file_with_text` or equivalent (e.g. `write_file`).
   If operating in a loop, first verify whether the root directory should be created and whether files already exist,
   then act accordingly. Use `assets/ai/MEMORY.md` as the initialization source.
5. For any update: re-read, apply the modification in memory, write the entire updated content back via
   `replace_file_text_by_path`. Never partially overwrite.
6. Keep total content ≤ 2000 characters.
7. After each write, re-read `<projectdir>/ai/MEMORY.md` and confirm non-empty content before reporting success.

Never copy `asserts/` files into target projects.

### Usage

- Wrap concise memory updates around significant events: new graph nodes, refined statements, failed attempts, explicit
  corrections, and completed tasks. Also include choices made among alternatives.
- For failed attempts, record the approach tried and the failure reason.
- For explicit corrections, record the user’s stated preference and any relevant context.

## Mandatory completion task

Evaluate results against `asserts/*.json`

## Required Output

The final output is one consolidated report for all memory checks.

```json
{
  "overall": "PASS|FAIL",
  "completion_token": "Memory initialized|Memory deferred",
  "deferred": false,
  "checks": [
    {
      "id": "MEM-001",
      "title": "Memory parent directory exists",
      "status": "PASS|FAIL",
      "severity": "critical",
      "reason": "Short explanation",
      "evidence": [
        {
          "path": "ai/",
          "matched_text": "...",
          "expected": "directory present"
        }
      ]
    }
  ]
}
```

- Emit `Memory initialized` only after `ai/MEMORY.md` is verified non-empty by direct re-read.
- Emit `Memory deferred` if the file is missing or empty post-write.
