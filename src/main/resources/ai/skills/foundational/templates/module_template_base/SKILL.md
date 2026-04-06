---
name: module_template_base
description: You are an assistant ensuring the project layout is respected and setup properly with  Gradle setup, BDD tests, and proper CICD
tools: [ "list_files_in_folder", "get_file_text_by_path", "create_new_file_with_text", "execute_terminal_command" ]
---

# Skill: Project Root Fundamentals Assessor

## Quick Reference

| Need                                  | Use                                                                          |
|---------------------------------------|------------------------------------------------------------------------------|
| Check definitions and detection hints | `asserts/fundamentals-checks.json`                                           |
| Root Gradle scaffold template         | `templates/build.gradle.kts.template`                                        |
| Root JReleaser scaffold template      | `templates/jreleaser.yml.template`                                           |
| Static Dependabot config asset        | `assets/.github/dependabot.yml`                                              |
| Static CI workflow asset              | `assets/.github/workflows/ci.yml`                                            |
| Static release workflow asset         | `assets/.github/workflows/release.yml`                                       |
| Static draft workflow asset           | `assets/.github/workflows/release-draft.yml`                                 |
| Non-templated Cucumber runtime asset  | `assets/cucumber.properties`                                                 |
| Cucumber runner template              | `templates/src/test/java/cucumber/CucumberTestRunner.java.template`          |
| Cucumber Spring template              | `templates/src/test/java/cucumber/CucumberSpringConfiguration.java.template` |
| Sample feature asset                  | `assets/src/test/resources/features/sample.feature`                          |
| Sample dataset asset                  | `assets/src/test/resources/dataset/config.json`                              |
| Placeholder variable examples         | `templates/variables.example.env`                                            |

## Template and Asset Semantics

- Templates are file scaffolds with placeholders to substitute (format: `{{VARIABLE_NAME}}`).
- Assets are copied as-is, with no variable substitution.
- Use templates for project-specific files like `build.gradle.kts` and `jreleaser.yml`.
- Use assets for static support files like `cucumber.properties`, `.github/dependabot.yml`, and
  `.github/workflows/*.yml`.
- Cucumber starter scaffolding is always included in this module template.

## Checks Organization

- Canonical check metadata is defined in `asserts/fundamentals-checks.json`.
- Checks are split by domain under `asserts/`:
    - `asserts/build.json`
    - `asserts/tests.json`
    - `asserts/ci.json`
    - `asserts/release.json`
- Keep check IDs and severities stable across files.

## Required Inputs Before Rendering

Before generating files in interactive bootstrap mode, ask:

- Which package should be used for Cucumber classes (`CUCUMBER_PACKAGE`)?
- Which glue package should be used (`CUCUMBER_GLUE_PACKAGE`)?
- What is the Spring Boot application class FQN (`SPRING_BOOT_TEST_APPLICATION_CLASS`)?

For non-interactive `amend-existing` with `dryRun=false`, do not ask follow-up questions.
If any value is missing, prefer keeping existing project files unchanged over inventing placeholders.

## Purpose

Assess whether a project has the expected engineering fundamentals configured at the repository root for a Java/Gradle
library that can be built, tested, analyzed, and released.

## Scope

- Check only repository-root conventions unless a rule explicitly allows module fallback.
- Report deterministic PASS/FAIL results per check with file evidence.
- `validate` and `dryRun=true` are audit-only.
- For `amend-existing` with `dryRun=false`, apply only minimal deterministic scaffold changes required by failed checks.

## Required Artifacts

The skill should verify the existence and content of these root files:

- `build.gradle` or `build.gradle.kts`
- `settings.gradle` or `settings.gradle.kts`
- `gradle/libs.versions.toml`
- `jreleaser.yml` or `jreleaser.yaml`
- `.github/dependabot.yml`
- `.github/workflows/ci.yml` (or a clearly equivalent CI workflow file in `.github/workflows/`)

## Check Matrix

### FND-001: Gradle build can compile Java

Pass when all are true:

- Root build file exists (`build.gradle` or `build.gradle.kts`). Verification: use `list_files_in_folder` on project
  root.
- Java support is configured via at least one valid signal (found by reading the file with `get_file_text_by_path`):
    - Java plugin usage (`java`, `java-library`, or Spring Boot with Java toolchain/source compatibility).
    - Java toolchain/source/target compatibility explicitly set.
- There is a standard compile path for Java (for example presence of Java plugin tasks or Java source sets implied by
  plugin usage).

Evidence to capture:

- Plugin lines and Java/toolchain/sourceCompatibility lines from root build file.

### FND-002: Release capability using JReleaser

Pass when all are true:

- Root `jreleaser.yml` (or `.yaml`) exists.
- Root build config applies the JReleaser plugin and/or configures `jreleaser { ... }`.
- Release/deploy settings in JReleaser config are present (for example `release`, `deploy.maven`, signing, or Maven
  Central stanza).

Evidence to capture:

- JReleaser plugin/config references in root build.
- Relevant sections from JReleaser config.

### FND-003: Dependabot configured

Pass when all are true:

- `.github/dependabot.yml` exists.
- Includes Gradle ecosystem updates at repository root (`package-ecosystem: gradle`, `directory: "/"`).

Evidence to capture:

- Dependabot ecosystem and directory entries.

### FND-004: GitHub CI configured

Pass when all are true:

- At least one workflow exists in `.github/workflows/`.
- A CI workflow includes build/test execution for Gradle (for example `./gradlew` commands in jobs triggered by `push`
  or `pull_request`).

Evidence to capture:

- Workflow trigger block and Gradle run steps.

### FND-005: Versions centralized in `libs.versions.toml`

Pass when all are true:

- `gradle/libs.versions.toml` exists.
- Root build uses version catalog aliases (`libs.*`) for plugins and dependencies.
- Required version families appear in the catalog:
    - Cucumber (`cucumber`)
    - JaCoCo (`jacoco`)
    - Spring (`spring`, including Spring Boot and actuator dependency aliases)
    - SonarQube (`sonarqube`)

Evidence to capture:

- Relevant `[versions]`, `[plugins]`, and `[libraries]` entries.
- Root build usage snippets proving alias consumption.

### FND-005.1: Cucumber managed in catalog

Pass when all are true:

- `libs.versions.toml` contains a cucumber version key and cucumber library aliases.
- Build file references cucumber dependencies via `libs` aliases (or BOM alias + cucumber modules).

### FND-005.2: JaCoCo managed in catalog

Pass when all are true:

- `libs.versions.toml` contains `jacoco` version key.
- Build config sets JaCoCo tool version from catalog (directly or through centralized convention).

### FND-005.3: Spring managed in catalog (Boot + Actuator)

Pass when all are true:

- `libs.versions.toml` contains Spring Boot-related plugin/library aliases.
- Build applies Spring Boot (and usually dependency-management) via `libs` plugin aliases.
- Build declares actuator dependency through catalog alias.

### FND-005.4: Publishing conventions present

Pass when all are true:

- Build has `maven-publish` configured.
- POM/license defaults include Apache 2.0 (`Apache License, Version 2.0` or `Apache-2.0`).
- A local Maven staging repository is configured (for example `build/staging-deploy`).

Evidence to capture:

- `publishing { ... }` block snippets with license and staging repository config.

### FND-005.5: SonarQube managed in catalog and configured

Pass when all are true:

- `libs.versions.toml` includes `sonarqube` plugin version/alias.
- Build applies SonarQube plugin via catalog alias.
- Build has `sonar { properties { ... } }` (or equivalent `sonarqube`) including key analysis properties.

### FND-006.x: Cucumber starter scaffolding present

Pass when all are true:

- `src/test/java/cucumber/CucumberTestRunner.java` exists.
- `src/test/java/cucumber/CucumberSpringConfiguration.java` exists.
- `src/test/resources/features/sample.feature` exists.
- `src/test/resources/dataset/config.json` exists.

## Severity Rules

- `critical`: FND-001, FND-002, FND-004, FND-005
- `major`: FND-003, FND-005.4, FND-005.5
- `minor`: FND-005.1, FND-005.2, FND-005.3, FND-006.1, FND-006.2, FND-006.3, FND-006.4

## Output Contract

Return results in two parts:

1) Human-readable summary table.
2) Machine-readable JSON block.

JSON schema:

```json
{
  "overall": "PASS|FAIL",
  "checks": [
    {
      "id": "FND-001",
      "title": "Gradle build can compile Java",
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
- If multiple files satisfy a condition, prefer root file evidence first.
- If a check cannot be verified due to missing files, mark FAIL with explicit missing artifact reason.
- Do not infer PASS from indirect signals when direct required evidence is absent.

## Suggested Remediation Style

For each failed check, provide:

- Minimal patch target (exact file path).
- One concrete fix suggestion.
- A short validation command (for example a `./gradlew` task) when applicable.

## Companion Assets and Templates

- Detection hints index: [asserts/fundamentals-checks.json](asserts/fundamentals-checks.json)
- Domain checks (build): [asserts/build.json](asserts/build.json)
- Domain checks (tests): [asserts/tests.json](asserts/tests.json)
- Domain checks (ci): [asserts/ci.json](asserts/ci.json)
- Domain checks (release): [asserts/release.json](asserts/release.json)
- Root build template: [templates/build.gradle.kts.template](templates/build.gradle.kts.template)
- Root settings template: [templates/settings.gradle.kts.template](templates/settings.gradle.kts.template)
- Version catalog template: [templates/gradle/libs.versions.toml.template](templates/gradle/libs.versions.toml.template)
- JReleaser template: [templates/jreleaser.yml.template](templates/jreleaser.yml.template)
- Dependabot asset: [assets/.github/dependabot.yml](assets/.github/dependabot.yml)
- CI workflow asset: [assets/.github/workflows/ci.yml](assets/.github/workflows/ci.yml)
- Release workflow asset: [assets/.github/workflows/release.yml](assets/.github/workflows/release.yml)
- Release draft workflow asset: [assets/.github/workflows/release-draft.yml](assets/.github/workflows/release-draft.yml)
- Static cucumber asset: [assets/cucumber.properties](assets/cucumber.properties)
- Cucumber properties
  asset: [assets/src/test/resources/cucumber.properties](assets/src/test/resources/cucumber.properties)
- Cucumber runner
  template: [templates/src/test/java/cucumber/CucumberTestRunner.java.template](templates/src/test/java/cucumber/CucumberTestRunner.java.template)
- Cucumber Spring
  template: [templates/src/test/java/cucumber/CucumberSpringConfiguration.java.template](templates/src/test/java/cucumber/CucumberSpringConfiguration.java.template)
- Sample feature
  asset: [assets/src/test/resources/features/sample.feature](assets/src/test/resources/features/sample.feature)
- Sample dataset asset: [assets/src/test/resources/dataset/config.json](assets/src/test/resources/dataset/config.json)
- Placeholder examples: [templates/variables.example.env](templates/variables.example.env)

Use this flow:

1. Ask for required rendering inputs, including Cucumber package and Spring test application class values.
2. Generate scaffold files from templates by replacing `{{...}}` variables using `create_new_file_with_text`.
3. Copy static assets as-is using `create_new_file_with_text`.
4. Load `asserts/fundamentals-checks.json` using `get_file_text_by_path`, then evaluate each domain file and all checks
   from `FND-001` to `FND-006.4`
   with file-backed evidence.

Validation command examples (use `execute_terminal_command`):

```bash
./gradlew clean assemble test jacocoTestReport --stacktrace
./gradlew publishMavenJavaPublicationToStagingRepository
./gradlew jreleaserConfig
```




