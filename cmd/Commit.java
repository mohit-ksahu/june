import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Commit {
  public static void run(Repository repo, String[] args) throws IOException {
    String msg = null;
    boolean auto = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-a")) {
        auto = true;
      } else if (args[i].equals("-m") || args[i].equals("-am")) {
        if (args[i].equals("-am")) {
          auto = true;
        }
        if (i + 1 < args.length) {
          msg = args[++i];
        }
      }
    }
    if (msg == null) {
      throw new OperationException("fatal: no commit message specified (use -m)");
    }
    String res = repo.commit(msg, auto).message();
    if (res != null && !res.isEmpty()) {
      System.out.println(res);
    }
  }
}