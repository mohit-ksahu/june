import june.OperationException;
import june.Repository;
import june.Repository.BranchResult;

public final class Branch {
  private static final String RESET = "\u001B[0m";
  private static final String GREEN = "\u001B[32m";

  public static void run(Repository repo, String[] args) throws Exception {
    if (args.length == 0) {
      print(repo.branchList());
      return;
    }
    if (args[0].equals("-d") || args[0].equals("-D")) {
      if (args.length < 2) {
        throw new OperationException("branch name required");
      }
      System.out.println(repo.branchDelete(args[1], args[0].equals("-D")));
      return;
    }
    if (args[0].equals("-m")) {
      if (args.length < 2) {
        throw new OperationException("branch name required");
      }
      String oldName = args.length > 2 ? args[1] : null;
      String newName = args.length > 2 ? args[2] : args[1];
      System.out.println(repo.branchRename(oldName, newName));
      return;
    }
    String message = repo.branchCreate(args[0]);
    if (!message.isEmpty()) {
      System.out.println(message);
    }
  }

  private static void print(BranchResult result) {
    for (String name : result.branches()) {
      if (result.current() != null && name.equals(result.current())) {
        System.out.println(GREEN + "* " + name + RESET);
      } else {
        System.out.println("  " + name);
      }
    }
    if (result.current() != null && result.current().startsWith("(HEAD detached")) {
      System.out.println(GREEN + "* " + result.current() + RESET);
    }
  }
}
