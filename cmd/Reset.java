import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Reset {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length < 2 || !args[0].equals("--hard")) {
      throw new OperationException("Usage: reset --hard <commit>");
    }
    String res = repo.reset(args[1]);
    if (res != null && !res.isEmpty()) {
      System.out.println(res);
    }
  }
}