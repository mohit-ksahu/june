import java.io.IOException;
import java.util.Arrays;
import june.Repository;

public final class Restore {
  public static void run(Repository repo, String[] args) throws IOException {
    boolean staged = false;
    int start = 0;
    if (args.length > 0 && args[0].equals("--staged")) {
      staged = true;
      start = 1;
    }
    String res = repo.restore(Arrays.asList(Arrays.copyOfRange(args, start, args.length)), staged);
    if (res != null && !res.isEmpty()) {
      System.out.println(res);
    }
  }
}