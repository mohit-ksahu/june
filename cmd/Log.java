import java.io.IOException;
import java.util.List;
import june.OperationException;
import june.Repository;
import june.lib.Log.LogEntry;

public final class Log {

  private static final String ANSI_RESET = "\u001B[0m";
  private static final String ANSI_YELLOW = "\u001B[33m";

  public static void run(Repository repo, String[] args) throws Exception {
    boolean oneline = false;
    for (String arg : args) {
      if (arg.equals("--oneline")) {
        oneline = true;
      }
    }
    List<LogEntry> entries = repo.log(parseMaxCount(args));
    if (entries.isEmpty()) {
      System.out.println("No commits yet.");
    } else {
      formatLog(entries, oneline);
    }
  }

  private static int parseMaxCount(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-n") || args[i].equals("--max-count")) {
        if (i + 1 >= args.length) {
          throw new OperationException("option '" + args[i] + "' requires a value");
        }
        String val = args[i + 1];
        if (!val.matches("\\d+")) {
          throw new OperationException("invalid argument for " + args[i] + ": " + val);
        }
        return Integer.parseInt(val);
      }
    }
    return Integer.MAX_VALUE;
  }

  private static void formatLog(List<LogEntry> entries, boolean oneline) {
    for (LogEntry entry : entries) {
      if (oneline) {
        System.out.println(ANSI_YELLOW + entry.sha1().substring(0, 7) + ANSI_RESET
            + " " + entry.message().split("\n")[0]);
      } else {
        System.out.println(ANSI_YELLOW + "commit " + entry.sha1() + ANSI_RESET);
        System.out.println("Author: " + entry.author());
        if (!entry.date().isEmpty()) {
          System.out.println("Date:   " + entry.date());
        }
        System.out.println();
        for (String line : entry.message().split("\n")) {
          System.out.println("    " + line);
        }
        System.out.println();
      }
    }
  }
}