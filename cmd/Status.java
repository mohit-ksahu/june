import java.io.IOException;
import java.util.List;
import june.Repository;
import june.lib.Status.FileChange;
import june.lib.Status.StatusResult;

public final class Status {

  private static final String ANSI_RESET = "\u001B[0m";
  private static final String ANSI_GREEN = "\u001B[32m";
  private static final String ANSI_RED = "\u001B[31m";

  public static void run(Repository repo, String[] args) throws Exception {
    formatStatus(repo.status());
  }

  private static void formatStatus(StatusResult sr) {
    System.out.println(sr.branch());
    printSection("Changes to be committed:",
        "  (use \"june restore --staged <file>...\" to unstage)",
        formatChanges(sr.staged()), ANSI_GREEN);
    printSection("Changes not staged for commit:",
        "  (use \"june add <file>...\" to update what will be committed)\n"
            + "  (use \"june restore <file>...\" to discard changes in working directory)",
        formatChanges(sr.unstaged()), ANSI_RED);
    printSection("Untracked files:",
        "  (use \"june add <file>...\" to include in what will be committed)",
        sr.untracked().stream().map(s -> "  " + s).toList(), ANSI_RED);
    if (sr.staged().isEmpty() && sr.unstaged().isEmpty() && sr.untracked().isEmpty()) {
      System.out.println("nothing to commit, working tree clean");
    }
  }

  private static List<String> formatChanges(List<FileChange> changes) {
    return changes.stream()
        .map(c -> "  " + c.type().name().toLowerCase() + ":   " + c.path())
        .toList();
  }

  private static void printSection(String title, String hint, List<String> items, String color) {
    if (items.isEmpty()) {
      return;
    }
    System.out.println(title);
    System.out.println(hint);
    System.out.print(color);
    for (String item : items) {
      System.out.println(item);
    }
    System.out.println(ANSI_RESET);
  }
}