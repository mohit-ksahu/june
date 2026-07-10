import java.io.IOException;
import java.util.Arrays;
import june.Repository;

public final class Rm {
  public static void run(Repository repo, String[] args) throws IOException {
    boolean cached = false;
    int start = 0;
    if (args.length > 0 && args[0].equals("--cached")) {
      cached = true;
      start = 1;
    }
    String res = repo.rm(Arrays.asList(Arrays.copyOfRange(args, start, args.length)), cached);
    if (res != null && !res.isEmpty()) {
      System.out.println(res);
    }
  }
}