import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Config {
  public static void run(Repository repo, String[] args) throws Exception {
    String result = runConfigCommand(repo, args);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }

  private static String runConfigCommand(Repository repo, String[] args) throws Exception {
    if (args.length == 0) {
      throw new OperationException("usage: june config <key> [<value>]");
    }
    if (args.length == 1) {
      String value = repo.getConfig(args[0]);
      return value != null ? value : "";
    }
    repo.setConfig(args[0], args[1]);
    return "";
  }
}