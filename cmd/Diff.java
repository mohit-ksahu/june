import java.io.IOException;
import june.Repository;

public final class Diff {
  public static void run(Repository repo, String[] args) throws IOException {
    boolean staged = false;
    if (args.length > 0 && (args[0].equals("--staged") || args[0].equals("--cached"))) {
      staged = true;
    }
    System.out.print(repo.diff(staged));
  }
}