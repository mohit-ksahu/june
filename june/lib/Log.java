package june.lib;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import june.Commit;
import june.Repository;

public final class Log {

  public record LogEntry(String sha1, String author, String date, String message, List<String> parents) {}

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy Z");

  private Log() {}

  private static long parseTimestamp(String authorLine) {
    String[] parts = authorLine.split(" ");
    if (parts.length >= 2 && parts[parts.length - 2].matches("\\d+")) {
      return Long.parseLong(parts[parts.length - 2]);
    }
    return 0;
  }

  public static List<LogEntry> log(Repository repo, int maxCount) throws IOException {
    String head = repo.getHeadCommitSha1();
    if (head == null) return List.of();

    Map<String, Commit> commits = new HashMap<>();
    Map<String, Long> timestamps = new HashMap<>();
    Set<String> visited = new HashSet<>();
    Queue<String> queue = new LinkedList<>();
    queue.add(head);
    visited.add(head);

    while (!queue.isEmpty()) {
      String sha = queue.poll();
      Commit commit = repo.readCommit(sha);
      commits.put(sha, commit);
      timestamps.put(sha, parseTimestamp(commit.getAuthor()));
      for (String p : commit.getParentSha1s()) {
        if (visited.add(p)) queue.add(p);
      }
    }

    List<String> sorted = new ArrayList<>(commits.keySet());
    sorted.sort((a, b) -> {
      int cmp = Long.compare(timestamps.get(b), timestamps.get(a));
      return cmp != 0 ? cmp : b.compareTo(a);
    });

    List<LogEntry> entries = new ArrayList<>();
    for (int i = 0; i < Math.min(sorted.size(), maxCount); i++) {
      String sha = sorted.get(i);
      Commit c = commits.get(sha);
      String authorLine = c.getAuthor();
      long epoch = timestamps.get(sha);
      String dateStr = epoch == 0 ? "" :
          ZonedDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault()).format(DATE_FMT);
      String[] parts = authorLine.split(" ");
      String author = (parts.length >= 3 && epoch != 0)
          ? String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 2))
          : authorLine;
      entries.add(new LogEntry(sha, author, dateStr, c.getMessage(), c.getParentSha1s()));
    }
    return entries;
  }
}