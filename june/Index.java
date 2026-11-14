package june;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.TreeMap;

public class Index {
  public record Entry(String hash, String mode, String path, long size, long mtime) {}

  private static final String FIELD_SEP = "\0";
  private static final String LINE_SEP = "\n";

  private final File indexFile;
  private final TreeMap<String, Entry> entries = new TreeMap<>();

  public Index(File indexFile) throws IOException {
    this.indexFile = indexFile;
    if (!indexFile.exists()) {
      return;
    }
    for (String line : Files.readAllLines(indexFile.toPath(), StandardCharsets.UTF_8)) {
      parseLine(line);
    }
  }

  private void parseLine(String line) {
    int[] p = new int[4];
    int from = 0;
    for (int i = 0; i < 4; i++) {
      int idx = line.indexOf('\0', from);
      if (idx == -1) {
        return;
      }
      p[i] = idx;
      from = idx + 1;
    }
    String path = line.substring(p[3] + 1);
    checkPath(path);
    long size = parseLong(line.substring(p[1] + 1, p[2]));
    long mtime = parseLong(line.substring(p[2] + 1, p[3]));
    entries.put(path, new Entry(line.substring(0, p[0]), line.substring(p[0] + 1, p[1]), path, size, mtime));
  }

  private static long parseLong(String val) {
    try {
      return Long.parseLong(val);
    } catch (NumberFormatException e) {
      return -1L;
    }
  }

  public void add(String hash, String mode, String path) {
    add(hash, mode, path, -1L, -1L);
  }

  public void add(String hash, String mode, String path, long size, long mtime) {
    checkPath(path);
    entries.put(path, new Entry(hash, mode, path, size, mtime));
  }

  private static void checkPath(String path) {
    if (path == null || path.indexOf('\0') != -1 || path.indexOf('\n') != -1
        || path.indexOf('\r') != -1) {
      throw new OperationException("index paths cannot contain NUL or newline");
    }
  }

  public boolean remove(String path) {
    return entries.remove(path) != null;
  }

  public Entry entry(String path) {
    return entries.get(path);
  }

  public Collection<Entry> entries() {
    return entries.values();
  }

  public void clear() {
    entries.clear();
  }

  public void write() throws IOException {
    StringBuilder buf = new StringBuilder();
    for (Entry entry : entries.values()) {
      buf.append(entry.hash()).append(FIELD_SEP)
        .append(entry.mode()).append(FIELD_SEP)
        .append(entry.size()).append(FIELD_SEP)
        .append(entry.mtime()).append(FIELD_SEP)
        .append(entry.path()).append(LINE_SEP);
    }
    FileLock.write(indexFile, buf.toString());
  }
}

final class FileLock {
  private static final long STALE_LOCK_MILLIS =
      Long.getLong("june.lock.staleMillis", 5L * 60 * 1000);

  static void write(File target, String content) throws IOException {
    File lock = new File(target.getParentFile(), target.getName() + ".lock");
    acquire(lock);
    lock.deleteOnExit();
    Files.writeString(lock.toPath(), content, StandardCharsets.UTF_8);
    Files.move(lock.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
  }

  static void acquire(File lock) throws IOException {
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
    long mtime = Worktree.mtime(lock);
    return mtime > 0 && (System.currentTimeMillis() - mtime > STALE_LOCK_MILLIS);
  }
}
