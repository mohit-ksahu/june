import java.io.IOException;
import java.util.List;
import june.OperationException;
import june.Repository;

public final class Tag {
  public static void run(Repository repo, String[] args) throws Exception {
    String message = runTagCommand(repo, args);
    if (!message.isEmpty()) {
      System.out.println(message);
    }
  }

  private static String runTagCommand(Repository repo, String[] args) throws Exception {
    if (args.length == 0) {
      List<String> tags = repo.listTags();
      return String.join("\n", tags);
    }
    if (args[0].equals("-d")) {
      if (args.length < 2) {
        throw new OperationException("tag name required");
      }
      String name = args[1];
      String sha = repo.deleteTag(name);
      return "Deleted tag '" + name + "' (was " + sha.substring(0, 7) + ")";
    }
    String name = args[0];
    String target = args.length > 1 ? args[1] : "HEAD";
    june.lib.Tag.create(repo, name, target);
    return "";
  }
}