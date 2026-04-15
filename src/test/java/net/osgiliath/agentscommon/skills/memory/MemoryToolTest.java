package net.osgiliath.agentscommon.skills.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryToolTest {

    private MemoryTool memoryTool;
    private Path tempMemoryFile;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        memoryTool = new MemoryTool() {
            @Override
            protected Path getMemoryFilePath() {
                return tempDir.resolve("ai/MEMORY.md");
            }
        };
        tempMemoryFile = tempDir.resolve("ai/MEMORY.md");
    }

    @Test
    void testAdd() throws IOException {
        String result = memoryTool.add("Initial memory entry");
        assertThat(result).contains("Memory updated successfully");
        assertThat(Files.readString(tempMemoryFile)).isEqualTo("Initial memory entry");

        memoryTool.add("Second entry");
        assertThat(Files.readString(tempMemoryFile)).isEqualTo("Initial memory entry§Second entry");
    }

    @Test
    void testReplace() throws IOException {
        memoryTool.add("Entry to replace");
        memoryTool.add("Other entry");

        String result = memoryTool.replace("replace", "Replaced entry");
        assertThat(result).contains("Memory updated successfully");
        assertThat(Files.readString(tempMemoryFile)).isEqualTo("Replaced entry§Other entry");
    }

    @Test
    void testRemove() throws IOException {
        memoryTool.add("First entry");
        memoryTool.add("Entry to remove");
        memoryTool.add("Last entry");

        String result = memoryTool.remove("remove");
        assertThat(result).contains("Memory updated successfully");
        assertThat(Files.readString(tempMemoryFile)).isEqualTo("First entry§Last entry");
    }

    @Test
    void testMemoryLimit() {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 2001; i++) {
            largeContent.append("a");
        }

        String result = memoryTool.add(largeContent.toString());
        assertThat(result).contains("Error: Memory limit exceeded");
    }
}
