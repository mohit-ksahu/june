package june;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

public final class Revision {
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy Z");
  private static final DateTimeFormatter ZONE_FORMAT =
      DateTimeFormatter.ofPattern("xx");

  private record LogNode(String sha, Commit commit, long timestamp) {}

  public static class TreeNode {
    public final String name;
    public final String mode;
    public final String hash;
    public final TreeMap<String, TreeNode> children = new TreeMap<>();

    public TreeNode(String name, String mode, String hash) {
      this.name = name;
      this.mode = mode;
      this.hash = hash;
    }
  }

  public static String write(TreeNode node, Repository repo) throws Exception {
    if (!node.mode.equals(Mode.TREE)) {
      return node.hash;
    }
    List<Tree.Entry> entries = new ArrayList<>();
    for (TreeNode child : node.children.values()) {
      entries.add(new Tree.Entry(child.mode, child.name, write(child, repo)));
    }
    return repo.write(new Tree(entries));
  }

  public static void collect(
      String hash, String prefix, Repository repo, Map<String, Worktree.FileInfo> out) throws IOException {
    Tree tree = (Tree) repo.read(hash);
    for (Tree.Entry entry : tree.entries()) {
      String path = prefix.isEmpty() ? entry.name() : prefix + entry.name();
      if (entry.mode().equals(Mode.TREE)) {
        collect(entry.hash(), path + "/", repo, out);
      } else {
        out.put(path, new Worktree.FileInfo(entry.hash(), entry.mode()));
      }
    }
  }

  public static Map<String, Worktree.FileInfo> tree(Repository repo, String commitSha) throws IOException {
    Map<String, Worktree.FileInfo> files = new HashMap<>();
    if (commitSha != null) {
      collect(repo.readCommit(commitSha).tree(), "", repo, files);
    }
    return files;
  }

  public static String build(Repository repo, Collection<Index.Entry> entries) throws Exception {
    TreeNode root = new TreeNode("", Mode.TREE, null);
    for (Index.Entry entry : entries) {
      add(root, entry);
    }
    return write(root, repo);
  }

  private static void add(TreeNode root, Index.Entry entry) {
    String path = entry.path();
    int slash = path.indexOf('/');
    if (slash == -1) {
      root.children.put(path, new TreeNode(path, entry.mode(), entry.hash()));
      return;
    }
    TreeNode current = root;
    int start = 0;
    while (slash != -1) {
      String segment = path.substring(start, slash);
      current = current.children.computeIfAbsent(segment, name -> new TreeNode(name, Mode.TREE, null));
      start = slash + 1;
      slash = path.indexOf('/', start);
    }
    String name = path.substring(start);
    current.children.put(name, new TreeNode(name, entry.mode(), entry.hash()));
  }

  public static Repository.CommitResult commit(
      Repository repo, String message, boolean auto) throws Exception {
    if (message == null || message.isBlank()) {
      throw new OperationException("empty commit message");
    }
    Index index = new Index(repo.indexFile());
    if (auto) {
      Worktree.stage(repo, index);
    }
    Collection<Index.Entry> entries = index.entries();
    String parentSha = repo.headSha();
    if (entries.isEmpty() && parentSha == null) {
      return new Repository.CommitResult(
          "nothing to commit (create/copy files and use \"add\" to stage)", null, null);
    }
    String treeSha = build(repo, entries);
    if (parentSha != null && repo.readCommit(parentSha).tree().equals(treeSha)) {
      return new Repository.CommitResult("nothing to commit, working tree clean", null, null);
    }
    String author = author(repo, ZonedDateTime.now());
    List<String> parents = parentSha != null ? List.of(parentSha) : List.of();
    String commitSha = repo.write(new Commit(treeSha, parents, author, author, message));
    repo.updateHead(commitSha);

    String branch = repo.currentBranch();
    String branchDisplay = branch != null ? branch : "detached HEAD";
    return new Repository.CommitResult(
        "[" + branchDisplay + " " + commitSha.substring(0, 7) + "] " + message, commitSha, branch);
  }

  private static String author(Repository repo, ZonedDateTime now) throws IOException {
    Properties props = Refs.load(repo);
    String name = value(props.getProperty("user.name"), System.getenv("JUNE_AUTHOR_NAME"));
    String email = value(props.getProperty("user.email"), System.getenv("JUNE_AUTHOR_EMAIL"));
    if (name.isEmpty() || email.isEmpty()) {
      throw new OperationException(
          "Author identity unknown. Please set 'user.name' and 'user.email' via june config.");
    }
    return name + " <" + email + "> " + now.toEpochSecond() + " " + now.format(ZONE_FORMAT);
  }

  private static String value(String primary, String fallback) {
    if (primary != null && !primary.isEmpty()) {
      return primary;
    }
    return (fallback != null && !fallback.isEmpty()) ? fallback : "";
  }

  public static boolean isAncestor(
      Repository repo, String ancestorSha, String descendantSha) throws IOException {
    if (ancestorSha.equals(descendantSha)) {
      return true;
    }
    Set<String> visited = new HashSet<>();
    Queue<String> queue = new ArrayDeque<>();
    queue.add(descendantSha);
    visited.add(descendantSha);

    while (!queue.isEmpty()) {
      if (checkParents(repo, queue.poll(), ancestorSha, queue, visited)) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkParents(
      Repository repo, String current, String ancestor, Queue<String> queue, Set<String> visited) throws IOException {
    for (String parent : repo.readCommit(current).parents()) {
      if (parent.equals(ancestor)) {
        return true;
      }
      if (visited.add(parent)) {
        queue.add(parent);
      }
    }
    return false;
  }

  public static List<Repository.LogEntry> log(Repository repo, int limit) throws IOException {
    String head = repo.headSha();
    if (head == null || limit <= 0) {
      return List.of();
    }
    PriorityQueue<LogNode> pq = new PriorityQueue<>((a, b) -> {
      int cmp = Long.compare(b.timestamp, a.timestamp);
      return cmp != 0 ? cmp : b.sha.compareTo(a.sha);
    });
    Set<String> visited = new HashSet<>();
    Commit headCommit = repo.readCommit(head);
    pq.add(new LogNode(head, headCommit, timestamp(headCommit.author())));
    visited.add(head);

    List<Repository.LogEntry> entries = new ArrayList<>();
    while (!pq.isEmpty() && entries.size() < limit) {
      LogNode node = pq.poll();
      entries.add(entry(node.sha, node.commit, node.timestamp));
      queueParents(repo, node.commit, pq, visited);
    }
    return entries;
  }

  private static void queueParents(
      Repository repo, Commit commit, PriorityQueue<LogNode> pq, Set<String> visited) throws IOException {
    for (String parent : commit.parents()) {
      if (visited.add(parent)) {
        Commit parentCommit = repo.readCommit(parent);
        pq.add(new LogNode(parent, parentCommit, timestamp(parentCommit.author())));
      }
    }
  }

  private static long timestamp(String author) {
    int last = author.lastIndexOf(' ');
    int second = last != -1 ? author.lastIndexOf(' ', last - 1) : -1;
    if (second == -1) {
      return 0L;
    }
    try {
      return Long.parseLong(author.substring(second + 1, last));
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static String author(String line, long epoch) {
    if (epoch == 0) {
      return line;
    }
    int last = line.lastIndexOf(' ');
    int second = last != -1 ? line.lastIndexOf(' ', last - 1) : -1;
    return second != -1 ? line.substring(0, second) : line;
  }

  private static Repository.LogEntry entry(String sha, Commit commit, long epoch) {
    String line = commit.author();
    String date = epoch == 0 ? "" :
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault()).format(DATE_FORMAT);
    return new Repository.LogEntry(sha, author(line, epoch), date, commit.message(), commit.parents());
  }

  public static String merge(Repository repo, String target) throws Exception {
    if (target.isEmpty()) {
      throw new OperationException("merge requires a branch name, tag, or commit hash to merge");
    }
    String currentSha = repo.headSha();
    if (currentSha == null) {
      throw new OperationException("HEAD is empty; cannot merge into an empty branch");
    }
    String targetSha = repo.resolveRef(target);
    if (targetSha == null) {
      throw new OperationException("target '" + target + "' not found");
    }
    if (!(repo.read(targetSha) instanceof Commit)) {
      throw new OperationException("object " + targetSha + " is not a commit");
    }
    if (currentSha.equals(targetSha)) {
      return "Already up to date.";
    }
    if (isAncestor(repo, currentSha, targetSha)) {
      Map<String, Worktree.FileInfo> targetFiles = tree(repo, targetSha);
      Worktree.conflicts(repo, targetFiles, new Index(repo.indexFile()), "merge");
      Worktree.sync(repo, targetFiles);
      repo.updateHead(targetSha);
      return "Updating " + currentSha.substring(0, 7) + ".." + targetSha.substring(0, 7)
          + "\nFast-forward";
    }
    if (isAncestor(repo, targetSha, currentSha)) {
      return "Already up to date.";
    }
    throw new OperationException("Not a fast-forward merge (non-fast-forward merges are not supported).");
  }

  public static String catFile(Repository repo, String ref) throws IOException {
    String sha = repo.resolveRef(ref);
    if (sha == null) {
      throw new OperationException("object " + ref + " not found");
    }
    ObjectData item = repo.read(sha);
    if (item instanceof Tree tree) {
      return format(tree);
    }
    return new String(item.data(), StandardCharsets.UTF_8);
  }

  private static String format(Tree tree) {
    StringBuilder buf = new StringBuilder();
    for (Tree.Entry entry : tree.entries()) {
      if (buf.length() > 0) {
        buf.append("\n");
      }
      String type = entry.mode().equals(Mode.TREE) ? ObjectData.TREE : ObjectData.BLOB;
      buf.append(entry.mode()).append(" ").append(type).append(" ").append(entry.hash())
        .append("\t").append(entry.name());
    }
    return buf.toString();
  }
}
