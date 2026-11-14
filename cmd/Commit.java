import june.OperationException;
import june.Repository;

public final class Commit {
  public static void run(Repository repo, String[] args) throws Exception {
    String message = null;
    boolean auto = false;
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("-a")) {
        auto = true;
      } else if (arg.equals("-am")) {
        auto = true;
        message = next(args, ++i);
      } else if (arg.equals("-m")) {
        message = next(args, ++i);
      }
    }
    if (message == null) {
      throw new OperationException("no commit message specified (use -m \"message\")");
    }
    String result = repo.commit(message, auto).message();
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }

  private static String next(String[] args, int index) {
    if (index >= args.length) {
      throw new OperationException("no commit message specified (use -m \"message\")");
    }
    return args[index];
  }
}
