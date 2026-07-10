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

  public boolean remove(String path) {
    return entries.remove(path) != null;
  }

  public Entry getEntry(String path) {
    return entries.get(path);
  }

  public List<Entry> getEntries() {
    return new ArrayList<>(entries.values());
  }

  public void clear() {
    entries.clear();
  }

  public void write() throws IOException {
    File lock = new File(indexFile.getParentFile(), "index.lock");
    FileLock.acquireOrBreak(lock);
    try {
      StringBuilder sb = new StringBuilder();
      for (Entry e : entries.values()) {
        sb.append(e.sha1()).append(FIELD_SEP)
          .append(e.mode()).append(FIELD_SEP)
          .append(e.path()).append(LINE_SEP);
      }
      Files.writeString(lock.toPath(), sb.toString(), StandardCharsets.UTF_8);
      Files.move(lock.toPath(), indexFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      lock.delete();
      throw e;
    }
  }
}

final class FileLock {

  private static final long STALE_LOCK_MILLIS =
      Long.getLong("june.lock.staleMillis", 5L * 60 * 1000);

  private FileLock() {}

  static void acquireOrBreak(File lock) throws IOException {
    File parent = lock.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    if (lock.createNewFile()) {
      return;
    }
    if (isStale(lock)) {
      lock.delete();
      if (lock.createNewFile()) {
        return;
      }
    }
    throw new OperationException(
        "Unable to create " + lock.getName() + ": another june process is running.");
  }

  private static boolean isStale(File lock) {
    try {
      java.nio.file.attribute.BasicFileAttributes attrs =
          Files.readAttributes(lock.toPath(), java.nio.file.attribute.BasicFileAttributes.class);
      return System.currentTimeMillis() - attrs.lastModifiedTime().toMillis() > STALE_LOCK_MILLIS;
    } catch (IOException e) {
      return false;
    }
  }
}