import java.io.File;
import june.Repository;

public class App {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println("Usage: java App <command> [<args>]");
      System.exit(1);
    }
    String cmd = args[0];
    if (cmd.equals("init")) {
      File repoTarget = new File(".");
      Repository repo = new Repository(repoTarget);
      repo.init();
      System.out.println("Initialized empty June repository in .june/");
      return;
    }
    System.out.println("Unknown command: " + cmd);
  }
}
