import java.io.IOException;
import june.Repository;

public final class Branch {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length == 0) {
      var br = repo.listBranches();
      for (String b : br.branches()) {
        if (b.equals(br.current())) {
          System.out.println("* " + b);
        } else {
          System.out.println("  " + b);
        }
      }
    } else {
      String res = repo.createBranch(args[0]);
      if (res != null && !res.isEmpty()) {
        System.out.println(res);
      }
    }
  }
}