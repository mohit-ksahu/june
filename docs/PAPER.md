# June

June is a simple version control system written in Java.

## 1. System Design and Key Layers

June is written in Java and does not use any external packages. The code is split into three layers to keep the storage logic, the feature logic, and the user interface separate.

### Why split the code this way?

Separating the command-line interface from the core storage logic keeps the codebase modular. This ensures the core logic is reusable and unaffected by changes to user commands.

### The three layers:

1. **The Storage and Utility Library (`june`)**: This layer handles repository paths, reads and writes compressed objects, updates the staging index, compiles ignore rules, calculates diffs, and locks files to prevent write conflicts.
2. **The Feature Library (`june.lib`)**: This layer implements the actual features (like staging, committing, checking out, and merging). It coordinates the storage layer and the workspace but does not deal with command parsing or flags.
3. **The CLI (App and command classes)**: This layer parses command-line arguments, checks user inputs, prints formatted messages to the console, and exits with a non-zero code if something goes wrong.

### Command Dispatcher (`App.java`)

- June routes all command-line inputs through `App.java` in the `cmd/` directory.
- By keeping command routing separate, other programs can call the library directly without needing to validate user commands.
- The main entry point splits the arguments so that each command handler only gets the arguments it needs. It then sets up the repository directory path, checks if the repository exists (except for the init command), and runs the correct command handler.

```java
public static void main(String[] args) {
  if (args.length == 0) {
    printUsage();
    return;
  }
  String command = args[0];
  try {
    Repository repo = new Repository(new File("."));
    String[] rest = Arrays.copyOfRange(args, 1, args.length);
    if (command.equals("init")) {
      Init.run(repo, rest);
      return;
    }
    if (!repo.exists()) {
      throw new OperationException("fatal: not a june repository (or any of the parent directories): .june");
    }
    switch (command) {
      case "add" -> Add.run(repo, rest);
      case "commit" -> Commit.run(repo, rest);
      case "status" -> Status.run(repo, rest);
      case "checkout" -> Checkout.run(repo, rest);
      case "restore" -> Restore.run(repo, rest);
      case "rm" -> Rm.run(repo, rest);
      case "reset" -> Reset.run(repo, rest);
      default -> {
        System.err.println("june: '" + command + "' is not a june command.");
        printUsage();
        System.exit(1);
      }
    }
  } catch (OperationException e) {
    System.err.println(e.getMessage());
    System.exit(1);
  } catch (Exception e) {
    System.err.println("fatal: " + e.getMessage());
    e.printStackTrace();
    System.exit(1);
  }
}
```

## 2. On-Disk Database and File Layout

June stores all its data and settings in a `.june` directory at the root of the workspace.

By default, June looks for or creates this directory in the current directory. If it is not found, it checks parent directories until it finds a `.june` folder. You can configure a custom location using the `JUNE_DIR` environment variable or the `june.dir` Java property.

```
[workspace_root]/
├── .june/
│   ├── HEAD
│   ├── index
│   ├── refs/
│   │   ├── heads/
│   │   │   └── main
│   │   └── tags/
│   └── objects/
│       ├── [2-char hex]/
│       │   └── [38-char hex]
```

### Branches, Tags, and the HEAD Pointer

References are text files that hold a commit hash. They are saved as plain text containing a 40-character SHA-1 string.
- The `HEAD` pointer tells June which branch or commit is currently checked out.
- Pointing to a branch uses a path (like `ref: refs/heads/main`), while checking out a specific commit or tag writes the commit hash directly.
- Pointing `HEAD` to a branch file means June does not have to change the `HEAD` file directly every time a commit is made; instead, it just updates the branch file.
- If `HEAD` starts with `ref: `, June follows the path to read the underlying branch file; otherwise, it returns the commit hash directly.

```java
public String getHeadTarget() throws IOException {
  return headFile.exists() ? Files.readString(headFile.toPath(), StandardCharsets.UTF_8).trim() : null;
}

public String getHeadCommitSha1() throws IOException {
  String head = getHeadTarget();
  if (head == null) return null;
  if (head.startsWith("ref: ")) {
    File refFile = headRefFile(head);
    return refFile.exists() ? Files.readString(refFile.toPath(), StandardCharsets.UTF_8).trim() : null;
  }
  return head;
}

public void updateHeadRefOrCommit(String sha) throws IOException {
  String head = getHeadTarget();
  if (head != null && head.startsWith("ref: ")) {
    writeWithLock(headRefFile(head), sha + "\n");
  } else {
    setHeadTarget(sha);
  }
}
```

### Staging Index Layout

- The index file (`.june/index`) lists files that are staged for the next commit.
- June uses a plain text format for the index instead of a binary cache. This makes it easy to read and debug. It uses the NUL (`\0`) character to separate parts because NUL cannot be used in filenames, and normalizes all slashes to `/`.
- In `Index.java`, June reads each line of the index file and splits it by the NUL character into exactly three parts: hash, mode, and path.
- These entries are loaded into a sorted map. This ensures that files are always listed in alphabetical order when saved.

```java
public Index(File indexFile) throws IOException {
  this.indexFile = indexFile;
  if (indexFile.exists()) {
    for (String line : Files.readAllLines(indexFile.toPath(), StandardCharsets.UTF_8)) {
      if (line.isEmpty()) continue;
      String[] parts = line.split("\0", 3);
      if (parts.length == 3) {
        entries.put(parts[2], new Entry(parts[0], parts[1], parts[2]));
      }
    }
  }
}
```

### Object Storage Layout

Objects are saved under `.june/objects/`. June groups objects into directories using the first two characters of their SHA-1 hash (for example, hash `a94a8fe5...` is saved at `.june/objects/a9/4a8fe5...`). This stops any single directory from having too many files, which can slow down the filesystem. Objects are compressed to save space and are capped at 10MB to avoid using too much memory.

## 3. Object Hashing and Serialization Formats

### The Object Header Format

Before writing an object to disk or computing its SHA-1 hash, June adds a standard header at the beginning:
`[Object Type] [Payload Length in Bytes]\0[Payload Body Bytes]`

- The header specifies the object type and size.
- Using a NUL byte at the end of the header lets June read the type and size before allocating memory for the rest of the file.
- In `ObjectData.java`, the header prefix is written to a byte array, followed by the body data.

```java
public byte[] serialize() {
  byte[] header = (type + " " + data.length + "\0").getBytes(StandardCharsets.UTF_8);
  byte[] result = new byte[header.length + data.length];
  System.arraycopy(header, 0, result, 0, header.length);
  System.arraycopy(data, 0, result, header.length, data.length);
  return result;
}
```

### Commit Objects

A commit object links a directory to its parent commits and contains the author, committer, timestamp, and message. It is saved as plain text:

```text
tree [tree_sha1]
parent [parent_sha1]
author [name] <[email]> [timestamp] [timezone]
committer [name] <[email]> [timestamp] [timezone]

[commit message]
```

- Using a key-value header format makes the commit details easy to read and validate.
- Placing a blank line before the commit message separates the headers from the message body. This allows the parser to read them line-by-line easily.
- In `Commit.java`, June reads the lines one by one. When it sees a blank line, it treats everything after it as the commit message.

```java
public Commit(byte[] rawData) {
  super(ObjectTypes.COMMIT, rawData);
  String text = new String(rawData, StandardCharsets.UTF_8);
  String[] lines = text.split("\n", -1);
  String tree = null;
  List<String> parents = new ArrayList<>();
  String auth = null;
  String comm = null;
  StringBuilder msgBuf = new StringBuilder();
  boolean readingMessage = false;

  for (String line : lines) {
    if (readingMessage) {
      msgBuf.append(line).append("\n");
    } else if (line.isEmpty()) {
      readingMessage = true;
    } else if (line.startsWith("tree ")) {
      tree = line.substring(5).trim();
    } else if (line.startsWith("parent ")) {
      parents.add(line.substring(7).trim());
    } else if (line.startsWith("author ")) {
      auth = line.substring(7).trim();
    } else if (line.startsWith("committer ")) {
      comm = line.substring(10).trim();
    }
  }
  this.treeSha1 = tree;
  this.parentSha1s = parents;
  this.author = auth;
  this.committer = comm != null ? comm : auth;
  String msg = msgBuf.toString();
  this.message = msg.endsWith("\n") ? msg.substring(0, msg.length() - 1) : msg;
}
```

### Tree Objects

A tree object represents a directory. It lists file modes, file names, and their SHA-1 hashes. Trees are saved as binary data:
`[Octal File Mode] [Entry Name]\0[20-Byte Binary SHA-1]`

```java
private static byte[] serialize(List<Entry> entries) {
  Collections.sort(entries);
  java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
  for (Entry e : entries) {
    out.writeBytes((e.mode + " " + e.name + "\0").getBytes(StandardCharsets.UTF_8));
    out.writeBytes(Sha1.fromHex(e.sha1));
  }
  return out.toByteArray();
}
```

#### Directory Sorting Logic

- When sorting files and directories, directories are grouped with files that share the same prefix.
- Adding a virtual slash (`/`) to directory names during sorting keeps the sorting consistent.
- This is done in the `compareTo` method of the `Entry` record, which compares directory names with a `/` suffix.

```java
public record Entry(String mode, String name, String sha1) implements Comparable<Entry> {
  @Override
  public int compareTo(Entry o) {
    String a = name + (mode.equals(Modes.TREE) ? "/" : "");
    String b = o.name + (o.mode.equals(Modes.TREE) ? "/" : "");
    return a.compareTo(b);
  }
}
```

#### Binary Parser Logic

- Tree objects are saved as binary files to save space.
- June parses the raw bytes directly using offsets to find spaces and NUL characters. It does not convert the binary data to a string, as that would corrupt the binary hashes.
- The parser loops through the binary data, scanning for spaces to find the mode, scanning for NUL characters to find the path name, and reading the next 20 bytes for the hash.

```java
private static List<Entry> parseEntries(byte[] data) {
  List<Entry> list = new ArrayList<>();
  int i = 0;
  while (i < data.length) {
    int sp = indexOf(data, (byte) ' ', i);
    if (sp == -1) break;
    String mode = new String(data, i, sp - i, StandardCharsets.UTF_8);

    int nul = indexOf(data, (byte) 0, sp + 1);
    if (nul == -1) break;
    String name = new String(data, sp + 1, nul - sp - 1, StandardCharsets.UTF_8);

    if (nul + 21 > data.length) {
      throw new IllegalArgumentException("Malformed tree: truncated SHA-1");
    }
    byte[] hash = new byte[20];
    System.arraycopy(data, nul + 1, hash, 0, 20);

    list.add(new Entry(mode, name, Sha1.toHex(hash)));
    i = nul + 21;
  }
  return list;
}
```

## 4. Transaction Safety and File Locking

June locks files to stop multiple commands from modifying the same file at the same time and corrupting it.

- If more than one command modifies the index or a branch pointer at the same time, the files can become corrupt.
- To prevent this, June writes changes to a temporary file ending in `.lock`. Once writing is finished, it renames the lock file to replace the original. This ensures the file is never left half-written.
- Creating the lock file is atomic, which prevents two processes from grabbing the lock at the same time.
- Before writing to files like the index or branch references, June tries to create a lock file (like `index.lock` or `main.lock`).
- If the lock file already exists, June checks how old it is. If it is older than 5 minutes, June deletes the stale lock and tries to create a new one.

```java
static void acquireOrBreak(File lock) throws IOException {
  File parent = lock.getParentFile();
  if (parent != null) {
    parent.mkdirs();
  }
  if (lock.createNewFile()) {
    return;
  }
  if (isStale(lock)) {
    lock.delete();
    if (lock.createNewFile()) {
      return;
    }
  }
  throw new OperationException(
      "Unable to create " + lock.getName() + ": another june process is running.");
}
```

## 5. System Algorithms and Logic

### The Myers Diff Algorithm (`XDiff.java`)

June compares text files using the Myers Diff algorithm. It takes two lists of text lines and finds the shortest path of changes between them.

- The Myers algorithm finds the minimum number of additions and deletions needed to change one file into another.
- It produces clean diffs with low memory usage by searching along diagonal paths.
- The algorithm searches through different edit distances, finding the furthest path for each step and saving the history:

```java
for (d = 0; d <= max; d++) {
  int[] vClone = v.clone();
  for (int k = -d; k <= d; k += 2) {
    boolean down = (k == -d || (k != d && vClone[max + k - 1] < vClone[max + k + 1]));
    int kPrev = down ? k + 1 : k - 1;

    int xStart = vClone[max + kPrev];
    int x = down ? xStart : xStart + 1;
    int y = x - k;

    while (x < n && y < m && originalLines.get(x).equals(newLines.get(y))) {
      x++;
      y++;
    }
    v[max + k] = x;
    if (x >= n && y >= m) {
      found = true;
      break;
    }
  }
  history.add(v.clone());
  if (found) break;
}
```
- Once the path is found, June walks backward through the saved history to identify additions, deletions, and unchanged lines:

```java
for (int step = d; step >= 1; step--) {
  int k = x - y;
  int[] vPrev = history.get(step - 1);
  boolean down = (k == -step || (k != step && vPrev[max + k - 1] < vPrev[max + k + 1]));
  int kPrev = down ? k + 1 : k - 1;

  int xPrev = vPrev[max + kPrev];
  int xTrans = down ? xPrev : xPrev + 1;

  while (x > xTrans) {
    x--; y--;
    ops.add(new DiffOp(' ', x + 1, y + 1, originalLines.get(x)));
  }
  if (down) {
    y--;
    ops.add(new DiffOp('+', -1, y + 1, newLines.get(y)));
  } else {
    x--;
    ops.add(new DiffOp('-', x + 1, -1, originalLines.get(x)));
  }
  x = xPrev;
  y = xPrev - kPrev;
}
```
- These changes are grouped into hunks with 3 lines of unchanged text around them. If two changes are close to each other, they are merged into a single hunk.

### Glob Patterns and Ignore Rules (`IgnoreRules.java`)

June reads `.juneignore` files to ignore specific files and directories (like build folders or temporary files).

- Java's standard string matching uses regular expressions.
- Converting glob patterns (like `*.log`) into regular expressions allows June to reuse Java's fast built-in matcher instead of writing a custom parser.
- June translates glob wildcards to Java regular expressions:
  * Double asterisks `**` match directories recursively (`.*`).
  * Single asterisks `*` match characters only within a single directory segment (`[^/]*`).
  * Question marks `?` match any single character except a path separator (`[^/]`).

```java
private static String globToRegex(String glob) {
  StringBuilder sb = new StringBuilder("^");
  for (int i = 0; i < glob.length(); i++) {
    char c = glob.charAt(i);
    if (c == '*') {
      if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
        sb.append(".*");
        i++;
      } else {
        sb.append("[^/]*");
      }
    } else if (c == '?') {
      sb.append("[^/]");
    } else if ("\\.[]{}()+-^$|".indexOf(c) != -1) {
      sb.append("\\").append(c);
    } else {
      sb.append(c);
    }
  }
  return sb.append("$").toString();
}
```
- Pattern rules starting with `!` include files instead of ignoring them. Patterns anchored at the root directory must match from the top level, while other patterns match any segment of the file path.

### Conflict Detection, Workspace Sync, and Merging (`Helper.java` and `Merge.java`)

Before modifying workspace files during checkout, reset, or merge, June checks for conflicts.

- This check ensures that your unstaged changes or untracked files are not accidentally overwritten.
- June compares the target commit, the staging index, and the files on disk.

```java
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
  // If local or untracked is not empty, throws an OperationException listing files
}
```
If there are no conflicts, June updates the workspace by deleting files that are not in the target commit, writing the new files (including links and executable files), and updating the index.

#### Ancestry Traversal (BFS Search)

Checking if a commit is an ancestor of another commit is required to validate merges and branch deletions. This check determines if a merge can be fast-forwarded or if a branch is safe to delete.

In `Helper.isAncestor()`, June searches the commit history graph using a queue and a visited set to find ancestors safely:

```java
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
    try {
      ObjectData obj = repo.read(sha);
      if (obj instanceof Commit commit) {
        for (String parent : commit.getParentSha1s()) {
          if (!visited.contains(parent)) {
            visited.add(parent);
            queue.add(parent);
          }
        }
      }
    } catch (Exception ignored) {
    }
  }
  return false;
}
```

#### Fast-Forward Merge Execution

June only supports fast-forward merges, where the active branch pointer is advanced directly to the target commit pointer if the current branch commit is a direct ancestor of the target.

In `june.lib.Merge.merge()`, June performs the following logical checks and operations:
1. Resolves the target string input to a target commit SHA-1 by looking up branch files under `.june/refs/heads/`, tag files under `.june/refs/tags/`, or falling back to a short SHA-1 resolution.
2. If the current branch commit hash matches the target commit, or if the target commit is already an ancestor of the current branch, it skips action and returns `"Already up to date."`.
3. If the current branch commit is an ancestor of the target commit, it initiates a fast-forward:
   - Collects all files associated with the target commit's root tree directory.
   - Performs working-tree conflict verification to ensure unstaged/untracked changes are not overwritten.
   - Writes target files to the workspace and updates the staging index state.
   - Updates the active branch reference pointer to the target commit SHA-1 and returns `"Fast-forward"`.
4. If neither commit is an ancestor of the other, June throws an exception stating that non-fast-forward merges are not supported.

## 6. System Operations and Low-Level Behaviors

This section explains how June handles specific situations and settings.

### 1. User Identity Resolution

- June uses a series of fallbacks to find the username and email for commits.
- This ensures every commit has a valid author name and email, even if they are not configured.
- When creating a commit, June resolves the name and email by checking:
  1. The local config file (`user.name` and `user.email`).
  2. The system environment variable `USER`.
  3. The Java system property `user.name` (defaults to `"June User"` if not set).
  4. For the fallback email, June converts the name to lowercase, removes spaces, and adds `@localhost`.

### 2. Binary File Identification

- It is important to detect if a file contains binary data (like images or archives) rather than plain text.
- Running text comparison algorithms on binary files is slow, uses too much memory, and prints unreadable text. Checking a small part of the file is fast and prevents this.
- In `Helper.isBinary()`, June scans the first 8,000 bytes of the file. If it finds a NUL (`0`) byte, the file is treated as binary, and June prints `Binary files differ` instead of a line-by-line diff:

```java
public static boolean isBinary(byte[] data) {
  for (int i = 0; i < Math.min(data.length, 8000); i++) {
    if (data[i] == 0) {
      return true;
    }
  }
  return false;
}
```

### 3. Short SHA-1 Lookup and Ambiguity Resolution

- Finding a full 40-character hash from a short prefix (like `a94a8f`) makes June easier to use.
- Typing the full hash is tedious, but a short prefix must be checked carefully to avoid matching more than one file.
- In `Helper.resolveShortSha1()`, June requires the prefix to be at least 4 characters long. It searches the matching objects directory for files that start with the prefix. If no match is found, it returns `null`. If more than one file matches, it throws an error stating that the short hash is ambiguous.

```java
public static String resolveShortSha1(File repoDir, String shortSha1) {
  if (shortSha1.length() == 40) {
    return shortSha1;
  }
  if (shortSha1.length() < 4) {
    throw new OperationException("error: short SHA-1 must be at least 4 characters");
  }
  File subDir = new File(new File(repoDir, "objects"), shortSha1.substring(0, 2));
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
```

### 4. Status Path Collapsing

- Grouping untracked files in the same directory under a single folder name keeps the status output clean.
- If you have an untracked directory containing many files, listing all of them would clutter the console.
- In `june.lib.Status.status()`, if a folder is untracked and none of its files are tracked, June lists only the folder path (e.g., `dir/`) instead of listing every file inside it.

### 5. Platform-Independent Link and Permission Mapping

- Saving and restoring file permissions (like executable status) and symbolic links is required to support different operating systems.
- Links and executable settings are important for scripts and builds, but operating systems handle them differently.
- June uses standard Java features in `Helper.java`. `Helper.entryMode()` checks if a file is a symbolic link to apply the `120000` mode, and checks if it is executable to apply the `100755` mode.
- The destination of a symbolic link is read as a string and saved as the file content.
- During checkout, if a file mode is `120000`, June recreates the symbolic link. If the mode is `100755`, it writes the file and marks it as executable.

## 7. Dependencies & Build Requirements

June does not use any external packages. It is written in pure Java and only uses standard library packages:

### Required Standard Packages

* `java.io`: Handles file and directory reading and writing.
* `java.nio`: Handles path resolution, symbolic links, and file movements.
* `java.security`: Provides the SHA-1 hashing classes.
* `java.util`: Provides lists, maps, and property utilities.
* `java.util.zip`: Handles file compression.
* `java.time`: Handles date and time for commits.

## 8. System Implementation Sequence and Class Dependency Reference

This section outlines how each class is built and how they work together.

### 1. Hashing Library & Serialization Models (`Sha1.java`, `ObjectData.java`, `Tree.java`, and `Commit.java`)

* **Role**: Establishes byte-level data serialization, SHA-1 hashing, and type-specific data mapping. `ObjectData.java` serves as the base model, while `Tree.java` (handling tree entry sorting and binary parsing) and `Commit.java` (handling parent reference list parsing and commit message extraction) inherit from it.
* **Integrations**: Receives raw byte payloads, formats standard metadata headers, and performs hex conversions used across all storage and reference systems.

### 2. Object Storage (`ObjectStore.java` and `ObjectTypes.java`)

* **Role**: Implements zlib-compressed persistence, file reads/writes, and streaming access to the repository object database.
* **Integrations**: Writes serialized object bytes to partitioned subdirectories based on computed SHA-1 hashes, and decompresses payload inputs up to a 10MB memory ceiling.

### 3. File Lock & Staging State (implemented in `Index.java`)

* **Role**: Protects metadata records from concurrent write corruption using atomic lock verification (via an internal package-private `FileLock` class) and parses the plain-text NUL-separated staging database.
* **Integrations**: Manages staging operations for adding and removing workspace paths, sorting entries alphabetically via an internal tree map structure.

### 4. Repository Metadata Model (`Repository.java` and `Modes.java`)

* **Role**: Resolves local repository paths, updates active HEAD pointer states (symbolic and detached), and manages local properties config storage.
* **Integrations**: Orchestrates interactions between the staging index, object storage, and ref sub-systems during checkouts and commits.

### 5. Ignore Rules and Path Traversal (`IgnoreRules.java` and `Helper.java`)

* **Role**: Parses pattern lists, compiles glob rules to regular expressions, searches workspaces recursively, and identifies local conflicts.
* **Integrations**: Filters files during index staging operations and prevents active checkouts or merges from overwriting modified workspace files.

### 6. Line Diff Algorithm (`XDiff.java` and `Modes.java`)

* **Role**: Runs Myers diagonal searches, tracks edit history coordinates, and formats line differences into unified hunks.
* **Integrations**: Computes changes between the staging index, HEAD commits, and physical workspace files for display by the diff operations.

### 7. Feature Command Controllers (`june.lib.*`)

* **Role**: Houses the logical workflows for all version control commands, managing state changes across the index, object store, and ref paths.
* **Integrations**: Exposes structured domain results and programmatic exceptions that downstream callers utilize.

### 8. CLI commands (App.java and default package commands)

* **Role**: Parses command arguments, maps parameters, triggers library actions, and outputs text messages to standard output or error.
* **Integrations**: Acts as the user interface adapter that programmatically wraps library operations.

## 9. Programmatic API Integration and Thread Safety

Because the core library is separate from the command-line parser, June can be used directly inside other programs (like a graphical interface, web server, or IDE plugin). This allows you to manage repositories directly in Java without running terminal commands.

### Thread Safety

When using June in a multi-threaded program:
- Creating a `Repository` using `new Repository(workspaceDir, customMetadataDir)` keeps all files, index states, and locks isolated to that specific instance.
- This avoids using shared settings and ensures that multiple threads can work on different repositories at the same time without conflicts.

### Programmatic Example

Below is a Java example showing how to initialize a repository, stage files, check status, and commit changes using the library:

```java
import june.Repository;
import june.lib.Commit.CommitResult;
import june.lib.Status.StatusResult;
import java.io.File;
import java.util.List;

public class ProgrammaticExample {
    public static void main(String[] args) throws Exception {
        File workspace = new File("/path/to/my/project");
        File customMetadataDir = new File("/path/to/custom/metadata/parent");

        // 1. Instantiate the Repository (fully thread-safe, isolated per instance)
        Repository repo = new Repository(workspace, customMetadataDir);

        // 2. Initialize the repository directory structures
        repo.init();
        System.out.println("Repository initialized!");

        // 3. Stage changes (equivalent to `june add`)
        repo.add(List.of("src/Main.java", "README.md"));
        System.out.println("Files staged successfully.");

        // 4. Query current staging status (equivalent to `june status`)
        StatusResult status = repo.status();
        System.out.println("Active Branch: " + status.branch());
        System.out.println("Staged files: " + status.staged());
        System.out.println("Unstaged files: " + status.unstaged());
        System.out.println("Untracked files: " + status.untracked());

        // 5. Commit staged changes (equivalent to `june commit`)
        CommitResult commit = repo.commit("feat: initial programmatic commit", false);
        System.out.println("Created Commit: " + commit.commitSha1());

        // 6. Switch version context (equivalent to `june checkout`)
        repo.checkout("main");
    }
}
```

---

# June

June commands parse user inputs, check arguments, and run the library code.

## 1. Command Syntax and Router Dispatch

The main entry point is the `App` class in the `cmd` directory. When you run a command:
1. `App.main` gets the command name from the first argument (`args[0]`).
2. It splits the rest of the arguments to pass to the specific command handler.
3. It sets up the repository controller: `new Repository(new File("."))`. (If `JUNE_DIR` or `june.dir` is set, it uses that path instead of the current directory).
4. It checks if the repository exists (except for the `init` command).
5. It runs the correct command handler.

If a command fails, it throws a `june.OperationException`. `App.main` catches this error, prints the message, and exits with a code of 1.

## 2. Command Specifications

### 1. `init`

* **Syntax**: `june init`
- You must initialize the repository before running other commands.
- Initializing the repository creates the required directories. If the `HEAD` reference is missing, June points it to the `main` branch.
- In `Init`, June calls `repo.init()` to create `.june/`, `.june/objects/`, `.june/refs/heads/`, and `.june/refs/tags/`.

### 2. `add`

* **Syntax**: `june add <file>...`
- Staging changed files is required to prepare a snapshot of the workspace for a future commit.
- The command checks if the user provided at least one path target. It then resolves each argument to add regular files, walk directories recursively, or remove entries if files have been deleted on disk.
- In `Add`, June validates the arguments length. If empty, it throws a user-facing operation error; otherwise, it converts the array to a list and routes execution to the repository staging workflow: `repo.add()`.

```java
public static void run(Repository repo, String[] args) throws IOException {
  if (args.length == 0) {
    throw new OperationException("nothing specified, nothing added");
  }
  repo.add(Arrays.asList(args));
}
```
- Validating the arguments length before invoking the staging workflow prevents empty stages from running.
- Passing the arguments as a standard Java list ensures compatibility with collection walking methods.

### 3. `commit`

* **Syntax**: `june commit [-a] -m <message>` or `june commit -am <message>`
- Recording modifications in a commit permanently saves the staged state to the commit log.
- Commits require a non-blank message parameter and optional auto-staging flags to automatically include modified tracked files.
- In `june.cmd.Commit`, June iterates through argument inputs, toggling auto-stage state variables when encountering `-a` or `-am` flags, and extracting the trailing message parameter when encountering `-m` or `-am`.

```java
public static void run(Repository repo, String[] args) throws IOException {
  String msg = null;
  boolean auto = false;
  for (int i = 0; i < args.length; i++) {
    switch (args[i]) {
      case "-a" -> auto = true;
      case "-m", "-am" -> {
        if (args[i].equals("-am")) {
          auto = true;
        }
        if (i + 1 < args.length) {
          msg = args[++i];
        }
      }
      default -> {}
    }
  }
  if (msg == null) {
    throw new OperationException("fatal: no commit message specified (use -m \"message\")");
  }
  String result = repo.commit(msg, auto).message();
  if (!result.isEmpty()) {
    System.out.println(result);
  }
}
```
- Parsing the arguments with a switch loop allows options like `-a` and `-m` to be specified in any order.
- Splicing the next argument index immediately when `-m` or `-am` is detected safely captures multi-word commit messages enclosed in quotes.
- The command throws a clean operation exception if no message is found, preventing the creation of empty-labeled commits.

### 4. `status`

* **Syntax**: `june status`
- Displaying unstaged, staged, and untracked changes helps users verify modifications before recording commits.
- Grouping status results into clear categories and printing them in color makes changes easy to scan in the terminal.
- In `june.cmd.Status`, June receives the categorized status model from the library layer, prints the branch name, and writes out staged files in green, and unstaged or untracked paths in red.

```java
private static final String ANSI_RESET = "\u001B[0m";
private static final String ANSI_GREEN = "\u001B[32m";
private static final String ANSI_RED = "\u001B[31m";

public static void run(Repository repo, String[] args) throws IOException {
  formatStatus(repo.status());
}

private static void formatStatus(june.lib.Status.StatusResult sr) {
  System.out.println(sr.branch());
  printSection("Changes to be committed:",
      "  (use \"june restore --staged <file>...\" to unstage)",
      formatChanges(sr.staged()), ANSI_GREEN);
  printSection("Changes not staged for commit:",
      "  (use \"june add <file>...\" to update what will be committed)\n"
          + "  (use \"june restore <file>...\" to discard changes in working directory)",
      formatChanges(sr.unstaged()), ANSI_RED);
  printSection("Untracked files:",
      "  (use \"june add <file>...\" to include in what will be committed)",
      sr.untracked().stream().map(s -> "  " + s).toList(), ANSI_RED);
  if (sr.staged().isEmpty() && sr.unstaged().isEmpty() && sr.untracked().isEmpty()) {
    System.out.println("nothing to commit, working tree clean");
  }
}
```
- Defining ANSI color escape strings as static variables keeps the terminal formatting logic clear.
- Re-routing status lists into a helper printer checks list boundaries, ensuring empty sections are not printed.
- Writing the ANSI reset code at the end of each print section prevents color bleeding into subsequent terminal outputs.

### 6. `checkout`

* **Syntax**: `june checkout <branch-name> | <commit-or-tag>`
- Checkout updates the workspace to match a target branch, tag, or commit.
- Switching versions requires validating targets, checking for conflicts, and updating files on disk.
- In `june.cmd.Checkout`, June passes the target argument string to the checkout logic to resolve hashes and verify commit payloads.

### 7. `restore`

* **Syntax**: `june restore [--staged] <file>...`
- Restore discards local modifications in files or unstages index entries.
- The command routes paths and staging flags to the restoration utilities, expanding directories as needed.
- In `june.cmd.Restore`, June parses the `--staged` option to choose between index modifications or workspace content resets.

### 9. `rm`

* **Syntax**: `june rm [--cached] <file>...`
- Removing files untracks them from the index and optionally deletes them from the disk.
- The command validates that target paths are currently tracked before executing deletions.
- In `june.cmd.Rm`, June parses the `--cached` option to decide whether to preserve the physical file on disk.

### 13. `reset`

* **Syntax**: `june reset --hard [<commit-sha1>]`
- Hard resetting updates the workspace, index, and branch pointers to match a target commit.
- The command requires a `--hard` flag to confirm that local modifications will be overwritten.
- In `june.cmd.Reset`, June validates the hard reset flag and resolves the target commit hash.

## 3. Class Design of the commands

Each command has a dedicated wrapper class under `cmd/` to separate argument parsing from logical execution:
* **Add.java**: Parses path lists and calls `repo.add()`.
* **Checkout.java**: Verifies target arguments and calls checkout.
* **Commit.java**: Parses `-am`, `-a`, and `-m` commit options.
* **Init.java**: Calls the repository initialization method directly.
* **Reset.java**: Validates `--hard` flags and commit targets.
* **Restore.java**: Checks for `--staged` flags and path arrays.
* **Rm.java**: Extracts path specifications and `--cached` flags.
* **Status.java**: Prints the results of the repository status call.

## 4. Programmatic API Mapping

In addition to command-line execution, the library exposes all features via direct method calls on `june.Repository` or the logical classes inside `june.lib`. This makes June fully usable as a standalone programmatic library:

| VCS Concept | Library API Invocation | CLI Command Shadowed |
| :--- | :--- | :--- |
| **Initialize** | `repo.init()` | `Init.java` |
| **Stage Changes** | `repo.add(List<String> paths)` | `Add.java` |
| **Record Commit** | `repo.commit(String message, boolean autoStage)` | `Commit.java` |
| **Check Status** | `repo.status()` | `Status.java` |
| **Checkout Target** | `repo.checkout(String target)` | `Checkout.java` |
| **Restore State** | `repo.restore(List<String> paths, boolean staged)` | `Restore.java` |
| **Untrack Files** | `repo.rm(List<String> paths, boolean cached)` | `Rm.java` |
| **Reset State** | `repo.reset(String target)` | `Reset.java` |