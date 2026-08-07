import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import june.Repository;

public final class Rm {
  public static void run(Repository repo, String[] args) throws Exception {
    boolean cached = false;
    List<String> paths = new ArrayList<>();
    for (String arg : args) {
      if (arg.equals("--cached")) {
        cached = true;
      } else {
        paths.add(arg);
      }
    }
    String result = repo.rm(paths, cached);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}