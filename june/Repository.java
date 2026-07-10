package june;
import java.io.File;
import java.io.IOException;

public class Repository {
  public static final String REPO_DIR = ".june";
  private final File workDir;
  private final File repoDir;
  public Repository(File workDir) {
    this(workDir, readRepoParent());
  }
  public Repository(File workDir, File customRepoParent) {
    if (customRepoParent != null) {
      this.workDir = workDir;
      this.repoDir = new File(customRepoParent, REPO_DIR);
    } else {
      this.workDir = workDir;
      this.repoDir = new File(workDir, REPO_DIR);
    }
  }
  private static File readRepoParent() {
    String directory = System.getProperty("june.dir", System.getenv("JUNE_DIR"));
    return (directory != null && !directory.isBlank()) ? new File(directory).getAbsoluteFile() : null;
  }
  public void init() throws IOException {
    repoDir.mkdirs();
    new File(repoDir, "objects").mkdirs();
    new File(repoDir, "refs").mkdirs();
    new File(repoDir, "refs" + File.separator + "heads").mkdirs();
    new File(repoDir, "refs" + File.separator + "tags").mkdirs();
  }
  public File getRepoDir() {
    return repoDir;
  }
}