# Agents Common
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=OsgiliathEnterprise_agentscommon&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=OsgiliathEnterprise_agentscommon)
![Maven Central Version](https://img.shields.io/maven-central/v/net.osgiliath.ai/agents-common)

In progress a library containing common agents and skills, valid in any context.


## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| **Java** | 21 | Primary SDK implementation |
| **Kotlin** | 2.2.20 | ACP client interoperability |
| **Spring Boot** | 3.4.2 | Dependency injection and bootstrapping |
| **JetBrains ACP SDK** (`com.agentclientprotocol:acp`) | 0.15.3 | Agent Client Protocol support |
| **LangChain4j** | 1.11.0 | LLM abstraction and integrations |
| **LangGraph4j** | 1.8.3 | Graph orchestration primitives |
| **CommonMark** | 0.27.1 | Markdown parsing/rendering |

## Useful Commands

Run commands from the module root:

```bash
cd /Users/charliemordant/Code/Sources/CodingCrew/agents-common
```

### Build / Test

```bash
./gradlew clean build --stacktrace
./gradlew test --stacktrace
```

### JaCoCo Coverage

```bash
./gradlew test jacocoTestReport --stacktrace
```

### Dependency Verification (Checksums/Metadata)

Refresh metadata after dependency/plugin changes (lenient), then validate strict mode:

```bash
./gradlew --refresh-dependencies --dependency-verification lenient --write-verification-metadata sha256 help
./gradlew --dependency-verification strict build -x test --stacktrace
```

If strict mode fails after updates, regenerate metadata across CI-like classpaths and retry strict mode:

```bash
./gradlew --refresh-dependencies --dependency-verification lenient --write-verification-metadata sha256 \
  build test jacocoTestReport check dependencyCheckAnalyze sonar \
  -Dsonar.qualitygate.wait=false \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.token=dummy \
  -Dsonar.organization=dummy \
  -Dsonar.projectKey=dummy
./gradlew --dependency-verification strict test --stacktrace
```

### SonarQube / SonarCloud Analysis

Set coordinates and token:

```bash
export SONAR_HOST_URL="https://sonarcloud.io"
export SONAR_TOKEN="<token>"
export SONAR_ORGANIZATION="<organization-key>"
export SONAR_PROJECT_KEY="<project-key>"
```

Run analysis and wait for quality gate:

```bash
./gradlew sonar -Dsonar.qualitygate.wait=true --stacktrace
```

### Dependency Vulnerability Scan

```bash
./gradlew dependencyCheckAnalyze --stacktrace
```

### Publish

```bash
./gradlew publishToMavenLocal --stacktrace
./gradlew clean build publishToMavenLocal --stacktrace
```

### Build Release Artifacts

```bash
./gradlew jar sourcesJar javadocJar --stacktrace
```

### Use a Local Bridge Snapshot

Consume a specific local bridge version while iterating across modules:

```bash
./gradlew test -PbridgeVersion=1.0-SNAPSHOT --stacktrace
```

Or with environment variable:

```bash
export BRIDGE_VERSION="1.0-SNAPSHOT"
./gradlew test --stacktrace
```

## CI-like Local Validation

```bash
./gradlew --dependency-verification strict clean build test jacocoTestReport --stacktrace
./gradlew --dependency-verification strict dependencyCheckAnalyze --stacktrace
./gradlew --dependency-verification strict sonar -Dsonar.qualitygate.wait=true --stacktrace
```

## Troubleshooting

- `DependencyVerificationException`: refresh metadata in lenient mode, then rerun strict mode.
- `Task 'dependencyCheckAnalyze' not found`: ensure OWASP dependency-check plugin is applied.
- Sonar project errors: verify `SONAR_ORGANIZATION` and `SONAR_PROJECT_KEY` match your SonarCloud settings.

## Notes

The SDK is designed to be consumed as a library module; use it from your own Spring Boot agent application and wire your graph/prompt logic around the provided parser and ACP client abstractions.
