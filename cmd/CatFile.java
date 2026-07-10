import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class CatFile {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length < 2 || !args[0].equals("-p")) {
      throw new OperationException("Usage: cat-file -p <object>");
    }
    System.out.print(repo.catFile(args[1]));
  }
}