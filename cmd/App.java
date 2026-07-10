import june.Repository;
import java.io.File;
import java.util.Arrays;
import june.OperationException;

public class App {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Usage: java App <command> [<args>]");
      System.exit(1);
    }
    String command = args[0];
    try {
      Repository repo = new Repository(new File("."));
      String[] rest = Arrays.copyOfRange(args, 1, args.length);
      if (command.equals("init")) {
        Init.run(repo, rest);
        return;
      }
      if (!repo.getRepoDir().exists()) {
        throw new OperationException("fatal: not a june repository");
      }
      switch (command) {
        case "add" -> Add.run(repo, rest);
        case "rm" -> Rm.run(repo, rest);
        case "commit" -> Commit.run(repo, rest);
        default -> System.out.println("Unknown command: " + command);
      }
    } catch (OperationException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      System.err.println("fatal: " + e.getMessage());
      System.exit(1);
    }
  }
}
