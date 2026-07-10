import java.io.IOException;
import june.OperationException;
import june.Repository;

public final class CatFile {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length < 2 || !args[0].equals("-p")) {
      throw new OperationException("usage: june cat-file -p <object>");
    }
    String result = repo.catFile(args[1]);
    if (!result.isEmpty()) {
      System.out.println(result);
    }
  }
}