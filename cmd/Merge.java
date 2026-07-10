import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Merge {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length == 0) {
      throw new OperationException("fatal: merge target required");
    }
    String result = repo.merge(args[0]);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}