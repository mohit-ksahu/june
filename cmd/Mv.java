import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Mv {
  public static void run(Repository repo, String[] args) throws Exception {
    if (args.length < 2) {
      throw new OperationException("source and destination required");
    }
    String result = repo.mv(args[0], args[1]);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}