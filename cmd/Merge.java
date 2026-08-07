import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Merge {
  public static void run(Repository repo, String[] args) throws Exception {
    if (args.length == 0) {
      throw new OperationException("merge target required");
    }
    String result = repo.merge(args[0]);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}