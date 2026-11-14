import java.util.List;
import june.Repository;

public final class Diff {
  private static final String RESET = "\u001B[0m";
  private static final String RED = "\u001B[31m";
  private static final String GREEN = "\u001B[32m";
  private static final String CYAN = "\u001B[36m";

  public static void run(Repository repo, String[] args) throws Exception {
    List<String> list = List.of(args);
    boolean staged = list.contains("--cached") || list.contains("--staged");
    String result = repo.diff(staged);
    if (!result.isEmpty()) {
      print(result);
    }
  }

  private static void print(String text) {
    for (String line : text.split("\n")) {
      if (line.startsWith("-")) {
        System.out.println(RED + line + RESET);
      } else if (line.startsWith("+")) {
        System.out.println(GREEN + line + RESET);
      } else if (line.startsWith("@@")) {
        System.out.println(CYAN + line + RESET);
      } else {
        System.out.println(line);
      }
    }
  }
}
