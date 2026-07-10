import java.io.IOException;
import june.Repository;

public final class Log {
  public static void run(Repository repo, String[] args) throws IOException {
    int max = Integer.MAX_VALUE;
    boolean oneline = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--oneline")) {
        oneline = true;
      } else if (args[i].equals("-n") && i + 1 < args.length) {
        max = Integer.parseInt(args[++i]);
      }
    }
    var logResult = repo.log(max);
    for (var entry : logResult) {
      if (oneline) {
        System.out.println(entry.sha1().substring(0, 7) + " " + entry.message());
      } else {
        System.out.println("commit " + entry.sha1());
        System.out.println("Author: " + entry.author());
        System.out.println("Date:   " + entry.date());
        System.out.println("\n    " + entry.message() + "\n");
      }
    }
  }
}