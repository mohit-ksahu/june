package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import june.Helper;
import june.Index;
import june.OperationException;
import june.Repository;

public final class Mv {
  private Mv() {}

  public static String mv(Repository repo, String sourcePath, String destinationPath)
      throws Exception {
    Index index = new Index(repo.getIndexFile());
    String relSource = repo.getRelativePath(sourcePath);
    if (index.getEntry(relSource) == null) {
      throw new OperationException("not under version control, source=" + sourcePath);
    }
    File sourceFile = new File(repo.getRootDir(), relSource);
    if (!sourceFile.exists() && !Files.isSymbolicLink(sourceFile.toPath())) {
      throw new OperationException("source file '" + sourcePath + "' does not exist");
    }
    String relDest = repo.getRelativePath(destinationPath);
    File destinationFile = new File(repo.getRootDir(), relDest);
    if (destinationFile.isDirectory()) {
      destinationFile = new File(destinationFile, sourceFile.getName());
      relDest = repo.getRelativePath(destinationFile.getPath());
    }
    if (destinationFile.getParentFile() != null) {
      destinationFile.getParentFile().mkdirs();
    }
    Index.Entry destEntry = index.getEntry(relDest);
    if (destinationFile.exists() || Files.isSymbolicLink(destinationFile.toPath())) {
      if (destEntry == null) {
        throw new OperationException("destination already exists");
      } else {
        String mode = Helper.entryMode(destinationFile);
        String sha = Helper.entrySha1(destinationFile, mode);
        if (!sha.equals(destEntry.sha1())) {
          throw new OperationException("destination '" + relDest
              + "' has uncommitted local changes; commit or restore it first.");
        }
      }
    }
    Files.move(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    index.remove(relSource);
    index.write();
    Add.add(repo, List.of(destinationFile.getPath()));
    return "Renamed " + relSource + " -> " + relDest;
  }
}