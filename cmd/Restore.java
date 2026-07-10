import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import june.Repository;

public final class Restore {
  public static void run(Repository repo, String[] args) throws IOException {
    boolean staged = false;
    List<String> paths = new ArrayList<>();
    for (String arg : args) {
      if (arg.equals("--staged")) {
        staged = true;
      } else {
        paths.add(arg);
      }
    }
    String result = repo.restore(paths, staged);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}