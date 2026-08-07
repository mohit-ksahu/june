import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class Commit {
  public static void run(Repository repo, String[] args) throws Exception {
    String msg = null;
    boolean auto = false;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-a" -> auto = true;
        case "-m", "-am" -> {
          if (args[i].equals("-am")) {
            auto = true;
          }
          if (i + 1 < args.length) {
            msg = args[++i];
          }
        }
        default -> {}
      }
    }
    if (msg == null) {
      throw new OperationException("no commit message specified (use -m \"message\")");
    }
    String result = repo.commit(msg, auto).message();
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}