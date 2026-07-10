import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Checkout {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length == 0) {
      throw new OperationException("fatal: no branch or commit specified");
    }
    String res = repo.checkout(args[0]);
    if (res != null && !res.isEmpty()) {
      System.out.println(res);
    }
  }
}