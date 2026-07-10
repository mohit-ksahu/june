package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import june.Index;
import june.OperationException;
import june.Repository;

public final class Rm {
  private Rm() {}

  public static String rm(Repository repo, List<String> paths, boolean cached) throws IOException {
    if (paths.isEmpty()) {
      throw new OperationException("fatal: no files specified");
    }
    Index index = new Index(repo.getIndexFile());
    StringBuilder result = new StringBuilder();
    for (String rawPath : paths) {
      String relPath = repo.getRelativePath(rawPath);
      if (index.getEntry(relPath) == null) {
        throw new OperationException("fatal: pathspec '" + rawPath + "' did not match any files");
      }
      index.remove(relPath);
      if (!cached) {
        File target = new File(repo.getRootDir(), relPath);
        boolean exists = target.exists() || Files.isSymbolicLink(target.toPath());
        if (exists && !target.delete()) {
          throw new OperationException("fatal: could not remove '" + rawPath + "'");
        }
      }
      appendLine(result, "rm '" + relPath + "'");
    }
    index.write();
    return result.toString();
  }

  private static void appendLine(StringBuilder sb, String line) {
    if (sb.length() > 0) {
      sb.append("\n");
    }
    sb.append(line);
  }
}
