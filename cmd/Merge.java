import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Merge {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length == 0) {
      throw new OperationException("fatal: no merge target specified");
    }
    String res = repo.merge(args[0]);
    if (res != null && !res.isEmpty()) {
      System.out.println(res);
    }
  }
}