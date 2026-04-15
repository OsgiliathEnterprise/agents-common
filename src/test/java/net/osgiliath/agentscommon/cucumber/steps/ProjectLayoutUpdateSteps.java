package net.osgiliath.agentscommon.cucumber.steps;

import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectLayoutUpdateSteps {

    private static final List<String> REQUIRED_LAYOUT_FILES = List.of(
            "build.gradle.kts",
            "settings.gradle.kts",
            "jreleaser.yml",
            "gradle/libs.versions.toml",
            ".github/dependabot.yml",
            ".github/workflows/ci.yml",
            "ai/MEMORY.md"
    );
    private static final String DEFERRED_MARKER = "deferred:";

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private ProjectLayoutScenarioContext context;

    @Then("the project layout should match the expected layout definition")
    public void the_project_layout_should_match_the_expected_layout_definition() {
        Path root = Path.of(context.getWorkspacePath());
        String streamed = context.fullAnswer();

        List<String> missingFiles = REQUIRED_LAYOUT_FILES.stream()
                .filter(relativeFile -> !Files.exists(root.resolve(relativeFile)))
                .toList();

        Path taskDir = root.resolve("ai/tasks/001-Project_layout");
        boolean taskDirExists = Files.isDirectory(taskDir);

        assertThat(!streamed.contains(DEFERRED_MARKER) && missingFiles.isEmpty() && taskDirExists)
                .withFailMessage(buildLayoutFailureMessage(root, missingFiles, taskDirExists))
                .isTrue();
    }

    @Then("the layout should be effectively in place and active")
    public void the_layout_should_be_effectively_in_place_and_active() {
        assertThat(context.fullAnswer()).contains("Project layout updated");
    }

    private String buildLayoutFailureMessage(Path root, List<String> missingFiles, boolean taskDirExists) {
        StringBuilder message = new StringBuilder();
        message.append("Project layout was not applied correctly for workspace: ")
                .append(root)
                .append(System.lineSeparator());

        if (!missingFiles.isEmpty()) {
            message.append("Missing required files: ")
                    .append(missingFiles)
                    .append(System.lineSeparator());
        }

        if (!taskDirExists) {
            message.append("Missing required task directory: ")
                    .append(root.resolve("ai/tasks/001-Project_layout"))
                    .append(System.lineSeparator());
        }
        return message.toString();
    }
}
