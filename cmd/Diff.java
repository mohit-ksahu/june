import java.io.IOException;
import june.Repository;

public final class Diff {
  public static void run(Repository repo, String[] args) throws IOException {
    boolean staged = false;
    for (String arg : args) {
      if (arg.equals("--cached") || arg.equals("--staged")) {
        staged = true;
      }
    }
    String result = repo.diff(staged);
    if (!result.isEmpty()) {
      System.out.print(result);
    }
  }
}