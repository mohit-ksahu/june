package june;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Index {
  public record Entry(String sha1, String mode, String path) {}

  private static final String FIELD_SEP = "\0";
  private static final String LINE_SEP = "\n";

  private final File indexFile;
  private final TreeMap<String, Entry> entries = new TreeMap<>();

  public Index(File indexFile) throws IOException {
    this.indexFile = indexFile;
    if (indexFile.exists()) {
      for (String line : Files.readAllLines(indexFile.toPath(), StandardCharsets.UTF_8)) {
        if (line.isEmpty()) {
          continue;
        }
        String[] parts = line.split(FIELD_SEP, 3);
        if (parts.length == 3) {
          validatePath(parts[2]);
          entries.put(parts[2], new Entry(parts[0], parts[1], parts[2]));
        }
      }
    }
  }

  public void add(String sha1, String mode, String path) {
    validatePath(path);
    entries.put(path, new Entry(sha1, mode, path));
  }

  private static void validatePath(String path) {
    if (path == null || path.indexOf('\0') != -1 || path.indexOf('\n') != -1
        || path.indexOf('\r') != -1) {
      throw new OperationException("fatal: index paths cannot contain NUL or newline");
    }
  }

  public void write() throws IOException {
    StringBuilder sb = new StringBuilder();
    for (Entry e : entries.values()) {
      sb.append(e.sha1()).append(FIELD_SEP)
        .append(e.mode()).append(FIELD_SEP)
        .append(e.path()).append(LINE_SEP);
    }
    Files.writeString(indexFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
  }
}