import java.io.IOException;
import june.Repository;

public final class Init {
  public static void run(Repository repo, String[] args) throws Exception {
    repo.init();
    System.out.println("Initialized empty repository in " + repo.getRepoDir() + "/");
  }
}