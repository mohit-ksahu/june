package june;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

public final class Refs {
  public static void clean(File dir, File rootDir) {
    if (dir == null || !dir.exists() || dir.equals(rootDir)) {
      return;
    }
    File[] files = dir.listFiles();
    if (files != null && files.length == 0) {
      dir.delete();
      clean(dir.getParentFile(), rootDir);
    }
  }

  public static String resolve(File repoDir, String ref) {
    if (ref.length() < 4) {
      return null;
    }
    if (ref.length() == 40 && hex(ref)) {
      return ref.toLowerCase();
    }
    File objDir = new File(new File(repoDir, Odb.OBJECTS_DIR_NAME), ref.substring(0, 2));
    if (!objDir.isDirectory()) {
      return null;
    }
    String rest = ref.substring(2);
    String match = null;
    File[] candidates = objDir.listFiles();
    if (candidates == null) {
      return null;
    }
    for (File file : candidates) {
      if (!file.getName().startsWith(rest)) {
        continue;
      }
      if (match != null) {
        throw new OperationException("short hash " + ref + " is ambiguous");
      }
      match = file.getName();
    }
    return match != null ? ref.substring(0, 2) + match : null;
  }

  private static boolean hex(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
        return false;
      }
    }
    return true;
  }

  public static Repository.BranchResult branchList(Repository repo) throws IOException {
    List<String> branches = repo.branchNames();
    String current = repo.currentBranch();
    if (current == null) {
      String headSha = repo.headSha();
      current = headSha != null
          ? "(HEAD detached at " + headSha.substring(0, 7) + ")"
          : "(no branch)";
    }
    return new Repository.BranchResult(branches, current);
  }

  public static String branchCreate(Repository repo, String name) throws IOException {
    if (repo.branchExists(name)) {
      throw new OperationException("A branch named '" + name + "' already exists.");
    }
    String sha = repo.headSha();
    if (sha == null) {
      throw new OperationException("Not a valid object name: '" + name + "'.");
    }
    repo.writeBranch(name, sha);
    return "Branch '" + name + "' created at " + sha.substring(0, 7);
  }

  public static String branchDelete(Repository repo, String name, boolean force) throws IOException {
    if (name.equals(repo.currentBranch())) {
      throw new OperationException("Cannot delete branch '" + name + "' which you are currently on.");
    }
    String sha = repo.readBranch(name);
    if (sha == null) {
      throw new OperationException("branch '" + name + "' not found.");
    }
    if (!force && !Revision.isAncestor(repo, sha, repo.headSha())) {
      throw new OperationException("The branch '" + name + "' is not fully merged.\n"
          + "If you are sure you want to delete it, run 'june branch -D " + name + "'.");
    }
    repo.dropBranch(name);
    return "Deleted branch " + name + " (was " + sha.substring(0, 7) + ").";
  }

  public static String branchRename(Repository repo, String oldName, String newName) throws IOException {
    if (oldName == null) {
      oldName = repo.currentBranch();
      if (oldName == null) {
        throw new OperationException("Cannot rename detached HEAD");
      }
    }
    if (!repo.branchExists(oldName)) {
      throw new OperationException("branch '" + oldName + "' not found.");
    }
    if (repo.branchExists(newName)) {
      throw new OperationException("A branch named '" + newName + "' already exists.");
    }
    String sha = repo.readBranch(oldName);
    repo.writeBranch(newName, sha);
    repo.dropBranch(oldName);
    if (oldName.equals(repo.currentBranch())) {
      repo.setHead(Repository.HEADS_REF_PREFIX + newName);
    }
    return "Renamed branch '" + oldName + "' to '" + newName + "'";
  }

  public static void tagCreate(Repository repo, String name, String targetRef) throws IOException {
    String sha = repo.resolveRef(targetRef != null ? targetRef : "HEAD");
    if (sha == null) {
      throw new OperationException("Failed to resolve '" + targetRef + "' as a valid ref.");
    }
    repo.writeTag(name, sha);
  }

  private static File file(Repository repo) {
    return new File(repo.repoDir(), "config");
  }

  static Properties load(Repository repo) throws IOException {
    Properties properties = new Properties();
    File f = file(repo);
    if (f.isFile()) {
      try (Reader reader = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
        properties.load(reader);
      }
    }
    return properties;
  }

  public static String configGet(Repository repo, String key) throws IOException {
    if (!repo.exists()) {
      return null;
    }
    return load(repo).getProperty(key);
  }

  public static void configSet(Repository repo, String key, String value) throws IOException {
    Properties properties = load(repo);
    properties.setProperty(key, value);
    try (Writer writer = Files.newBufferedWriter(
        file(repo).toPath(), StandardCharsets.UTF_8)) {
      properties.store(writer, "June Configuration");
    }
  }
}
