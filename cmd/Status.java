import java.io.IOException;
import java.util.List;
import june.Repository;

public final class Status {
  public static void run(Repository repo, String[] args) throws IOException {
    var sr = repo.status();
    System.out.println(sr.branch());
    printSection("Changes to be committed:", sr.staged().stream().map(c -> "  " + c.type().name().toLowerCase() + ":   " + c.path()).toList(), "\u001B[32m");
    printSection("Changes not staged for commit:", sr.unstaged().stream().map(c -> "  " + c.type().name().toLowerCase() + ":   " + c.path()).toList(), "\u001B[31m");
    printSection("Untracked files:", sr.untracked().stream().map(s -> "  " + s).toList(), "\u001B[31m");
    if (sr.staged().isEmpty() && sr.unstaged().isEmpty() && sr.untracked().isEmpty()) {
      System.out.println("nothing to commit, working tree clean");
    }
  }

  private static void printSection(String title, List<String> items, String color) {
    if (items.isEmpty()) return;
    System.out.println(title);
    System.out.print(color);
    items.forEach(System.out::println);
    System.out.print("\u001B[0m");
  }
}