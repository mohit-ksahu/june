package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import june.Helper;
import june.Index;
import june.Modes;
import june.OperationException;
import june.Repository;

public final class Commit {

  public record CommitResult(String message, String sha, String branch) {}
  private Commit() {}

  public static CommitResult commit(Repository repo, String message, boolean autoStage)
      throws IOException {
    if (message == null || message.isBlank()) {
      throw new OperationException("fatal: empty commit message");
    }
    Index index = new Index(repo.getIndexFile());
    if (autoStage) {
      for (Index.Entry entry : new ArrayList<>(index.getEntries())) {
        File target = new File(repo.getRootDir(), entry.path());
        if (target.exists() || Files.isSymbolicLink(target.toPath())) {
          String mode = Helper.entryMode(target);
          String sha = Helper.entrySha1(target, mode);
          if (!sha.equals(entry.sha1()) || !mode.equals(entry.mode())) {
            index.add(repo.writeFileOrSymlinkTarget(target, mode), mode, entry.path());
          }
        } else {
          index.remove(entry.path());
        }
      }
      index.write();
    }
    List<Index.Entry> entries = index.getEntries();
    if (entries.isEmpty()) {
      String headSha = repo.getHeadCommitSha1();
      String msg2 = headSha == null
          ? "nothing to commit (create/copy files and use \"add\" to stage)"
          : "nothing added to commit but untracked files may exist (use \"june add\" to track)";
      return new CommitResult(msg2, null, null);
    }
    Helper.TreeNode root = new Helper.TreeNode("", Modes.TREE, null);
    for (Index.Entry entry : entries) {
      String[] parts = entry.path().split("/");
      Helper.TreeNode current = root;
      for (int i = 0; i < parts.length; i++) {
        if (i == parts.length - 1) {
          current.children.put(parts[i], new Helper.TreeNode(parts[i], entry.mode(), entry.sha1()));
        } else {
          current.children.putIfAbsent(parts[i], new Helper.TreeNode(parts[i], Modes.TREE, null));
          current = current.children.get(parts[i]);
        }
      }
    }
    String treeSha = Helper.writeTree(root, repo);
    String parentSha = repo.getHeadCommitSha1();
    if (parentSha != null && repo.readCommit(parentSha).getTreeSha1().equals(treeSha)) {
      return new CommitResult("nothing to commit, working tree clean", null, null);
    }
    List<String> parents = parentSha != null ? List.of(parentSha) : List.of();
    ZonedDateTime now = ZonedDateTime.now();
    long ts = now.toEpochSecond();
    String tz = now.format(DateTimeFormatter.ofPattern("xx"));
    String name = Config.get(repo, "user.name");
    if (name == null || name.isEmpty()) {
      name = System.getenv("USER");
      if (name == null || name.isEmpty()) {
        name = System.getProperty("user.name", "June User");
      }
    }
    String email = Config.get(repo, "user.email");
    if (email == null || email.isEmpty()) {
      email = name.toLowerCase().replaceAll("\\s+", "") + "@localhost";
    }
    String author = name + " <" + email + "> " + ts + " " + tz;
    String commitSha = repo.write(new june.Commit(treeSha, parents, author, author, message));
    repo.updateHeadRefOrCommit(commitSha);
    String branch = repo.getCurrentBranch();
    String branchDisplay = branch != null ? branch : "detached HEAD";
    return new CommitResult(
        "[" + branchDisplay + " " + commitSha.substring(0, 7) + "] " + message, commitSha, branch);
  }
}