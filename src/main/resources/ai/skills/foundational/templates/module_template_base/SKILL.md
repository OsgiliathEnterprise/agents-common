---
name: module_template_base
description: You are an assistant ensuring the project layout is respected and setup properly with  Gradle setup, BDD tests, and proper CICD
tools: [ "list_files_in_folder", "create_directory", "get_file_text_by_path", "create_new_file_with_text", "write_file", "directory_tree" ]
---

# Skill: Project Root Fundamentals Assessor

## Description

Assesses and scaffolds engineering fundamentals at the repository root for a Java/Gradle library: build configuration,
test infrastructure (Cucumber/BDD), CI/CD workflows, release capability (JReleaser), and version catalog conventions.
Reports deterministic PASS/FAIL results per check with file evidence.

## Quick Reference

| Need                              | Source                                                                       |
|-----------------------------------|------------------------------------------------------------------------------|
| Check definitions (FND-001–006.4) | `asserts/fundamentals-checks.json`                                           |
| Domain checks (build)             | `asserts/build.json`                                                         |
| Domain checks (tests)             | `asserts/tests.json`                                                         |
| Domain checks (ci)                | `asserts/ci.json`                                                            |
| Domain checks (release)           | `asserts/release.json`                                                       |
| Root build template               | `templates/build.gradle.kts.template`                                        |
| Root settings template            | `templates/settings.gradle.kts.template`                                     |
| Version catalog template          | `templates/gradle/libs.versions.toml.template`                               |
| JReleaser template                | `templates/jreleaser.yml.template`                                           |
| Cucumber runner template          | `templates/src/test/java/cucumber/CucumberTestRunner.java.template`          |
| Cucumber Spring template          | `templates/src/test/java/cucumber/CucumberSpringConfiguration.java.template` |
| Dependabot asset                  | `assets/.github/dependabot.yml`                                              |
| CI workflow asset                 | `assets/.github/workflows/ci.yml`                                            |
| Release workflow asset            | `assets/.github/workflows/release.yml`                                       |
| Release draft workflow asset      | `assets/.github/workflows/release-draft.yml`                                 |
| Cucumber properties asset         | `assets/src/test/resources/cucumber.properties`                              |
| Sample feature asset              | `assets/src/test/resources/features/sample.feature`                          |
| Sample dataset asset              | `assets/src/test/resources/dataset/config.json`                              |
| Placeholder examples              | `templates/variables.example.env`                                            |

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
- **Modes**: `validate` and `dryRun=true` are audit-only; `resync` with `dryRun=false` applies minimal scaffold
  changes for failed checks only.
- **Templates**: Use `{{VARIABLE_NAME}}` placeholder format; project-specific files (e.g. `build.gradle.kts`,
  `jreleaser.yml`).
- **Assets**: Copied as-is with no substitution; static support files (e.g. `cucumber.properties`,
  `.github/workflows/*.yml`).
- **Publishing**: `maven-publish` with Apache 2.0 license and a local `build/staging-deploy` Maven staging repository.

All checks are defined in `asserts/*.json`. Never copy `asserts/` files into target projects.

## Process

1. For interactive mode: gather required inputs (see Required Inputs).
2. For `resync dryRun=false`: skip questions; apply defaults; preserve existing files where possible.
3. Create any missing parent directories (`gradle/`, `.github/`, `.github/workflows/`) before writing files.
4. Generate scaffold files from templates by replacing `{{...}}` variables using `create_new_file_with_text`.
5. Copy static assets as-is using `create_new_file_with_text`.
6. Apply `assets/*` and hydrated `templates/*` on the target project.

Never copy `asserts/` into the target project.

## Mandatory completion task

Validation commands:

```bash
./gradlew clean assemble test jacocoTestReport --stacktrace
./gradlew publishMavenJavaPublicationToStagingRepository
./gradlew jreleaserConfig
```

Evaluate results against `asserts/*.json`

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
