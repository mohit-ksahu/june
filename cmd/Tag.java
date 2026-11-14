import java.util.List;
import june.OperationException;
import june.Repository;

public final class Tag {
  public static void run(Repository repo, String[] args) throws Exception {
    if (args.length == 0) {
      List<String> tags = repo.tagList();
      if (!tags.isEmpty()) {
        System.out.println(String.join("\n", tags));
      }
      return;
    }
    if (args[0].equals("-d")) {
      if (args.length < 2) {
        throw new OperationException("tag name required");
      }
      String sha = repo.tagDelete(args[1]);
      System.out.println("Deleted tag '" + args[1] + "' (was " + sha.substring(0, 7) + ")");
      return;
    }
    String target = args.length > 1 ? args[1] : "HEAD";
    repo.tagCreate(args[0], target);
  }
}
