package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import june.Helper;
import june.IgnoreRules;
import june.Index;
import june.OperationException;
import june.Repository;

public final class Add {
  private Add() {}

  public static void add(Repository repo, List<String> paths) throws IOException {
    Index index = new Index(repo.getIndexFile());
    var ignorePatterns = IgnoreRules.load(repo.getRootDir());
    for (String rawPath : paths) {
      String relPath = repo.getRelativePath(rawPath);
      File target = new File(repo.getRootDir(), relPath);
      boolean exists = target.exists() || Files.isSymbolicLink(target.toPath());
      if (!exists) {
        if (index.getEntry(relPath) != null) {
          index.remove(relPath);
        } else {
          throw new OperationException("fatal: pathspec '" + rawPath + "' did not match any files");
        }
      } else {
        List<File> files = new ArrayList<>();
        if (target.isDirectory()) {
          Helper.collectWorkspaceFiles(target, repo.getRootDir(), files, ignorePatterns);
        } else if (!IgnoreRules.isIgnored(relPath, ignorePatterns)) {
          files.add(target);
        }
        for (File file : files) {
          String rel = repo.getRootDir().toPath().relativize(file.toPath()).toString().replace('\\', '/');
          String mode = Helper.entryMode(file);
          index.add(repo.writeFileOrSymlinkTarget(file, mode), mode, rel);
        }
      }
    }
    index.write();
  }
}
