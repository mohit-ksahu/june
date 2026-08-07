import java.io.IOException;
import june.OperationException;
import june.Repository;
import june.lib.Branch.BranchResult;

public final class Branch {

  private static final String ANSI_RESET = "\u001B[0m";
  private static final String ANSI_GREEN = "\u001B[32m";

  public static void run(Repository repo, String[] args) throws Exception {
    BranchResult br = runBranchCommand(repo, args);
    formatBranch(br);
  }

  private static BranchResult runBranchCommand(Repository repo, String[] args) throws Exception {
    if (args.length == 0) {
      return repo.listBranches();
    }
    if (args[0].equals("-d") || args[0].equals("-D")) {
      if (args.length < 2) {
        throw new OperationException("branch name required");
      }
      String message = repo.deleteBranch(args[1], args[0].equals("-D"));
      return new BranchResult(null, null, message);
    }
    if (args[0].equals("-m") || args[0].equals("-M") || args[0].equals("mv") || args[0].equals("rename")) {
      if (args.length < 2) {
        throw new OperationException("branch name required");
      }
      String oldName = args.length > 2 ? args[1] : null;
      String newName = args.length > 2 ? args[2] : args[1];
      String message = june.lib.Branch.rename(repo, oldName, newName);
      return new BranchResult(null, null, message);
    }
    String message = repo.createBranch(args[0]);
    return new BranchResult(null, null, message);
  }

  private static void formatBranch(BranchResult br) {
    if (br.message() != null) {
      System.out.println(br.message());
      return;
    }
    for (String branchName : br.branches()) {
      if (br.current() != null && branchName.equals(br.current())) {
        System.out.println(ANSI_GREEN + "* " + branchName + ANSI_RESET);
      } else {
        System.out.println("  " + branchName);
      }
    }
    if (br.current() != null && br.current().startsWith("(HEAD detached")) {
      System.out.println(ANSI_GREEN + "* " + br.current() + ANSI_RESET);
    }
  }
}