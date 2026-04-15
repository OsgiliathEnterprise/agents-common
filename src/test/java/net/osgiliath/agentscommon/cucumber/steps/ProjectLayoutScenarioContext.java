package net.osgiliath.agentscommon.cucumber.steps;

import io.cucumber.spring.ScenarioScope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@ScenarioScope
public class ProjectLayoutScenarioContext {

    private final List<String> streamedAnswers = new ArrayList<>();
    private String workspacePath;
    private String workspaceDataset;
    private Path tempWorkspaceRoot;

    public List<String> getStreamedAnswers() {
        return streamedAnswers;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }

    public String getWorkspaceDataset() {
        return workspaceDataset;
    }

    public void setWorkspaceDataset(String workspaceDataset) {
        this.workspaceDataset = workspaceDataset;
    }

    public Path getTempWorkspaceRoot() {
        return tempWorkspaceRoot;
    }

    public void setTempWorkspaceRoot(Path tempWorkspaceRoot) {
        this.tempWorkspaceRoot = tempWorkspaceRoot;
    }

    public String fullAnswer() {
        return String.join("", streamedAnswers);
    }

    public void reset() {
        workspacePath = null;
        workspaceDataset = null;
        tempWorkspaceRoot = null;
        streamedAnswers.clear();
    }
}

