import java.io.IOException;
import june.Repository;

public final class Reset {
  public static void run(Repository repo, String[] args) throws Exception {
    String target = null;
    for (String arg : args) {
      if (!arg.equals("--hard")) {
        target = arg;
      }
    }
    String result = repo.reset(target);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}