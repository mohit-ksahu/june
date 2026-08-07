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
  public record Entry(String sha1, String mode, String path, long size, long mtime) {
    public Entry(String sha1, String mode, String path) {
      this(sha1, mode, path, -1L, -1L);
    }
  }

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
        String[] parts = line.split(FIELD_SEP, 5);
        if (parts.length == 3) {
          validatePath(parts[2]);
          entries.put(parts[2], new Entry(parts[0], parts[1], parts[2]));
        } else if (parts.length == 5) {
          validatePath(parts[4]);
          long sz = parts[2].matches("-?\\d+") ? Long.parseLong(parts[2]) : -1L;
          long mt = parts[3].matches("-?\\d+") ? Long.parseLong(parts[3]) : -1L;
          entries.put(parts[4], new Entry(parts[0], parts[1], parts[4], sz, mt));
        }
      }
    }
  }

  public void add(String sha1, String mode, String path) {
    add(sha1, mode, path, -1L, -1L);
  }

  public void add(String sha1, String mode, String path, long size, long mtime) {
    validatePath(path);
    entries.put(path, new Entry(sha1, mode, path, size, mtime));
  }

  private static void validatePath(String path) {
    if (path == null || path.indexOf('\0') != -1 || path.indexOf('\n') != -1
        || path.indexOf('\r') != -1) {
      throw new OperationException("index paths cannot contain NUL or newline");
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
    StringBuilder sb = new StringBuilder();
    for (Entry e : entries.values()) {
      sb.append(e.sha1()).append(FIELD_SEP)
        .append(e.mode()).append(FIELD_SEP)
        .append(e.size()).append(FIELD_SEP)
        .append(e.mtime()).append(FIELD_SEP)
        .append(e.path()).append(LINE_SEP);
    }
    Files.writeString(lock.toPath(), sb.toString(), StandardCharsets.UTF_8);
    Files.move(lock.toPath(), indexFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
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
    long mtime = Helper.fileModifiedTime(lock);
    return mtime > 0 && (System.currentTimeMillis() - mtime > STALE_LOCK_MILLIS);
  }
}