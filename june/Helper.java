package june;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

public final class Helper {
  private Helper() {}

  public record FileInfo(String sha1, String mode) {}

  public static class TreeNode {
    public final String name;
    public final String mode;
    public final String sha1;
    public final TreeMap<String, TreeNode> children = new TreeMap<>();

    public TreeNode(String name, String mode, String sha1) {
      this.name = name;
      this.mode = mode;
      this.sha1 = sha1;
    }
  }

  public static String writeTree(TreeNode node, Repository repo) throws IOException {
    if (!node.mode.equals(Modes.TREE)) {
      return node.sha1;
    }
    List<Tree.Entry> entries = new ArrayList<>();
    for (TreeNode child : node.children.values()) {
      entries.add(new Tree.Entry(child.mode, child.name, writeTree(child, repo)));
    }
    return repo.write(new Tree(entries));
  }

  public static void collectTreeFiles(
      String sha1, String prefix, Repository repo, Map<String, FileInfo> out)
      throws IOException {
    Tree tree = (Tree) repo.read(sha1);
    for (Tree.Entry e : tree.getEntries()) {
      String path = prefix.isEmpty() ? e.name() : prefix + e.name();
      if (e.mode().equals(Modes.TREE)) {
        collectTreeFiles(e.sha1(), path + "/", repo, out);
      } else {
        out.put(path, new FileInfo(e.sha1(), e.mode()));
      }
    }
  }

  public static void syncWorkingTreeFromCommit(
      Repository repo, Map<String, FileInfo> targetFiles) throws IOException {
    File rootDir = repo.getRootDir();
    Index index = new Index(repo.getIndexFile());

    List<String> wouldLose = new ArrayList<>();
    for (Index.Entry entry : index.getEntries()) {
      if (targetFiles.containsKey(entry.path())) {
        continue;
      }
      File f = new File(rootDir, entry.path());
      if (!f.exists()) {
        continue;
      }
      if (!entrySha1(f, entry.mode()).equals(entry.sha1())) {
        wouldLose.add(entry.path());
      }
    }
    if (!wouldLose.isEmpty()) {
      StringBuilder msg = new StringBuilder();
      msg.append("error: Your local changes to the following files would be lost ")
          .append("by checkout:\n");
      for (String p : wouldLose) {
        msg.append("    ").append(p).append("\n");
      }
      msg.append("Please commit your changes or stash them before you switch branches.\n")
          .append("Aborting");
      throw new OperationException(msg.toString());
    }

    for (Index.Entry entry : index.getEntries()) {
      if (!targetFiles.containsKey(entry.path())) {
        File f = new File(rootDir, entry.path());
        if (f.exists() || Files.isSymbolicLink(f.toPath())) {
          f.delete();
          deleteEmptyParentDirs(f.getParentFile(), rootDir);
        }
      }
    }
    for (var entry : targetFiles.entrySet()) {
      File dest = new File(rootDir, entry.getKey());
      if (dest.exists() || Files.isSymbolicLink(dest.toPath())) {
        dest.delete();
      }
      FileInfo info = entry.getValue();
      if (info.mode().equals(Modes.SYMLINK)) {
        byte[] target = repo.read(info.sha1()).getData();
        if (dest.getParentFile() != null) {
          dest.getParentFile().mkdirs();
        }
        Files.createSymbolicLink(dest.toPath(), Paths.get(new String(target, StandardCharsets.UTF_8)));
      } else {
        repo.readToFile(info.sha1(), dest);
        dest.setExecutable(info.mode().equals(Modes.EXECUTABLE));
      }
    }

    index.clear();
    for (var entry : targetFiles.entrySet()) {
      index.add(entry.getValue().sha1(), entry.getValue().mode(), entry.getKey());
    }
    index.write();
  }

  public static void checkConflicts(
      Repository repo, Map<String, FileInfo> targetFiles, Index index, String operation)
      throws IOException {
    File rootDir = repo.getRootDir();
    List<String> local = new ArrayList<>();
    List<String> untracked = new ArrayList<>();

    for (var targetEntry : targetFiles.entrySet()) {
      String path = targetEntry.getKey();
      FileInfo info = targetEntry.getValue();
      if (index.getEntry(path) == null) {
        File f = new File(rootDir, path);
        if ((f.exists() || Files.isSymbolicLink(f.toPath()))
            && !entrySha1(f, info.mode()).equals(info.sha1())) {
          untracked.add(path);
        }
      }
    }
    for (Index.Entry e : index.getEntries()) {
      File f = new File(rootDir, e.path());
      if (!(f.exists() || Files.isSymbolicLink(f.toPath()))) {
        continue;
      }
      if (entrySha1(f, e.mode()).equals(e.sha1())) {
        continue;
      }
      FileInfo target = targetFiles.get(e.path());
      if (target != null && entrySha1(f, target.mode()).equals(target.sha1())) {
        continue;
      }
      if (target != null && e.sha1().equals(target.sha1()) && e.mode().equals(target.mode())) {
        continue;
      }
      local.add(e.path());
    }

    if (!local.isEmpty()) {
      StringBuilder msg = new StringBuilder();
      msg.append("error: Your local changes to the following files would be overwritten by ")
          .append(operation).append(":\n");
      for (String p : local) {
        msg.append("    ").append(p).append("\n");
      }
      msg.append("Please commit your changes or stash them before you ")
          .append(operation).append(".\nAborting");
      throw new OperationException(msg.toString());
    }
    if (!untracked.isEmpty()) {
      StringBuilder msg = new StringBuilder();
      msg.append("error: The following untracked working tree files would be overwritten by ")
          .append(operation).append(":\n");
      for (String p : untracked) {
        msg.append("    ").append(p).append("\n");
      }
      msg.append("Please move or remove them before you ")
          .append(operation).append(".\nAborting");
      throw new OperationException(msg.toString());
    }
  }

  public static String entryMode(File file) {
    return Modes.FILE;
  }

  public static String entrySha1(File file, String mode) {
    try {
      if (false) {
        return Sha1.objectId(
            ObjectTypes.BLOB,
            Files.readSymbolicLink(file.toPath()).toString().getBytes(StandardCharsets.UTF_8));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return Sha1.hash(file);
  }

  public static void collectWorkspaceFiles(
      File dir, File rootDir, List<File> out, List<String> ignorePatterns) {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.getName().equals(Repository.REPO_DIR)) {
        continue;
      }
      String rel = rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/');
      if (IgnoreRules.isIgnored(rel, ignorePatterns)) {
        continue;
      }
      if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
        collectWorkspaceFiles(file, rootDir, out, ignorePatterns);
      } else {
        out.add(file);
      }
    }
  }

  public static void deleteEmptyParentDirs(File dir, File rootDir) {
    if (dir == null || !dir.exists() || dir.equals(rootDir)) {
      return;
    }
    File[] list = dir.listFiles();
    if (list == null || list.length == 0) {
      dir.delete();
      deleteEmptyParentDirs(dir.getParentFile(), rootDir);
    }
  }

  public static boolean isBinary(byte[] data) {
    for (int i = 0; i < Math.min(data.length, 8000); i++) {
      if (data[i] == 0) {
        return true;
      }
    }
    return false;
  }

  public static String resolveShortSha1(File repoDir, String shortSha1) {
    if (shortSha1.length() == 40) {
      return shortSha1;
    }
    if (shortSha1.length() < 4) {
      throw new OperationException("error: short SHA-1 must be at least 4 characters");
    }
    File subDir = new File(new File(repoDir, ObjectStore.OBJECTS_DIR_NAME), shortSha1.substring(0, 2));
    if (!subDir.isDirectory()) {
      return null;
    }
    String suffix = shortSha1.substring(2);
    File[] matches = subDir.listFiles((d, n) -> n.startsWith(suffix));
    if (matches == null || matches.length == 0) {
      return null;
    }
    if (matches.length > 1) {
      throw new OperationException("error: short SHA-1 " + shortSha1 + " is ambiguous");
    }
    return shortSha1.substring(0, 2) + matches[0].getName();
  }

  public static boolean isAncestor(Repository repo, String ancestorSha, String descendentSha)
      throws IOException {
    if (ancestorSha == null || descendentSha == null) {
      return false;
    }
    if (ancestorSha.equals(descendentSha)) {
      return true;
    }
    Queue<String> queue = new LinkedList<>();
    Set<String> visited = new HashSet<>();

    queue.add(descendentSha);
    visited.add(descendentSha);

    while (!queue.isEmpty()) {
      String sha = queue.poll();
      if (sha.equals(ancestorSha)) {
        return true;
      }
      ObjectData obj = repo.read(sha);
      if (obj instanceof Commit commit) {
        for (String parent : commit.getParentSha1s()) {
          if (!visited.contains(parent)) {
            visited.add(parent);
            queue.add(parent);
          }
        }
      }
    }
    return false;
  }

  public static byte[] compress(byte[] data) throws java.io.IOException {
    java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
    try (java.util.zip.DeflaterOutputStream def = new java.util.zip.DeflaterOutputStream(buf)) {
      def.write(data);
    }
    return buf.toByteArray();
  }
}
