package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import june.Helper;
import june.Index;
import june.Modes;
import june.OperationException;
import june.Repository;

public final class Restore {
  private Restore() {}

  public static String restore(Repository repo, List<String> paths, boolean staged)
      throws IOException {
    if (paths.isEmpty()) {
      throw new OperationException("no paths specified");
    }
    Index index = new Index(repo.getIndexFile());
    StringBuilder result = new StringBuilder();
    if (staged) {
      Map<String, Helper.FileInfo> headFiles = new HashMap<>();
      String headSha = repo.getHeadCommitSha1();
      if (headSha != null) {
        Helper.collectTreeFiles(repo.readCommit(headSha).getTreeSha1(), "", repo, headFiles);
      }
      Set<String> expanded = expandPaths(repo, paths, index, headFiles.keySet());
      for (String path : expanded) {
        Helper.FileInfo headFile = headFiles.get(path);
        if (headFile != null) {
          index.add(headFile.sha1(), headFile.mode(), path);
          appendLine(result, "Unstaged changes for '" + path + "'");
        } else if (index.remove(path)) {
          appendLine(result, "Unstaged new file '" + path + "'");
        } else {
          throw new OperationException("pathspec '" + path + "' did not match any files");
        }
      }
      index.write();
    } else {
      Set<String> expanded = expandPaths(repo, paths, index, Set.of());
      for (String path : expanded) {
        Index.Entry entry = index.getEntry(path);
        if (entry == null) {
          throw new OperationException(
              "error: pathspec '" + path + "' did not match any file(s) known to june");
        }
        File dest = new File(repo.getRootDir(), path);

        if (dest.exists() || Files.isSymbolicLink(dest.toPath())) {
          dest.delete();
        }
        if (entry.mode().equals(Modes.SYMLINK)) {
          byte[] linkBytes = repo.read(entry.sha1()).getData();
          if (dest.getParentFile() != null) {
            dest.getParentFile().mkdirs();
          }
          Files.createSymbolicLink(
              dest.toPath(), Paths.get(new String(linkBytes, StandardCharsets.UTF_8)));
        } else {
          repo.readToFile(entry.sha1(), dest);
          dest.setExecutable(entry.mode().equals(Modes.EXECUTABLE));
        }
        appendLine(result, "Restored working directory file '" + path + "'");
      }
    }
    return result.toString();
  }

  private static Set<String> expandPaths(Repository repo, List<String> paths, Index index, Set<String> extraPaths) {
    Set<String> result = new HashSet<>();
    for (String rawPath : paths) {
      String relPath = repo.getRelativePath(rawPath);
      String prefix = (relPath.equals(".") || relPath.isEmpty()) ? "" : relPath;
      boolean matched = false;
      for (Index.Entry entry : index.getEntries()) {
        if (prefix.isEmpty() || entry.path().equals(prefix) || entry.path().startsWith(prefix + "/")) {
          result.add(entry.path());
          matched = true;
        }
      }
      for (String extra : extraPaths) {
        if (prefix.isEmpty() || extra.equals(prefix) || extra.startsWith(prefix + "/")) {
          result.add(extra);
          matched = true;
        }
      }
      if (!matched && !new File(repo.getRootDir(), relPath).isDirectory()) {
        result.add(relPath);
      }
    }
    return result;
  }

  private static void appendLine(StringBuilder sb, String line) {
    if (sb.length() > 0) {
      sb.append("\n");
    }
    sb.append(line);
  }
}