---
name: module_template_base
description: You are an assistant ensuring the project layout is respected and set up properly with Gradle, BDD tests, and CI/CD.
tools: [ "list_files_in_folder", "create_directory", "get_file_text_by_path", "create_new_file_with_text", "write_file", "directory_tree", "read_multiple_files", "search_files" ]
---

# Skill: Project Root Fundamentals Assessor

## Description

Assesses and scaffolds engineering fundamentals at the repository root for a Java/Gradle project: build configuration,
test infrastructure (Cucumber/BDD), CI/CD workflows, release capability (JReleaser), and version catalog conventions.
Reports deterministic PASS/FAIL results per check with file evidence.

## Quick Reference

| Need                         | Source                                                                                   |
|------------------------------|------------------------------------------------------------------------------------------|
| Check definitions (MTB-*)    | `asserts/fundamentals-checks.json`                                                       |
| Domain checks (build)        | `asserts/build.json`                                                                     |
| Domain checks (tests)        | `asserts/tests.json`                                                                     |
| Domain checks (ci)           | `asserts/ci.json`                                                                        |
| Domain checks (release)      | `asserts/release.json`                                                                   |
| Root build template          | `templates/build.gradle.kts.template`                                                    |
| Root settings template       | `templates/settings.gradle.kts.template`                                                 |
| Version catalog template     | `templates/gradle/libs.versions.toml.template`                                           |
| JReleaser template           | `templates/jreleaser.yml.template`                                                       |
| Spring boot main Application | `templates/src/main/java/com/example/Application.java.template`                          |
| Cucumber runner template     | `templates/src/test/java/com/example/cucumber/CucumberTestRunner.java.template`          |
| Cucumber Spring template     | `templates/src/test/java/com/example/cucumber/CucumberSpringConfiguration.java.template` |
| Dependabot asset             | `assets/.github/dependabot.yml`                                                          |
| CI workflow asset            | `assets/.github/workflows/ci.yml`                                                        |
| Release workflow asset       | `assets/.github/workflows/release.yml`                                                   |
| Release draft workflow asset | `assets/.github/workflows/release-draft.yml`                                             |
| Cucumber properties asset    | `assets/src/test/resources/cucumber.properties`                                          |
| Sample feature asset         | `assets/src/test/resources/features/sample.feature`                                      |
| Sample dataset asset         | `assets/src/test/resources/dataset/config.json`                                          |
| Placeholder examples         | `templates/variables.example.env`                                                        |

## Required Inputs

For interactive `initialize` mode, ask before generating:

| Variable                             | Description                       |
|--------------------------------------|-----------------------------------|
| `CUCUMBER_PACKAGE`                   | Package for Cucumber classes      |
| `CUCUMBER_GLUE_PACKAGE`              | Glue package for step definitions |
| `SPRING_BOOT_TEST_APPLICATION_CLASS` | Spring Boot application class FQN |

For non-interactive `resync` with `dryRun=false`: do not ask questions; keep existing files unchanged if values
are missing rather than inventing placeholders.

## Explanation

- **Scope**: Root-level conventions only unless a check explicitly allows module fallback.
- **Modes**:
    - `initialize`: interactive scaffold generation.
    - `validate`: no file modifications; run checks and validation commands.
    - `resync` with `dryRun=true`: no file modifications; run checks and validation commands.
    - `resync` with `dryRun=false`: apply minimal scaffold changes for failed checks, then run validation commands.
- **Templates**: Use `{{VARIABLE_NAME}}` placeholder format; project-specific files (e.g. `build.gradle.kts`,
  `jreleaser.yml`).
- **Assets**: Copied as-is with no substitution; static support files (e.g. `cucumber.properties`,
  `.github/workflows/*.yml`).
- **Publishing**: `maven-publish` with Apache 2.0 license and a local `build/staging-deploy` Maven staging repository.

All checks are defined in `asserts/*.json`. Never copy `asserts/` files into target projects.

## Process

### New project setup:

1. For interactive mode: gather required inputs (see Required Inputs).
2. For `initialize` and `resync dryRun=false`: skip questions; preserve existing files where possible. If required
   template values are
   missing, do not invent placeholders; skip generation for those files and report them as missing artifacts.
3. Replicate the overall parent directories by comparing the skills'
   `templates/**/*` and `assets/**/*` trees against the target project's directory tree using `directory_tree` tool or
   equivalent (e.g. `<projectdir>/gradle/`, `<projectdir>/.github/`,
   `<projectdir>/.github/workflows/`, `<projectdir>/src/`, `<projectdir>/src/main/`...) **excluding files**. If the
   folder is part of a package e.g. `com/example`, try to identify or find a good default for it (and update package
   accordingly). If one or more parent directories are missing, create the full path from the nearest existing root to
   the required leaf (still excluding files).
4. Generate scaffold files from templates by replacing `{{...}}` variables using `create_new_file_with_text` or
   equivalent.
5. Copy static assets as-is using `create_new_file_with_text` or equivalent (e.g. `write_file`) if you loop multiple
   times, check if some root directory should be created or if the file exists, and act accordingly.
6. Apply `assets/**/*` and `templates/**/*` on the target project.
7. Hydrates the copied templates with the provided variables and any relevant information.

Never copy `asserts/` into the target project.

### Updates and maintenance:

#### Validate: run checks and validation commands, then report results without modifying files.

- For `validate` and `resync dryRun=true`: run checks and validation commands, then report results without modifying
  files.

#### Resync with dryRun=false: apply only the necessary scaffold changes for failed checks, then run validation commands.

- For `resync dryRun=false`: apply only the necessary scaffold changes for failed checks:
    - If folders are missing, create them, starting for the missing root, up to the leaf.
    - If a required artifact is missing, create it from the corresponding template or asset.
    - If a capability check fails but the artifact is present, do not modify the file; report the failure for manual
      resolution.
    - Do not modify existing files with user modified content, or only skills updates (i.e. libs.versions.toml package
      versions,
      new library); report any check failures with
      file evidence for manual
      resolution.

Never copy `asserts/` into the target project.

## Mandatory completion task

Validation commands:

```bash
./gradlew clean assemble test jacocoTestReport --stacktrace
./gradlew publishMavenJavaPublicationToStagingRepository
./gradlew jreleaserConfig
```

Evaluate results against `asserts/*.json`

If a required validation command cannot be executed or fails, set `deferred=true` and report command output evidence.

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
      "id": "MTB-001",
      "title": "Gradle build can compile Java",
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

- Emit `Project layout updated` only when all required artifacts are present and verified in-run.
- Emit `Project layout deferred` when `deferred=true` or `missing_artifacts` is non-empty.
