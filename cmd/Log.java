import java.util.List;
import june.OperationException;
import june.Repository;
import june.Repository.LogEntry;
import june.Worktree;

public final class Log {
  private static final String RESET = "\u001B[0m";
  private static final String YELLOW = "\u001B[33m";

  public static void run(Repository repo, String[] args) throws Exception {
    boolean oneline = List.of(args).contains("--oneline");
    List<LogEntry> entries = repo.log(limit(args));
    if (entries.isEmpty()) {
      System.out.println("No commits yet.");
      return;
    }
    for (LogEntry entry : entries) {
      print(entry, oneline);
    }
  }

  private static int limit(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (!args[i].equals("-n") && !args[i].equals("--max-count")) {
        continue;
      }
      if (i + 1 >= args.length) {
        throw new OperationException("option '" + args[i] + "' requires a value");
      }
      String text = args[i + 1];
      if (!digits(text)) {
        throw new OperationException("invalid argument for " + args[i] + ": " + text);
      }
      return Integer.parseInt(text);
    }
    return Integer.MAX_VALUE;
  }

  private static boolean digits(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return !s.isEmpty();
  }

  private static void print(LogEntry entry, boolean oneline) {
    if (oneline) {
      System.out.println(YELLOW + entry.hash().substring(0, 7) + RESET
          + " " + Worktree.title(entry.message()));
      return;
    }
    System.out.println(YELLOW + "commit " + entry.hash() + RESET);
    if (!entry.parents().isEmpty()) {
      List<String> list = entry.parents().stream().map(p -> p.substring(0, 7)).toList();
      System.out.println("Merge: " + String.join(" ", list));
    }
    System.out.println("Author: " + entry.author());
    if (!entry.date().isEmpty()) {
      System.out.println("Date:   " + entry.date());
    }
    System.out.println();
    for (String line : entry.message().split("\n")) {
      System.out.println("    " + line);
    }
  }
}
