package june;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import june.ObjectStore.ObjectStream;

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

  private final File rootDir;
  private final File repoDir;
  private final File refsDir;
  private final File headsDir;
  private final File tagsDir;
  private final File headFile;
  private final File indexFile;
  private final ObjectStore objects;
  private final File currentDir;

  public Repository(File currentDir) {
    this(currentDir, readRepoParent());
  }

  public Repository(File currentDir, File customRepoParent) {
    this.currentDir = currentDir.getAbsoluteFile();
    if (customRepoParent != null) {
      this.repoDir = new File(customRepoParent, REPO_DIR);
      this.rootDir = currentDir.getAbsoluteFile();
    } else {
      File dir = currentDir.getAbsoluteFile();
      File found = null;
      while (dir != null) {
        File test = new File(dir, REPO_DIR);
        if (test.isDirectory()) {
          found = test;
          break;
        }
        dir = dir.getParentFile();
      }
      if (found != null) {
        this.repoDir = found;
        this.rootDir = found.getParentFile();
      } else {
        this.repoDir = new File(currentDir, REPO_DIR);
        this.rootDir = currentDir;
      }
    }
    this.refsDir = new File(repoDir, REFS);
    this.headsDir = new File(refsDir, HEADS);
    this.tagsDir = new File(refsDir, TAGS);
    this.headFile = new File(repoDir, HEAD_FILE);
    this.indexFile = new File(repoDir, INDEX_FILE);
    this.objects = new ObjectStore(repoDir);
  }

  public String getRelativePath(String path) {
    File f = new File(path);
    File absFile = f.isAbsolute() ? f : new File(currentDir, path).getAbsoluteFile();
    String rel = rootDir.getAbsoluteFile().toPath().normalize()
        .relativize(absFile.toPath().normalize()).toString().replace('\\', '/');
    if (rel.startsWith("../") || rel.equals("..")) {
      throw new OperationException("fatal: " + path + " is outside repository");
    }
    return (rel.equals(".") || rel.isEmpty()) ? "" : rel;
  }


  private static File readRepoParent() {
    String directory = System.getProperty("june.dir", System.getenv("JUNE_DIR"));
    return (directory != null && !directory.isBlank()) ? new File(directory).getAbsoluteFile() : null;
  }

  public File getRootDir() {
    return rootDir;
  }

  public File getRepoDir() {
    return repoDir;
  }

  public File getIndexFile() {
    return indexFile;
  }

  public ObjectStore getObjects() {
    return objects;
  }

  public boolean exists() {
    return repoDir.isDirectory() && headFile.isFile();
  }

  public void init() throws IOException {
    repoDir.mkdirs();
    objects.mkdirs();
    refsDir.mkdirs();
    headsDir.mkdirs();
    tagsDir.mkdirs();
    if (!headFile.exists()) {
      Files.writeString(
          headFile.toPath(), HEADS_REF_PREFIX + MAIN_BRANCH + "\n", StandardCharsets.UTF_8);
    }
  }

  public String getHeadTarget() throws IOException {
    return headFile.exists() ? Files.readString(headFile.toPath(), StandardCharsets.UTF_8).trim() : null;
  }

  public void setHeadTarget(String target) throws IOException {
    writeWithLock(headFile, target + "\n");
  }

  public String getCurrentBranch() throws IOException {
    String head = getHeadTarget();
    return (head != null && head.startsWith(HEADS_REF_PREFIX))
        ? head.substring(HEADS_REF_PREFIX.length()).trim()
        : null;
  }

  public String getHeadCommitSha1() throws IOException {
    String head = getHeadTarget();
    if (head == null) {
      return null;
    }
    if (head.startsWith(REF_PREFIX)) {
      File refFile = headRefFile(head);
      return refFile.exists() ? Files.readString(refFile.toPath(), StandardCharsets.UTF_8).trim() : null;
    }
    return head;
  }

  public void updateBranchRef(String name, String sha) throws IOException {
    writeWithLock(branchRefFile(name), sha + "\n");
  }

  public void updateHeadRefOrCommit(String sha) throws IOException {
    String head = getHeadTarget();
    if (head != null && head.startsWith(REF_PREFIX)) {
      writeWithLock(headRefFile(head), sha + "\n");
    } else {
      setHeadTarget(sha);
    }
  }

  public List<String> getBranches() {
    return listRefs(headsDir);
  }

  public boolean branchExists(String name) {
    return branchRefFile(name).isFile();
  }

  public String readBranchRef(String name) throws IOException {
    File refFile = branchRefFile(name);
    return refFile.isFile() ? Files.readString(refFile.toPath(), StandardCharsets.UTF_8).trim() : null;
  }

  public String deleteBranchRef(String name) throws IOException {
    File refFile = branchRefFile(name);
    if (!refFile.isFile()) {
      throw new OperationException("error: branch '" + name + "' not found.");
    }
    String sha = Files.readString(refFile.toPath(), StandardCharsets.UTF_8).trim();
    if (!refFile.delete()) {
      throw new IOException("fatal: could not delete branch ref: " + refFile);
    }
    Helper.deleteEmptyParentDirs(refFile.getParentFile(), headsDir);
    return sha;
  }

  public List<String> getTags() {
    return listRefs(tagsDir);
  }

  public boolean tagExists(String name) {
    return tagRefFile(name).isFile();
  }

  public String readTagRef(String name) throws IOException {
    File refFile = tagRefFile(name);
    return refFile.isFile() ? Files.readString(refFile.toPath(), StandardCharsets.UTF_8).trim() : null;
  }

  public void createTag(String name, String sha) throws IOException {
    if (tagExists(name)) {
      throw new OperationException("fatal: tag '" + name + "' already exists");
    }
    writeWithLock(tagRefFile(name), sha + "\n");
  }

  public String deleteTagRef(String name) throws IOException {
    File refFile = tagRefFile(name);
    if (!refFile.isFile()) {
      throw new OperationException("error: tag '" + name + "' not found.");
    }
    String sha = Files.readString(refFile.toPath(), StandardCharsets.UTF_8).trim();
    if (!refFile.delete()) {
      throw new IOException("fatal: could not delete tag ref: " + refFile);
    }
    Helper.deleteEmptyParentDirs(refFile.getParentFile(), tagsDir);
    return sha;
  }

  public String write(ObjectData obj) throws IOException {
    return objects.write(obj);
  }

  public ObjectData read(String sha) throws IOException {
    return objects.read(sha);
  }

  public Commit readCommit(String sha) throws IOException {
    ObjectData obj = objects.read(sha);
    if (!(obj instanceof Commit c)) {
      throw new OperationException("fatal: object " + sha + " is a " + obj.getType() + ", not a commit");
    }
    return c;
  }

  public ObjectStream getObjectStream(String sha) throws IOException {
    return objects.getObjectStream(sha);
  }

  public void readToFile(String sha, File dest) throws IOException {
    objects.readToFile(sha, dest);
  }

  public File branchRefFile(String name) {
    validateRefName(name);
    return new File(headsDir, name);
  }

  public File tagRefFile(String name) {
    validateRefName(name);
    return new File(tagsDir, name);
  }

  public File headRefFile(String headTarget) {
    if (headTarget == null || !headTarget.startsWith(REF_PREFIX)) {
      throw new OperationException("fatal: malformed HEAD reference");
    }
    String ref = headTarget.substring(REF_PREFIX.length()).trim();
    if (ref.isEmpty()) {
      throw new OperationException("fatal: malformed HEAD reference");
    }
    validateRefPath(ref);
    return new File(repoDir, ref);
  }

  private static void validateRefPath(String path) {
    if (path == null || path.isBlank() || path.contains("\0") || path.startsWith("/")
        || path.contains("..") || path.contains("\\")) {
      throw new OperationException("fatal: invalid ref path: " + path);
    }
  }

  private static void validateRefName(String name) {
    if (name == null || name.isBlank() || name.contains("\0") || name.contains("..")
        || name.startsWith("/") || name.contains("\\")) {
      throw new OperationException("fatal: invalid ref name: " + name);
    }
  }

  public String writeFileOrSymlinkTarget(File target, String mode) throws IOException {
    if (mode.equals(Modes.SYMLINK)) {
      String linkTarget = Files.readSymbolicLink(target.toPath()).toString();
      return write(new ObjectData(ObjectTypes.BLOB, linkTarget.getBytes(StandardCharsets.UTF_8)));
    }
    return objects.writeBlob(target);
  }

  public void writeWithLock(File target, String content) throws IOException {
    File lock = new File(target.getParentFile(), target.getName() + ".lock");
    FileLock.acquireOrBreak(lock);
    try {
      Files.writeString(lock.toPath(), content, StandardCharsets.UTF_8);
      Files.move(lock.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      lock.delete();
      throw e;
    }
  }

  private List<String> listRefs(File dir) {
    if (!dir.isDirectory()) {
      return new ArrayList<>();
    }
    try (java.util.stream.Stream<java.nio.file.Path> stream = Files.walk(dir.toPath())) {
      return stream.filter(Files::isRegularFile)
          .map(p -> dir.toPath().relativize(p).toString().replace('\\', '/'))
          .sorted()
          .collect(java.util.stream.Collectors.toList());
    } catch (IOException e) {
      return new ArrayList<>();
    }
  }

  
  public void add(java.util.List<String> paths) throws java.io.IOException {
    june.lib.Add.add(this, paths);
  }
  public String rm(java.util.List<String> paths, boolean cached) throws java.io.IOException {
    return june.lib.Rm.rm(this, paths, cached);
  }
  public june.lib.Commit.CommitResult commit(String msg, boolean auto) throws java.io.IOException {
    return june.lib.Commit.commit(this, msg, auto);
  }
  public june.lib.Status.StatusResult status() throws java.io.IOException {
    return june.lib.Status.status(this);
  }
  public String checkout(String target) throws java.io.IOException {
    return june.lib.Checkout.checkout(this, target);
  }
  public String restore(java.util.List<String> paths, boolean staged) throws java.io.IOException {
    return june.lib.Restore.restore(this, paths, staged);
  }
  public String reset(String target) throws java.io.IOException {
    return june.lib.Reset.reset(this, target);
  }
  public june.lib.Branch.BranchResult listBranches() throws java.io.IOException {
    return june.lib.Branch.list(this);
  }
  public String createBranch(String name) throws java.io.IOException {
    return june.lib.Branch.create(this, name);
  }
}