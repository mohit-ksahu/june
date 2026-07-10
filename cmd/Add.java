import java.io.IOException;
import java.util.Arrays;
import june.OperationException;
import june.Repository;

public final class Add {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length == 0) {
      throw new OperationException("nothing specified, nothing added");
    }
    repo.add(Arrays.asList(args));
  }
}