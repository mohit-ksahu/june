package june;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class Repository {
  public static final String REPO_DIR = ".june";
  public static final String REFS = "refs";
  public static final String HEADS = "heads";
  public static final String TAGS = "tags";
  public static final String HEAD_FILE = "HEAD";
  public static final String INDEX_FILE = "index";
  public static final String REF_PREFIX = "ref: ";
  public static final String HEADS_REF_PREFIX = REF_PREFIX + REFS + "/" + HEADS + "/";
  public static final String MAIN_BRANCH = "main";

  public record CommitResult(String message, String hash, String branch) {}
  public record BranchResult(List<String> branches, String current) {}
  public record LogEntry(String hash, String author, String date, String message, List<String> parents) {}
  public record StatusResult(String branch, List<FileChange> staged, List<FileChange> unstaged, List<String> untracked) {}
  public record FileChange(String path, StatusChangeType type) {}
  public enum StatusChangeType { ADDED, DELETED, MODIFIED }

  private final File rootDir;
  private final File repoDir;
  private final File headsDir;
  private final File tagsDir;
  private final File headFile;
  private final File indexFile;
  private final Odb objects;
  private final File currentDir;

  public Repository(File currentDir) {
    this(currentDir, parent());
  }

  public Repository(File currentDir, File customParent) {
    this.currentDir = currentDir.getAbsoluteFile();
    if (customParent != null) {
      this.repoDir = new File(customParent, REPO_DIR);
      this.rootDir = currentDir.getAbsoluteFile();
    } else {
      File found = find(currentDir);
      if (found != null) {
        this.repoDir = found;
        this.rootDir = found.getParentFile();
      } else {
        this.repoDir = new File(currentDir, REPO_DIR);
        this.rootDir = currentDir;
      }
    }
    File refsDir = new File(repoDir, REFS);
    this.headsDir = new File(refsDir, HEADS);
    this.tagsDir = new File(refsDir, TAGS);
    this.headFile = new File(repoDir, HEAD_FILE);
    this.indexFile = new File(repoDir, INDEX_FILE);
    this.objects = new Odb(new File(repoDir, Odb.OBJECTS_DIR_NAME));
  }

  public String relPath(String path) {
    File file = new File(path);
    File absFile = file.isAbsolute() ? file : new File(currentDir, path).getAbsoluteFile();
    String rel = rootDir.getAbsoluteFile().toPath().normalize()
        .relativize(absFile.toPath().normalize()).toString().replace('\\', '/');
    if (rel.startsWith("../") || rel.equals("..")) {
      throw new OperationException(path + " is outside repository");
    }
    return (rel.equals(".") || rel.isEmpty()) ? "" : rel;
  }

  private static File parent() {
    String directory = System.getProperty("june.dir", System.getenv("JUNE_DIR"));
    return (directory != null && !directory.isBlank()) ? new File(directory).getAbsoluteFile() : null;
  }

  public File rootDir() {
    return rootDir;
  }

  public File repoDir() {
    return repoDir;
  }

  public File indexFile() {
    return indexFile;
  }

  public boolean exists() {
    return repoDir.isDirectory() && headFile.isFile();
  }

  public void init() throws IOException {
    repoDir.mkdirs();
    objects.mkdirs();
    headsDir.mkdirs();
    tagsDir.mkdirs();
    if (!headFile.exists()) {
      Files.writeString(headFile.toPath(), HEADS_REF_PREFIX + MAIN_BRANCH + "\n");
    }
  }

  public String headTarget() throws IOException {
    return read(headFile);
  }

  public void setHead(String target) throws IOException {
    writeLock(headFile, target + "\n");
  }

  public String currentBranch() throws IOException {
    String head = headTarget();
    return (head != null && head.startsWith(HEADS_REF_PREFIX))
        ? head.substring(HEADS_REF_PREFIX.length()).trim()
        : null;
  }

  public String headSha() throws IOException {
    String head = headTarget();
    if (head == null) {
      return null;
    }
    return head.startsWith(REF_PREFIX) ? read(headFile(head)) : head;
  }

  public String resolveRef(String ref) throws IOException {
    if (ref == null || ref.isEmpty()) {
      return null;
    }
    if (ref.equals("HEAD")) {
      return headSha();
    }
    if (branchExists(ref)) {
      return readBranch(ref);
    }
    if (tagExists(ref)) {
      return readTag(ref);
    }
    return Refs.resolve(repoDir, ref);
  }

  public void writeBranch(String name, String sha) throws IOException {
    writeLock(branchFile(name), sha + "\n");
  }

  public void updateHead(String sha) throws IOException {
    String head = headTarget();
    if (head != null && head.startsWith(REF_PREFIX)) {
      writeLock(headFile(head), sha + "\n");
    } else {
      setHead(sha);
    }
  }

  public List<String> branchNames() throws IOException {
    return list(headsDir);
  }

  public boolean branchExists(String name) {
    return branchFile(name).isFile();
  }

  public String readBranch(String name) throws IOException {
    return read(branchFile(name));
  }

  public String dropBranch(String name) throws IOException {
    return drop(branchFile(name), headsDir, "branch");
  }

  public boolean tagExists(String name) {
    return tagFile(name).isFile();
  }

  public String readTag(String name) throws IOException {
    return read(tagFile(name));
  }

  public void writeTag(String name, String sha) throws IOException {
    if (tagExists(name)) {
      throw new OperationException("tag '" + name + "' already exists");
    }
    writeLock(tagFile(name), sha + "\n");
  }

  public String tagDelete(String name) throws IOException {
    return drop(tagFile(name), tagsDir, "tag");
  }

  private static String read(File file) throws IOException {
    return file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8).trim() : null;
  }

  private String drop(File file, File base, String kind) throws IOException {
    if (!file.isFile()) {
      throw new OperationException(kind + " '" + file.getName() + "' not found.");
    }
    String sha = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
    if (!file.delete()) {
      throw new IOException("could not delete " + kind + " ref: " + file);
    }
    Refs.clean(file.getParentFile(), base);
    return sha;
  }

  public String write(ObjectData obj) throws Exception {
    return objects.write(obj);
  }

  public ObjectData read(String sha) throws IOException {
    return objects.read(sha);
  }

  public Commit readCommit(String sha) throws IOException {
    ObjectData obj = objects.read(sha);
    if (!(obj instanceof Commit commit)) {
      throw new OperationException("object " + sha + " is a " + obj.type() + ", not a commit");
    }
    return commit;
  }

  public void write(String hash, File dest) throws IOException {
    objects.write(hash, dest);
  }

  public File branchFile(String name) {
    check(name, "name");
    return new File(headsDir, name);
  }

  public File tagFile(String name) {
    check(name, "name");
    return new File(tagsDir, name);
  }

  public File headFile(String headTarget) {
    if (headTarget == null || !headTarget.startsWith(REF_PREFIX)) {
      throw new OperationException("malformed HEAD reference");
    }
    String ref = headTarget.substring(REF_PREFIX.length()).trim();
    if (ref.isEmpty()) {
      throw new OperationException("malformed HEAD reference");
    }
    check(ref, "path");
    return new File(repoDir, ref);
  }

  private static void check(String ref, String kind) {
    if (ref == null || ref.isBlank() || ref.contains("\0") || ref.startsWith("/")
        || ref.contains("..") || ref.contains("\\")) {
      throw new OperationException("invalid ref " + kind + ": " + ref);
    }
  }

  public String writeTarget(File target, String mode) throws Exception {
    if (mode.equals(Mode.SYMLINK)) {
      String linkTarget = Files.readSymbolicLink(target.toPath()).toString();
      return write(new ObjectData(ObjectData.BLOB, linkTarget.getBytes(StandardCharsets.UTF_8)));
    }
    return objects.writeBlob(target);
  }

  public void writeLock(File target, String content) throws IOException {
    FileLock.write(target, content);
  }

  private List<String> list(File dir) throws IOException {
    if (!dir.isDirectory()) {
      return List.of();
    }
    try (Stream<Path> stream = Files.walk(dir.toPath())) {
      return stream.filter(Files::isRegularFile)
          .map(path -> dir.toPath().relativize(path).toString().replace('\\', '/'))
          .sorted()
          .toList();
    }
  }

  private static File find(File currentDir) {
    File dir = currentDir.getAbsoluteFile();
    while (dir != null) {
      File test = new File(dir, REPO_DIR);
      if (test.isDirectory()) {
        return test;
      }
      dir = dir.getParentFile();
    }
    return null;
  }

  public void add(List<String> paths) throws Exception {
    Index index = new Index(indexFile());
    var rules = Ignore.load(rootDir());
    for (String rawPath : paths) {
      Worktree.process(this, index, rules, rawPath);
    }
    index.write();
  }

  public CommitResult commit(String message, boolean auto) throws Exception {
    return Revision.commit(this, message, auto);
  }

  public String checkout(String target) throws Exception {
    return Worktree.checkout(this, target);
  }

  public StatusResult status() throws Exception {
    return Worktree.status(this);
  }

  public String diff(boolean staged) throws Exception {
    return Worktree.diff(this, staged);
  }

  public List<LogEntry> log(int limit) throws IOException {
    return Revision.log(this, limit);
  }

  public String merge(String target) throws Exception {
    return Revision.merge(this, target);
  }

  public String reset(String target) throws Exception {
    return Worktree.reset(this, target);
  }

  public String restore(List<String> paths, boolean staged) throws IOException {
    return Worktree.restore(this, paths, staged);
  }

  public String rm(List<String> paths, boolean cached) throws IOException {
    return Worktree.rm(this, paths, cached);
  }

  public String mv(String sourcePath, String destinationPath) throws Exception {
    return Worktree.mv(this, sourcePath, destinationPath);
  }

  public String catFile(String ref) throws IOException {
    return Revision.catFile(this, ref);
  }

  public BranchResult branchList() throws IOException {
    return Refs.branchList(this);
  }

  public String branchCreate(String name) throws IOException {
    return Refs.branchCreate(this, name);
  }

  public String branchDelete(String name, boolean force) throws IOException {
    return Refs.branchDelete(this, name, force);
  }

  public String branchRename(String oldName, String newName) throws IOException {
    return Refs.branchRename(this, oldName, newName);
  }

  public List<String> tagList() throws IOException {
    return list(tagsDir);
  }

  public void tagCreate(String name, String targetRef) throws IOException {
    Refs.tagCreate(this, name, targetRef);
  }

  public String configGet(String key) throws IOException {
    return Refs.configGet(this, key);
  }

  public void configSet(String key, String value) throws IOException {
    Refs.configSet(this, key, value);
  }
}
