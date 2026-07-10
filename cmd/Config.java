import java.io.IOException;
import june.Repository;

public final class Config {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length == 1) {
      String val = repo.getConfig(args[0]);
      if (val != null) {
        System.out.println(val);
      }
    } else if (args.length == 2) {
      repo.setConfig(args[0], args[1]);
    }
  }
}