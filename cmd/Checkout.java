import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Checkout {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length == 0) {
      throw new OperationException("fatal: checkout requires a branch name or commit hash");
    }
    String result = repo.checkout(args[0]);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}