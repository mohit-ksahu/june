import june.OperationException;
import june.Repository;

public final class Config {
  public static void run(Repository repo, String[] args) throws Exception {
    if (args.length == 0) {
      throw new OperationException("usage: june config <key> [<value>]");
    }
    if (args.length == 1) {
      String value = repo.configGet(args[0]);
      if (value != null && !value.isEmpty()) {
        System.out.println(value);
      }
      return;
    }
    repo.configSet(args[0], args[1]);
  }
}
