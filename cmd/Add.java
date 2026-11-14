import java.util.List;
import june.OperationException;
import june.Repository;

public final class Add {
  public static void run(Repository repo, String[] args) throws Exception {
    if (args.length == 0) {
      throw new OperationException("nothing specified, nothing added");
    }
    repo.add(List.of(args));
  }
}
