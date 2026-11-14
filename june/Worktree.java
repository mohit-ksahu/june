package june;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Worktree {
  public record FileInfo(String hash, String mode) {}

  public static boolean exists(File file) {
    return file != null && (file.exists() || Files.isSymbolicLink(file.toPath()));
  }

  public static long mtime(File file) {
    return (file != null && file.exists()) ? file.lastModified() : -1;
  }

  public static void sync(
      Repository repo, Map<String, FileInfo> targetFiles) throws Exception {
    File rootDir = repo.rootDir();
    Index index = new Index(repo.indexFile());

    safety(rootDir, index, targetFiles);
    stale(rootDir, index, targetFiles);
    write(repo, rootDir, targetFiles);
    rebuild(rootDir, index, targetFiles);
  }

  private static void safety(
      File rootDir, Index index, Map<String, FileInfo> targetFiles) throws Exception {
    List<String> wouldLose = new ArrayList<>();
    for (Index.Entry entry : index.entries()) {
      if (targetFiles.containsKey(entry.path())) {
        continue;
      }
      File file = new File(rootDir, entry.path());
      if (file.exists() && !hash(file, entry.mode()).equals(entry.hash())) {
        wouldLose.add(entry.path());
      }
    }
    if (!wouldLose.isEmpty()) {
      fail(
          "Your local changes to the following files would be lost by checkout:\n",
          wouldLose,
          "Please commit your changes or stash them before you switch branches.");
    }
  }

  private static void stale(
      File rootDir, Index index, Map<String, FileInfo> targetFiles) {
    for (Index.Entry entry : index.entries()) {
      if (targetFiles.containsKey(entry.path())) {
        continue;
      }
      File file = new File(rootDir, entry.path());
      if (exists(file)) {
        file.delete();
        Refs.clean(file.getParentFile(), rootDir);
      }
    }
  }

  private static void write(
      Repository repo, File rootDir, Map<String, FileInfo> targetFiles) throws IOException {
    for (var targetEntry : targetFiles.entrySet()) {
      write(repo, new File(rootDir, targetEntry.getKey()),
          targetEntry.getValue().hash(), targetEntry.getValue().mode());
    }
  }

  private static void write(
      Repository repo, File dest, String hash, String mode) throws IOException {
    if (exists(dest)) {
      dest.delete();
    }
    if (mode.equals(Mode.SYMLINK)) {
      byte[] linkBytes = repo.read(hash).data();
      if (dest.getParentFile() != null) {
        dest.getParentFile().mkdirs();
      }
      Files.createSymbolicLink(
          dest.toPath(), Paths.get(new String(linkBytes, StandardCharsets.UTF_8)));
    } else {
      repo.write(hash, dest);
      dest.setExecutable(mode.equals(Mode.EXECUTABLE));
    }
  }

  private static void rebuild(
      File rootDir, Index index, Map<String, FileInfo> targetFiles) throws IOException {
    index.clear();
    for (var targetEntry : targetFiles.entrySet()) {
      File file = new File(rootDir, targetEntry.getKey());
      FileInfo info = targetEntry.getValue();
      index.add(info.hash(), info.mode(), targetEntry.getKey(), file.length(), mtime(file));
    }
    index.write();
  }

  public static void conflicts(
      Repository repo, Map<String, FileInfo> targetFiles, Index index, String action)
      throws Exception {
    File rootDir = repo.rootDir();
    List<String> untrackedConflicts = new ArrayList<>();
    List<String> modifiedConflicts = new ArrayList<>();

    for (var entry : targetFiles.entrySet()) {
      conflict(rootDir, index, entry.getKey(), entry.getValue(),
          untrackedConflicts, modifiedConflicts);
    }
    report(action, untrackedConflicts, modifiedConflicts);
  }

  private static void conflict(
      File rootDir, Index index, String path, FileInfo info,
      List<String> untrackedConflicts, List<String> modifiedConflicts) throws Exception {
    File file = new File(rootDir, path);
    boolean exists = exists(file);
    if (!exists) {
      return;
    }
    Index.Entry idxEntry = index.entry(path);
    if (idxEntry == null) {
      untrackedConflicts.add(path);
      return;
    }
    String diskSha = hash(file, idxEntry.mode());
    if (!diskSha.equals(idxEntry.hash()) && !diskSha.equals(info.hash())) {
      modifiedConflicts.add(path);
    }
  }

  private static void report(
      String action, List<String> untrackedConflicts, List<String> modifiedConflicts) {
    if (!untrackedConflicts.isEmpty()) {
      fail(
          "The following untracked working tree files would be overwritten by " + action + ":\n",
          untrackedConflicts,
          "Please move or remove them before you " + action + ".");
    }
    if (!modifiedConflicts.isEmpty()) {
      fail(
          "Your local changes to the following files would be overwritten by " + action + ":\n",
          modifiedConflicts,
          "Please commit your changes or stash them before you " + action + ".");
    }
  }

  private static void fail(String header, List<String> paths, String footer) {
    Collections.sort(paths);
    StringBuilder buf = new StringBuilder(header);
    for (String path : paths) {
      buf.append("\t").append(path).append("\n");
    }
    buf.append(footer);
    throw new OperationException(buf.toString());
  }

  public static String mode(File file) throws IOException {
    if (Files.isSymbolicLink(file.toPath())) {
      return Mode.SYMLINK;
    }
    return file.canExecute() ? Mode.EXECUTABLE : Mode.FILE;
  }

  public static String hash(File file, String mode) throws Exception {
    if (mode.equals(Mode.SYMLINK)) {
      String target = Files.readSymbolicLink(file.toPath()).toString();
      return Hash.hash(new ObjectData(ObjectData.BLOB, target.getBytes(StandardCharsets.UTF_8)).serialize());
    }
    return Hash.hash(file);
  }

  public static void collect(
      File dir, File rootDir, Map<String, File> out, List<Ignore.Rule> rules) {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.getName().equals(Repository.REPO_DIR)) {
        continue;
      }
      String rel = rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/');
      if (Ignore.isIgnored(rel, rules)) {
        continue;
      }
      if (file.isDirectory()) {
        collect(file, rootDir, out, rules);
      } else {
        out.put(rel, file);
      }
    }
  }

  public static void process(
      Repository repo, Index index, List<Ignore.Rule> rules, String rawPath)
      throws Exception {
    String relPath = repo.relPath(rawPath);
    File target = new File(repo.rootDir(), relPath);
    if (!exists(target)) {
      if (index.entry(relPath) == null) {
        throw new OperationException("pathspec '" + rawPath + "' did not match any files");
      }
      index.remove(relPath);
      return;
    }
    if (target.isDirectory()) {
      Map<String, File> files = new HashMap<>();
      collect(target, repo.rootDir(), files, rules);
      for (var entry : files.entrySet()) {
        add(repo, index, entry.getValue(), entry.getKey());
      }
      return;
    }
    add(repo, index, target, relPath);
  }

  private static void add(
      Repository repo, Index index, File file, String relPath) throws Exception {
    String mode = mode(file);
    String sha = repo.writeTarget(file, mode);
    index.add(sha, mode, relPath, file.length(), mtime(file));
  }

  public static void stage(Repository repo, Index index) throws Exception {
    for (Index.Entry entry : new ArrayList<>(index.entries())) {
      File target = new File(repo.rootDir(), entry.path());
      boolean exists = exists(target);
      if (!exists) {
        index.remove(entry.path());
        continue;
      }
      String mode = mode(target);
      String sha = hash(target, mode);
      if (!sha.equals(entry.hash()) || !mode.equals(entry.mode())) {
        String newSha = repo.writeTarget(target, mode);
        index.add(newSha, mode, entry.path(), target.length(), mtime(target));
      }
    }
    index.write();
  }

  public static String checkout(Repository repo, String target) throws Exception {
    String commitSha;
    boolean branch = false;
    String branchRef = null;
    if (repo.branchExists(target)) {
      branch = true;
      branchRef = Repository.HEADS_REF_PREFIX + target;
      commitSha = repo.readBranch(target);
    } else {
      commitSha = repo.resolveRef(target);
      if (commitSha == null) {
        throw new OperationException("pathspec '" + target
            + "' did not match any file(s) known to june");
      }
      if (!(repo.read(commitSha) instanceof Commit)) {
        throw new OperationException("object " + commitSha + " is not a commit");
      }
    }

    Map<String, FileInfo> files = Revision.tree(repo, commitSha);
    conflicts(repo, files, new Index(repo.indexFile()), "checkout");
    sync(repo, files);

    if (branch) {
      repo.setHead(branchRef);
      return "Switched to branch '" + target + "'";
    }
    repo.setHead(commitSha);
    return "Note: switching to '" + target + "'.\nHEAD is now at " + commitSha.substring(0, 7);
  }

  public static Repository.StatusResult status(Repository repo) throws Exception {
    Index index = new Index(repo.indexFile());
    Map<String, FileInfo> headFiles = head(repo);
    Map<String, File> workspaceFiles = workspace(repo);

    List<Repository.FileChange> staged = staged(index, headFiles);
    List<Repository.FileChange> unstaged = unstaged(index, workspaceFiles);
    List<String> untracked = untracked(index, workspaceFiles);

    String branch = repo.currentBranch();
    String branchDisplay = branch != null
        ? "On branch " + branch
        : (repo.headSha() != null ? "HEAD detached at " + repo.headSha().substring(0, 7) : "No commits yet");

    return new Repository.StatusResult(branchDisplay, staged, unstaged, untracked);
  }

  public static Map<String, FileInfo> head(Repository repo) throws IOException {
    return Revision.tree(repo, repo.headSha());
  }

  private static Map<String, File> workspace(Repository repo) throws IOException {
    Map<String, File> workspaceFiles = new HashMap<>();
    collect(repo.rootDir(), repo.rootDir(), workspaceFiles, Ignore.load(repo.rootDir()));
    return workspaceFiles;
  }

  private static List<Repository.FileChange> staged(
      Index index, Map<String, FileInfo> headFiles) {
    List<Repository.FileChange> staged = new ArrayList<>();
    for (Index.Entry entry : index.entries()) {
      FileInfo headInfo = headFiles.get(entry.path());
      if (headInfo == null) {
        staged.add(new Repository.FileChange(entry.path(), Repository.StatusChangeType.ADDED));
      } else if (!headInfo.hash().equals(entry.hash())) {
        staged.add(new Repository.FileChange(entry.path(), Repository.StatusChangeType.MODIFIED));
      }
    }
    for (String headPath : headFiles.keySet()) {
      if (index.entry(headPath) == null) {
        staged.add(new Repository.FileChange(headPath, Repository.StatusChangeType.DELETED));
      }
    }
    return staged;
  }

  private static List<Repository.FileChange> unstaged(
      Index index, Map<String, File> workspaceFiles) throws Exception {
    List<Repository.FileChange> unstaged = new ArrayList<>();
    for (Index.Entry entry : index.entries()) {
      File file = workspaceFiles.get(entry.path());
      if (file == null) {
        unstaged.add(new Repository.FileChange(entry.path(), Repository.StatusChangeType.DELETED));
        continue;
      }
      if (file.length() == entry.size() && mtime(file) == entry.mtime()) {
        continue;
      }
      String currentSha = hash(file, entry.mode());
      if (!currentSha.equals(entry.hash())) {
        unstaged.add(new Repository.FileChange(entry.path(), Repository.StatusChangeType.MODIFIED));
      }
    }
    return unstaged;
  }

  private static List<String> untracked(
      Index index, Map<String, File> workspaceFiles) {
    List<String> untracked = new ArrayList<>();
    for (String path : workspaceFiles.keySet()) {
      if (index.entry(path) == null) {
        untracked.add(path);
      }
    }
    Collections.sort(untracked);
    return untracked;
  }

  public static String diff(Repository repo, boolean staged) throws Exception {
    Index index = new Index(repo.indexFile());
    return staged ? diffStaged(repo, head(repo), index) : diffUnstaged(repo, workspace(repo), index);
  }

  private static String diffStaged(
      Repository repo, Map<String, FileInfo> headFiles, Index index) throws Exception {
    StringBuilder result = new StringBuilder();
    for (Index.Entry entry : index.entries()) {
      FileInfo headInfo = headFiles.get(entry.path());
      if (headInfo != null && headInfo.hash().equals(entry.hash())) {
        continue;
      }
      byte[] oldBytes = headInfo != null ? repo.read(headInfo.hash()).data() : new byte[0];
      byte[] newBytes = repo.read(entry.hash()).data();
      result.append(diff(oldBytes, newBytes, entry.path()));
    }
    return result.toString();
  }

  private static String diffUnstaged(
      Repository repo, Map<String, File> workspaceFiles, Index index) throws Exception {
    StringBuilder result = new StringBuilder();
    for (Index.Entry entry : index.entries()) {
      File file = workspaceFiles.get(entry.path());
      if (file != null && file.length() == entry.size() && mtime(file) == entry.mtime()) {
        continue;
      }
      byte[] oldBytes = repo.read(entry.hash()).data();
      byte[] newBytes = file != null ? Files.readAllBytes(file.toPath()) : new byte[0];
      if (!Arrays.equals(oldBytes, newBytes)) {
        result.append(diff(oldBytes, newBytes, entry.path()));
      }
    }
    return result.toString();
  }

  private static String diff(byte[] oldBytes, byte[] newBytes, String path) {
    StringBuilder buf = new StringBuilder();
    buf.append("diff a/").append(path).append(" b/").append(path).append("\n");
    if (binary(oldBytes) || binary(newBytes)) {
      buf.append("Binary files differ\n");
      return buf.toString();
    }
    String oldText = new String(oldBytes, StandardCharsets.UTF_8);
    String newText = new String(newBytes, StandardCharsets.UTF_8);
    String[] oldLines = oldText.isEmpty() ? new String[0] : oldText.split("\n", -1);
    String[] newLines = newText.isEmpty() ? new String[0] : newText.split("\n", -1);

    if (oldLines.length == 0 && newLines.length > 0) {
      buf.append("--- /dev/null\n");
      buf.append("+++ b/").append(path).append("\n");
      buf.append("@@ -0,0 +1,").append(newLines.length).append(" @@\n");
      for (String line : newLines) {
        buf.append("+").append(line).append("\n");
      }
      return buf.toString();
    }
    if (newLines.length == 0 && oldLines.length > 0) {
      buf.append("--- a/").append(path).append("\n");
      buf.append("+++ /dev/null\n");
      buf.append("@@ -1,").append(oldLines.length).append(" +0,0 @@\n");
      for (String line : oldLines) {
        buf.append("-").append(line).append("\n");
      }
      return buf.toString();
    }
    buf.append("--- a/").append(path).append("\n");
    buf.append("+++ b/").append(path).append("\n");
    List<String> diffLines = Diff.lines(Arrays.asList(oldLines), Arrays.asList(newLines));
    for (String line : diffLines) {
      buf.append(line).append("\n");
    }
    return buf.toString();
  }

  private static boolean binary(byte[] data) {
    int checkLen = Math.min(data.length, 8000);
    for (int i = 0; i < checkLen; i++) {
      if (data[i] == 0) {
        return true;
      }
    }
    return false;
  }

  public static String reset(Repository repo, String target) throws Exception {
    String sha = repo.resolveRef(target == null ? "HEAD" : target);
    if (sha == null) {
      throw new OperationException(target == null || target.equalsIgnoreCase("HEAD")
          ? "HEAD is empty; nothing to reset to"
          : "commit '" + target + "' not found");
    }
    Commit commit = repo.readCommit(sha);
    sync(repo, Revision.tree(repo, sha));
    repo.updateHead(sha);
    return "HEAD is now at " + sha.substring(0, 7) + " " + title(commit.message());
  }

  public static String title(String message) {
    int nl = message.indexOf('\n');
    return nl != -1 ? message.substring(0, nl) : message;
  }

  public static String restore(Repository repo, List<String> paths, boolean staged)
      throws IOException {
    if (paths.isEmpty()) {
      throw new OperationException("no paths specified");
    }
    Index index = new Index(repo.indexFile());
    StringBuilder result = new StringBuilder();
    if (staged) {
      restoreStaged(repo, paths, index, result);
    } else {
      restoreWorking(repo, paths, index, result);
    }
    return result.toString();
  }

  private static void restoreStaged(
      Repository repo, List<String> paths, Index index, StringBuilder result) throws IOException {
    Map<String, FileInfo> headFiles = head(repo);
    Set<String> expanded = expand(repo, paths, index, headFiles.keySet());
    for (String path : expanded) {
      unstage(path, index, headFiles.get(path), result);
    }
    index.write();
  }

  private static void unstage(
      String path, Index index, FileInfo headFile, StringBuilder result) {
    if (headFile != null) {
      index.add(headFile.hash(), headFile.mode(), path);
      result.append("Unstaged changes for '").append(path).append("'\n");
      return;
    }
    if (index.remove(path)) {
      result.append("Unstaged new file '").append(path).append("'\n");
      return;
    }
    throw new OperationException("pathspec '" + path + "' did not match any files");
  }

  private static void restoreWorking(
      Repository repo, List<String> paths, Index index, StringBuilder result) throws IOException {
    Set<String> expanded = expand(repo, paths, index, Set.of());
    for (String path : expanded) {
      Index.Entry entry = index.entry(path);
      if (entry == null) {
        throw new OperationException("pathspec '" + path + "' did not match any file(s) known to june");
      }
      write(repo, new File(repo.rootDir(), path), entry.hash(), entry.mode());
      result.append("Restored working directory file '").append(path).append("'\n");
    }
  }

  private static Set<String> expand(
      Repository repo, List<String> paths, Index index, Set<String> extraPaths) {
    Set<String> result = new HashSet<>();
    for (String rawPath : paths) {
      String relPath = repo.relPath(rawPath);
      String prefix = (relPath.equals(".") || relPath.isEmpty()) ? "" : relPath;
      boolean matched = match(prefix, index, extraPaths, result);
      if (!matched && !new File(repo.rootDir(), relPath).isDirectory()) {
        result.add(relPath);
      }
    }
    return result;
  }

  private static boolean match(
      String prefix, Index index, Set<String> extraPaths, Set<String> result) {
    boolean matched = false;
    for (Index.Entry entry : index.entries()) {
      if (match(prefix, entry.path())) {
        result.add(entry.path());
        matched = true;
      }
    }
    for (String extra : extraPaths) {
      if (match(prefix, extra)) {
        result.add(extra);
        matched = true;
      }
    }
    return matched;
  }

  private static boolean match(String prefix, String candidate) {
    return prefix.isEmpty() || candidate.equals(prefix) || candidate.startsWith(prefix + "/");
  }

  public static String rm(Repository repo, List<String> paths, boolean cached)
      throws IOException {
    if (paths.isEmpty()) {
      throw new OperationException("no files specified");
    }
    Index index = new Index(repo.indexFile());
    StringBuilder result = new StringBuilder();
    for (String rawPath : paths) {
      String relPath = repo.relPath(rawPath);
      if (index.entry(relPath) == null) {
        throw new OperationException("pathspec '" + rawPath + "' did not match any files");
      }
      index.remove(relPath);
      File target = new File(repo.rootDir(), relPath);
      if (!cached && exists(target) && !target.delete()) {
        throw new OperationException("could not remove '" + rawPath + "'");
      }
      result.append("rm '").append(relPath).append("'\n");
    }
    index.write();
    return result.toString();
  }

  public static String mv(Repository repo, String sourcePath, String destinationPath)
      throws Exception {
    Index index = new Index(repo.indexFile());
    String relSource = repo.relPath(sourcePath);
    if (index.entry(relSource) == null) {
      throw new OperationException("not under version control, source=" + sourcePath);
    }
    File sourceFile = new File(repo.rootDir(), relSource);
    if (!exists(sourceFile)) {
      throw new OperationException("source file does not exist: " + sourcePath);
    }
    String relDest = repo.relPath(destinationPath);
    File destFile = new File(repo.rootDir(), relDest);
    if (destFile.isDirectory()) {
      relDest = (relDest.isEmpty() ? "" : relDest + "/") + sourceFile.getName();
      destFile = new File(repo.rootDir(), relDest);
    }
    if (exists(destFile)) {
      throw new OperationException("destination exists, source=" + sourcePath + ", destination=" + destinationPath);
    }
    if (destFile.getParentFile() != null) {
      destFile.getParentFile().mkdirs();
    }
    Files.move(sourceFile.toPath(), destFile.toPath());
    index.remove(relSource);
    String mode = mode(destFile);
    String sha = repo.writeTarget(destFile, mode);
    index.add(sha, mode, relDest, destFile.length(), mtime(destFile));
    index.write();
    return "Renamed '" + relSource + "' to '" + relDest + "'";
  }
}
