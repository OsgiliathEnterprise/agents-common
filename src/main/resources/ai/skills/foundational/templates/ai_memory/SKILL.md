---
name: ai_memory
description: You are an assistant with a personal memory stored in `<projectroot>/ai/MEMORY.md`. You use this memory to keep track of session status, failed attempts, explicit corrections, and completed tasks.
tools: [ "get_file_text_by_path", "create_new_file_with_text", "replace_file_text_by_path" ]
---

# Skill: AI Memory Manager

## Quick Reference

| Action  | Tool Action                 | Purpose                                                                    |
|---------|-----------------------------|----------------------------------------------------------------------------|
| Read    | `get_file_text_by_path`     | Read the current memory.                                                   |
| Add     | `create_new_file_with_text` | Create the memory file if it doesn't exist, or replace its entire content. |
| Replace | `replace_file_text_by_path` | Replace the entire content of the memory file with updated version.        |

## Memory File Format

- **Location**: `ai/MEMORY.md` (relative to project root)
- **Max Length**: 2000 characters.
- **Delimiter**: Sections are separated by the `§` (section sign) delimiter.

## Content to Remember

1. **Current status**: Graph nodes traversed, statements refined, responses, etc.
2. **Temporary ADRs/Attempts**: Logic or approaches that were tried but failed.
3. **Explicit corrections**: Any corrections or preferences explicitly stated by the user.
4. **Completed tasks**: A record of what has been successfully achieved.

## Usage Guidelines

- **Keep it concise**: Do not exceed 2000 characters in total.
- **Skip trivial tasks**: Do not store easily rediscovered facts or very simple actions.
- **Sectioning**: Use the `§` delimiter between distinct memory entries.
- **Self-Monitoring**: Be aware of the current character count and usage percentage to manage capacity.
- **Persistence**: Always read the memory file first using `get_file_text_by_path` before updating it.
- **Determinism**: In non-interactive runs, do not ask follow-up questions; apply a read-modify-write update to `ai/MEMORY.md` directly.

## Verification Flow

Callers must delegate all `ai/MEMORY.md` existence checks to this skill — **do not inline this logic in agents**.

1. Attempt to read `ai/MEMORY.md` using `get_file_text_by_path`.
2. If the file does not exist, initialize it with `create_new_file_with_text` using an empty structure
   (at minimum a single `§` section header and a "Current status: initialized" entry).
3. For any subsequent update, always re-read the file first, apply the in-memory modification, then write
   the entire updated content back with `replace_file_text_by_path`.
4. Keep updates deterministic: never partially overwrite — always replace the whole file content.

## Tool Actions

### `create_new_file_with_text(path: "ai/MEMORY.md", text: String)`

Initializes the memory file with the provided content.

### `replace_file_text_by_path(path: "ai/MEMORY.md", text: String)`

Updates the memory file by replacing its entire content. Use this to add, replace, or remove entries by first reading
the file, modifying the text in memory, and then writing it back.
