import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Mv {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length < 2) {
      throw new OperationException("Usage: mv <source> <destination>");
    }
    String res = repo.mv(args[0], args[1]);
    if (res != null && !res.isEmpty()) {
      System.out.println(res);
    }
  }
}