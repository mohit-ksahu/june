import java.util.List;
import june.Repository;
import june.Repository.FileChange;
import june.Repository.StatusResult;

public final class Status {
  private static final String RESET = "\u001B[0m";
  private static final String GREEN = "\u001B[32m";
  private static final String RED = "\u001B[31m";

  public static void run(Repository repo, String[] args) throws Exception {
    print(repo.status());
  }

  private static void print(StatusResult result) {
    System.out.println(result.branch());
    section("Changes to be committed:",
        "  (use \"june restore --staged <file>...\" to unstage)",
        changes(result.staged()), GREEN);
    section("Changes not staged for commit:",
        "  (use \"june add <file>...\" to update what will be committed)\n"
            + "  (use \"june restore <file>...\" to discard changes in working directory)",
        changes(result.unstaged()), RED);
    section("Untracked files:",
        "  (use \"june add <file>...\" to include in what will be committed)",
        result.untracked().stream().map(path -> "  " + path).toList(), RED);
    if (result.staged().isEmpty() && result.unstaged().isEmpty() && result.untracked().isEmpty()) {
      System.out.println("nothing to commit, working tree clean");
    }
  }

  private static List<String> changes(List<FileChange> list) {
    return list.stream()
        .map(change -> "  " + change.type().name().toLowerCase() + ":   " + change.path())
        .toList();
  }

  private static void section(String title, String hint, List<String> items, String color) {
    if (items.isEmpty()) {
      return;
    }
    System.out.println(title);
    System.out.println(hint);
    System.out.print(color);
    for (String item : items) {
      System.out.println(item);
    }
    System.out.println(RESET);
  }
}
