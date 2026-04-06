package net.osgiliath.agentscommon.skills.memory;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool for managing AI memory in a project-local file.
 * The memory is stored in <projectroot>/ai/MEMORY.md.
 */
@Component
public class MemoryTool {

    private static final Logger log = LoggerFactory.getLogger(MemoryTool.class);
    private static final String MEMORY_FILE_PATH = "ai/MEMORY.md";
    private static final String DELIMITER = "§";
    private static final int MAX_MEMORY_SIZE = 2000;

    /**
     * Adds a new memory entry.
     *
     * @param content the content to add
     * @return a status message
     */
    @Tool("Adds a new entry to the AI memory. The memory is stored in ai/MEMORY.md and entries are separated by §.")
    public String add(@P("The content of the memory entry to add.") String content) {
        log.debug("Adding memory entry: {}", content);
        try {
            Path path = getMemoryFilePath();
            ensureDirectoryExists(path);

            String currentMemory = readMemory(path);
            String newEntry = (currentMemory.isEmpty() ? "" : DELIMITER) + content;

            if (currentMemory.length() + newEntry.length() > MAX_MEMORY_SIZE) {
                return String.format("Error: Memory limit exceeded. Current size: %d, New entry size: %d, Max size: %d",
                        currentMemory.length(), newEntry.length(), MAX_MEMORY_SIZE);
            }

            Files.writeString(path, newEntry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return getUsageStats(currentMemory.length() + newEntry.length());
        } catch (IOException e) {
            log.error("Failed to add memory entry", e);
            return "Error: Failed to add memory entry: " + e.getMessage();
        }
    }

    /**
     * Replaces an existing memory entry with updated content.
     *
     * @param oldText substring to match for replacement
     * @param newText the new content
     * @return a status message
     */
    @Tool("Replaces an existing memory entry with updated content. It uses substring matching via oldText.")
    public String replace(@P("The substring to match in the existing memory for replacement.") String oldText,
                          @P("The new content to replace the matched entry with.") String newText) {
        log.debug("Replacing memory entry containing '{}' with '{}'", oldText, newText);
        try {
            Path path = getMemoryFilePath();
            if (!Files.exists(path)) {
                return "Error: Memory file does not exist.";
            }

            String currentMemory = Files.readString(path, StandardCharsets.UTF_8);
            List<String> entries = splitMemory(currentMemory);
            boolean replaced = false;

            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).contains(oldText)) {
                    entries.set(i, newText);
                    replaced = true;
                    break;
                }
            }

            if (!replaced) {
                return "Error: No memory entry found containing '" + oldText + "'";
            }

            String updatedMemory = String.join(DELIMITER, entries);
            if (updatedMemory.length() > MAX_MEMORY_SIZE) {
                return String.format("Error: Memory limit exceeded after replacement. New size: %d, Max size: %d",
                        updatedMemory.length(), MAX_MEMORY_SIZE);
            }

            Files.writeString(path, updatedMemory, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return getUsageStats(updatedMemory.length());
        } catch (IOException e) {
            log.error("Failed to replace memory entry", e);
            return "Error: Failed to replace memory entry: " + e.getMessage();
        }
    }

    /**
     * Removes an entry that's no longer relevant.
     *
     * @param oldText substring to match for removal
     * @return a status message
     */
    @Tool("Removes a memory entry that's no longer relevant. It uses substring matching via oldText.")
    public String remove(@P("The substring to match in the existing memory for removal.") String oldText) {
        log.debug("Removing memory entry containing '{}'", oldText);
        try {
            Path path = getMemoryFilePath();
            if (!Files.exists(path)) {
                return "Error: Memory file does not exist.";
            }

            String currentMemory = Files.readString(path, StandardCharsets.UTF_8);
            List<String> entries = splitMemory(currentMemory);
            int originalSize = entries.size();

            entries.removeIf(entry -> entry.contains(oldText));

            if (entries.size() == originalSize) {
                return "Error: No memory entry found containing '" + oldText + "'";
            }

            String updatedMemory = String.join(DELIMITER, entries);
            Files.writeString(path, updatedMemory, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return getUsageStats(updatedMemory.length());
        } catch (IOException e) {
            log.error("Failed to remove memory entry", e);
            return "Error: Failed to remove memory entry: " + e.getMessage();
        }
    }

    protected Path getMemoryFilePath() {
        return Path.of(MEMORY_FILE_PATH);
    }

    private void ensureDirectoryExists(Path filePath) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private String readMemory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return "";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private List<String> splitMemory(String memory) {
        if (memory.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(memory.split(DELIMITER)));
    }

    private String getUsageStats(int currentLength) {
        double percentage = (double) currentLength / MAX_MEMORY_SIZE * 100;
        return String.format("Memory updated successfully. Current usage: %d characters (%.1f%% of %d limit).",
                currentLength, percentage, MAX_MEMORY_SIZE);
    }
}
