import june.OperationException;
import june.Repository;

public final class Reset {
  public static void run(Repository repo, String[] args) throws Exception {
    boolean hard = false;
    String target = null;
    for (String arg : args) {
      if (arg.equals("--hard")) {
        hard = true;
      } else {
        target = arg;
      }
    }
    if (!hard) {
      throw new OperationException("reset requires --hard");
    }
    String result = repo.reset(target);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}
