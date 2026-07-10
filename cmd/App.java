import java.io.File;
import java.util.Arrays;
import june.OperationException;
import june.Repository;

public final class App {
  public static void main(String[] args) {
    if (args.length == 0) {
      printUsage();
      return;
    }
    String command = args[0];
    try {
      Repository repo = new Repository(new File("."));
      String[] rest = Arrays.copyOfRange(args, 1, args.length);
      if (command.equals("init")) {
        Init.run(repo, rest);
        return;
      }
      if (!repo.exists()) {
        throw new OperationException(
            "fatal: not a june repository (or any of the parent directories): "
                + Repository.REPO_DIR);
      }
      switch (command) {
        case "add" -> Add.run(repo, rest);
        case "commit" -> Commit.run(repo, rest);
        case "log" -> Log.run(repo, rest);
        case "status" -> Status.run(repo, rest);
        case "branch" -> Branch.run(repo, rest);
        case "checkout" -> Checkout.run(repo, rest);
        case "restore" -> Restore.run(repo, rest);
        case "diff" -> Diff.run(repo, rest);
        case "rm" -> Rm.run(repo, rest);
        case "mv" -> Mv.run(repo, rest);
        case "tag" -> Tag.run(repo, rest);
        case "merge" -> Merge.run(repo, rest);
        case "config" -> Config.run(repo, rest);
        case "reset" -> Reset.run(repo, rest);
        case "cat-file" -> CatFile.run(repo, rest);
        default -> {
          System.err.println("june: '" + command + "' is not a june command.");
          printUsage();
          System.exit(1);
        }
      }
    } catch (OperationException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      System.err.println("fatal: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void printUsage() {
    System.out.println("""
        usage: june <command> [<args>]

        These are the June commands used in various situations:

        start a working area
           init       Create an empty repository
                      usage: june init

        work on the current change
           add        Add file contents to the staging area
                      usage: june add <file>...
           mv         Move or rename a file
                      usage: june mv <source> <destination>
           restore    Restore working tree files or unstage changes
                      usage: june restore [--staged] <file>...
           rm         Remove files from the working tree and index
                      usage: june rm [--cached] <file>...

        examine the history and state
           cat-file   Provide content of repository objects
                      usage: june cat-file -p <object>
           diff       Show changes between working tree and index
                      usage: june diff [--cached | --staged]
           log        Show commit logs
                      usage: june log [--oneline] [-n <num>]
           status     Show the working tree status
                      usage: june status

        grow, mark and tweak your common history
           branch     List, create, or delete branches
                      usage: june branch [-d | -D] [<branch-name>]
           checkout   Switch branches or restore working tree files
                      usage: june checkout <branch-name> | <commit>
           commit     Record changes to the repository
                      usage: june commit [-a] -m <msg>
                         or: june commit -am <msg>
           merge      Join two histories together (Fast-Forward only)
                      usage: june merge <branch-name>
           reset      Reset current HEAD to the specified state
                      usage: june reset --hard [<commit>]
           tag        Create or list tags
                      usage: june tag [-d] [<tag-name>]

        setup and configuration
           config     Get and set repository options
                      usage: june config <key> [<value>]""");
  }
}