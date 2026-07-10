# June Architecture & Specification Manual

This document provides a comprehensive guide to June's system architecture, design decisions, and command-line specifications.

---

# Architecture

June is structured into independent storage, library, and command wrapper layers to separate physical database serialization, version control workflows, and user interactions.

## 1. System Design and Key Layers

June is written in Java (JDK 17+) and has no external dependencies. The codebase is separated into three layers to keep storage logic, feature logic, and the user interface independent.

### Why split the code this way?

If the command-line interface code is mixed with the storage code, you can't reuse the storage system in other apps (like a GUI or a web service). Decoupling them allows you to run the VCS logic programmatically, write automated unit tests without mocking terminal streams, and change the command names without breaking the storage backend. The command wrapper is just a thin layer on top of the library.

### The three layers:

1. **The Storage and Utility Library (`june`)**: This layer handles repository paths, reads and writes compressed objects, updates the staging index, compiles ignore rules, calculates diffs, and locks files to prevent write conflicts.
2. **The Feature Library (`june.lib`)**: This layer implements the actual features (like staging, committing, checking out, and merging). It coordinates the storage layer and the workspace but does not deal with command parsing or flags.
3. **The CLI Wrapper (App and command classes)**: This layer parses command-line arguments, checks user inputs, prints formatted messages to the console, and exits with a non-zero code if something goes wrong.

### Command Dispatcher (`App.java`)

- June routes terminal inputs through a single entry point in `App.java` (located in the `cmd/` directory).
- Decoupling argument routing from the internals ensures that programmatic invocations do not have to perform user command validations. The entrypoint slices the main argument array so that individual handlers only receive parameters relevant to them.
- It instantiates the repository context path, checks for repository existence (except for the init command), and dispatches execution to the corresponding handler.

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
      case "branch" -> Branch.run(repo, rest);
      case "checkout" -> Checkout.run(repo, rest);
      case "restore" -> Restore.run(repo, rest);
      case "diff" -> Diff.run(repo, rest);
      case "rm" -> Rm.run(repo, rest);
      case "mv" -> Mv.run(repo, rest);
      case "tag" -> Tag.run(repo, rest);
      case "merge" -> Merge.run(repo, rest);
      case "config" -> Config.run(repo, rest);
      case "reset" -> Reset.run(repo, rest);
      case "cat-file" -> CatFile.run(repo, rest);
      case "log" -> Log.run(repo, rest);
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

June stores all its metadata and compressed data in a `.june` directory at the root of the workspace.

While the name `.june` is fixed, the parent directory location where it resides can be configured using either the `JUNE_DIR` environment variable or the JVM system property `june.dir`. If neither is configured, June defaults to resolving or creating the `.june/` directory in the current working directory, or walking up parent directories until an existing `.june/` folder is located.

```
[workspace_root]/
├── .june/
│   ├── HEAD
│   ├── config
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

References are human-readable files that point to a commit hash. They are saved as plain text containing a 40-character hex SHA-1 string and a newline (`\n`).
- The active branch pointer (`HEAD`) tells June which branch or commit is currently checked out.
- Pointing to a branch uses a symbolic path format (like `ref: refs/heads/main\n`), while checkout of a direct commit or tag writes the commit hash directly to create a detached head state.
- Using symbolic references avoids having to modify the HEAD file directly during every commit transaction. By letting HEAD point to a branch file, June only needs to update the branch ref file when a new commit advances history.
- In `Repository.java`, June reads the reference target. If it starts with `ref: `, June follows the path to read the underlying branch file; otherwise, it returns the HEAD string as the direct commit hash.

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

- The index file (`.june/index`) collects workspace modifications to prepare them for commits.
- June uses a simple flat text format instead of a binary cache. This makes the index easy to inspect and debug. Slashes are normalized to `/` to avoid platform incompatibilities, and NUL (`\0`) is used as a delimiter because it is an invalid character in filenames.
- In `Index.java`, June reads the index file line-by-line and splits each line by the NUL character into exactly 3 parts: hash, mode, and path.
- These are loaded into a `TreeMap` sorted by relative path. This ensures that tree objects built from the index are always serialized in deterministic alphabetical order.

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

### Configuration Management

- Local settings are saved in `.june/config` to allow users to specify a local username and email for commits within a specific repository.
- Using the standard properties format is clean because the JDK already has a built-in properties loader, which avoids the need to write custom parsing code or add external library dependencies.
- In `Config.java`, June loads properties from `.june/config` and returns the stored value for the requested key. If the repository does not exist or the key is missing, it returns `null`.

```java
public static String get(Repository repo, String key) {
  if (!repo.exists()) {
    return null;
  }
  java.util.Properties props = new java.util.Properties();
  File configFile = new File(repo.getRepoDir(), "config");
  if (configFile.exists()) {
    try (java.io.Reader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
      props.load(reader);
    } catch (IOException e) {
      throw new OperationException("fatal: could not read config: " + e.getMessage());
    }
  }
  return props.getProperty(key);
}
```

### Object Storage Layout

Objects live under `.june/objects/`. June partitions objects using a two-character subdirectory prefix based on the object's SHA-1 hash (e.g. hash `a94a8fe5...` is saved at `.june/objects/a9/4a8fe5...`). This partitioning avoids OS filesystem performance issues that occur when a single directory contains too many files. Objects are compressed using zlib (`DeflaterOutputStream` and `InflaterInputStream`) and are capped at 10MB in memory to prevent memory exhaustion.

## 3. Object Hashing and Serialization Formats

### The Object Header Format

Before writing an object to disk or computing its SHA-1 hash, June prepends a standard header:
`[Object Type] [Payload Length in Bytes]\0[Payload Body Bytes]`

- The header structure separates object types and sizing directly at the start of the payload.
- Prepending this metadata with a NUL byte boundary ensures the reader can parse the type and content length before allocating memory buffers for the body bytes.
- In `ObjectData.java`, the header prefix is written to a byte array, followed by the raw body data.

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

A commit object links a directory tree to its parent commits and contains author, committer, timestamp, and message metadata. Its payload is formatted as plain text:

```text
tree [tree_sha1]
parent [parent_sha1]
author [name] <[email]> [timestamp] [timezone]
committer [name] <[email]> [timestamp] [timezone]

[commit message]
```

- Using a simple key-value header format simplifies parsing and metadata validation.
- Placing a blank line boundary before the commit message body separates structured variables from unstructured, free-form text, letting the parser read them line-by-line without complex tokenizers.
- In `Commit.java`, June reads lines sequentially. When it encounters a blank line, it treats all subsequent lines as the commit message.

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

A tree object represents a directory listing, mapping file modes and names to their respective SHA-1 hashes. Trees are serialized as binary data payloads containing repeating entries:
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

- When sorting files and directories together, directories should be grouped relative to files that share the same prefix.
- Appending a virtual trailing slash (`/`) to directory names during sorting forces them to sort consistently, avoiding duplicate entries or inconsistent trees.
- This is implemented via a custom `compareTo` method in the `Entry` record where directories are compared with a `/` suffix.

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

- Tree objects are saved as binary structures to save space.
- June parses the byte array directly using offsets to find spaces and NUL characters rather than translating the entire binary object to a string, which would corrupt binary hashes.
- The parser iterates through the binary payload, scanning for space (extracting the mode), scanning for NUL (extracting the path name), and reading the next 20 bytes for the hash.

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

June uses atomic filesystem lock files to ensure operations do not corrupt index or reference states during concurrent modifications.

- If multiple processes modify the index or a branch pointer at the same time, the file could end up corrupted.
- Writing modifications to a temporary lock file and then renaming it atomically ensures that the target file is never left in a partially written state.
- `File.createNewFile()` is atomic under the hood, ensuring a lock is obtained without race conditions.
- Before writing to critical paths (like `.june/index` or references under `.june/refs/`), June attempts to create a lock file with a `.lock` extension (e.g. `index.lock` or `refs/heads/main.lock`).
- If the file creation fails because a lock exists, it checks the lock timestamp. If it is older than 5 minutes (customizable via `june.lock.staleMillis`), the active process deletes the stale lock file and attempts to recreate it.

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

June calculates changes between text versions using the Myers Diff algorithm. It processes two lists of string lines, searching for the shortest edit script (SES) in a diagonal grid.

- The Myers algorithm finds the minimum number of insertions and deletions needed to transform one file into another.
- It generates clean diffs with low memory overhead by searching diagonal paths.
- The search runs through progressive edit distances $d$ from $0$ to $N+M$ along diagonal paths defined by $k = x - y$. At each step, it selects the transition that reaches furthest in the $x$ direction, matches identical lines along that diagonal, and saves the history:

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
- After finding the end coordinate, June walks backward through the `history` states to trace the edit path, identifying additions, deletions, and unchanged lines:

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
- These operations are grouped into unified diff hunks with 3 lines of unchanged context (`CONTEXT_LINES = 3`). If changes are separated by less than 6 lines of context ($2 \times CONTEXT\_LINES$), they are consolidated into a single hunk.

### Glob Patterns and Ignore Rules (`IgnoreRules.java`)

June parses `.juneignore` files to exclude files like build directories or local caches.

- Java's standard string matching (`String.matches`) uses regex.
- Translating glob patterns into regular expressions allows us to reuse the JDK's fast regex implementation instead of writing a custom glob parser.
- June translates wildcards to standard Java regular expressions:
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
- Negations (`!`) include matched files. Root-anchored patterns (starting with `/` or containing a slash but not ending with one) must match starting from the root directory. Other patterns split the path by `/` and match each segment individually.

### Conflict Detection, Workspace Sync, and Merging (`Helper.java` and `Merge.java`)

Before modifying workspace files during checkout, reset, or merge, June checks for conflicts.

- This validation step ensures that local unstaged changes or untracked workspace files are not overwritten by checkout or merge.
- June compares the target tree, index entries, and physical files on disk:

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
Once conflict checks pass, June updates the workspace by deleting files missing from the target commit, writing target commit files (recreating symbolic links or regular executable files), and updating the staging index.

#### Ancestry Traversal (BFS Search)

Verifying if a specific commit exists within the direct ancestry tree of another commit is necessary to validate branch deletions and merge actions. Ancestry validation determines if a merge can be fast-forwarded or if a branch can be safely deleted.

In `Helper.isAncestor()`, June performs a BFS using a queue and a visited set to traverse history graphs safely:

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

This section explains the low-level routines, edge cases, and design choices implemented inside June.

### 1. User Identity Resolution

- Determining username and email metadata for commit objects requires a robust fallback chain.
- This ensures June can always record a valid author signature for commits, even if the user has not configured their name or email details.
- When creating a commit in `june.lib.Commit`, June resolves the username and email by checking sources in the following order:
  1. Local repository config properties (`user.name` and `user.email`).
  2. The system environment variable `USER`.
  3. The Java system property `user.name` (defaults to `"June User"` if null).
  4. For the email fallback, it sanitizes the resolved name (converting to lowercase and stripping all whitespaces) and appends `@localhost`.

### 2. Binary File Identification

- Detecting if a file contains binary content rather than plain text is critical.
- Performing line-based diff algorithms on binary assets (like images or zip archives) is slow, consumes large amounts of memory, and produces garbled console output. Scanning a small block of bytes is fast and prevents these issues.
- In `Helper.isBinary(byte[] data)`, June scans up to the first 8,000 bytes of the file. If it encounters a NUL (`0`) byte, the file is treated as binary, and diff calculators output `Binary files differ` instead of a unified diff:

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

- Resolving abbreviated commit hashes (like `a94a8f`) to their full 40-character target hashes improves usability.
- Typing out a full 40-character hash is tedious. Letting users type short prefixes is standard, but the lookup logic must validate the prefix length to avoid collision risks and fail gracefully if a prefix matches multiple objects.
- In `Helper.resolveShortSha1()`, June enforces that the search prefix must be at least 4 characters long. It looks in the `.june/objects/[first-two-chars]` directory for any files that begin with the remaining characters of the prefix. If no match is found, it returns null. If more than one file matches, it throws a `june.OperationException` indicating the short SHA-1 is ambiguous.

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

- Collapsing multiple untracked files in the same directory into a single folder indicator keeps command output readable.
- If a user has an untracked directory containing thousands of build files, listing every single file in `june status` would clutter the console and make the output unreadable.
- In `june.lib.Status.status()`, if a workspace file is not tracked in the index, June extracts the top-level folder name (e.g. `dir/`). If no files in that folder prefix are currently tracked, status lists only the collapsed directory path rather than each individual child path.

### 5. Platform-Independent Link and Permission Mapping

- Capturing and restoring executable permission flags (`100755`) and symbolic link metadata (`120000`) is required to support cross-platform checkouts.
- Symbolic links and executable permissions are important for scripts and builds, but standard Java file attributes vary across operating systems.
- June uses standard JDK Java NIO paths in `Helper.java`. `Helper.entryMode(File file)` checks `Files.isSymbolicLink()` to apply the `120000` mode, and `file.canExecute()` to apply the `100755` mode.
- The target path of a symbolic link is read as a string and stored as the raw blob payload.
- During checkout/restore, if the entry mode is `120000`, June deletes the old path and recreates the symbolic link using `Files.createSymbolicLink()`. If the mode is `100755`, it writes the regular file and calls `dest.setExecutable(true)`.

## 7. Dependencies & Build Requirements

June has zero external software dependencies. It is written in pure Java and relies exclusively on standard JDK library packages.

### Required Standard Packages

* `java.io`: Handles file, directory, stream, and reader processes.
* `java.nio`: Provides path resolution, symbolic link utilities, and file system movements.
* `java.security`: Provides the `MessageDigest` class used for SHA-1 computations.
* `java.util`: Provides collections (`TreeMap`, `ArrayList`), formatter utilities, and configuration properties.
* `java.util.zip`: Provides `DeflaterOutputStream` and `InflaterInputStream` for zlib object compression.
* `java.time`: Manages timestamps and timezone offsets for commit records.

## 8. System Implementation Sequence and Class Dependency Guide

This section outlines the progressive construction and validation sequence for the codebase, detailing how each component integrates with neighboring systems.

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

### 8. CLI Command wrappers (App.java and default package commands)

* **Role**: Parses command arguments, maps parameters, triggers library actions, and outputs text messages to standard output or error.
* **Integrations**: Acts as the user interface adapter that programmatically wraps library operations.

## 9. Programmatic API Integration and Thread Safety

Because the repository and logical workflows are entirely decoupled from the command line parser (`cmd/`), June can be embedded directly as a dependency inside other JVM-based programs (such as GUI clients, web application servers, or IDE plugins). This allows you to manage snapshots and history programmatically in a thread-safe manner without invoking the command-line interface or spawning subprocesses.

### Thread Isolation and Instance Safety

When embedded inside a multi-threaded application (such as a web server handling concurrent repository requests):
- Instantiating a `Repository` using the overloaded constructor `new Repository(workspaceDir, customMetadataDir)` ensures that all metadata operations, index states, database storage, and lock configurations remain fully scoped to that specific repository instance.
- This avoids shared global state (like JVM system properties or environment variables) and prevents concurrent database corruption or cross-repository contamination between threads.

### Code Example: Programmatic Operations

Below is a complete Java code example showing how to initialize a repository, stage files, query status, and record a commit programmatically using only the library APIs:

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

# June Commands Reference Manual

June command endpoints parse inputs, enforce validation rules, and route arguments to the `june.lib` library.

## 1. Command Syntax and Router Dispatch

The entry point of the command interface is the `App` class in the `cmd` directory. When a user runs a command from the shell:
1. `App.main` parses the command name from the first argument (`args[0]`).
2. Slices the remaining arguments using `Arrays.copyOfRange(args, 1, args.length)` to isolate the parameters for the specific command.
3. Instantiates the repository state controller (`new Repository(new File("."))`).
   - Note: The `Repository` constructor checks for the JVM system property `june.dir` or the system environment variable `JUNE_DIR` to determine the custom parent directory for the `.june` metadata directory. If either is set, it resolves `.june` relative to that path.
4. Checks if the repository exists on disk (unless the command is `init`).
5. Dispatches execution to the corresponding command handler class directly in the `cmd` project (default package).

If a command fails because of invalid input or runtime errors, the command wrapper throws a `june.OperationException`. `App.main` catches this exception, prints the clean message to standard error, and calls `System.exit(1)`.

## 2. Command Specifications

### 1. `init`

* **Syntax**: `june init`
- Setting up a repository directory structure is necessary before running any other version control operations.
- Creating the database directories initializes a fresh tracking scope. If the HEAD reference file is missing, June writes a default pointer targeting the main branch.
- In `june.cmd.Init`, June invokes `repo.init()` which generates `.june/`, `.june/objects/`, `.june/refs/heads/`, and `.june/refs/tags/` directories.

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

### 5. `branch`

* **Syntax**: `june branch [-d | -D] [<branch-name>]`
- Branches allow users to manage independent lines of development.
- The command supports listing active branches, creating branch pointers at the current commit, or deleting branches.
- In `june.cmd.Branch`, June parses delete flags (`-d` or `-D`) to call branch deletion routines, or routes to the branch creation logic if a raw name is specified.

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

### 8. `diff`

* **Syntax**: `june diff [--cached | --staged]`
- Diff shows line-by-line differences between the workspace, index, and commit history.
- Users can view changes staged in the index or modifications still in the working directory.
- In `june.cmd.Diff`, June checks for `--cached` or `--staged` flags to select the comparison target.

### 9. `rm`

* **Syntax**: `june rm [--cached] <file>...`
- Removing files untracks them from the index and optionally deletes them from the disk.
- The command validates that target paths are currently tracked before executing deletions.
- In `june.cmd.Rm`, June parses the `--cached` option to decide whether to preserve the physical file on disk.

### 10. `mv`

* **Syntax**: `june mv <source> <destination>`
- Moving a file renames it in the workspace and updates its path key in the staging index.
- June verifies that the source path is tracked and the destination path is free.
- In `june.cmd.Mv`, June passes the parameters to move files and update the index entries.

### 11. `cat-file`

* **Syntax**: `june cat-file -p <object-sha1>`
- Inspecting database objects displays decompressed payload details for debugging.
- The command restricts access using the `-p` parameter for pretty-printing.
- In `june.cmd.CatFile`, June verifies the pretty-print flag and outputs the resolved object contents.

### 12. `config`

* **Syntax**: `june config <key> [<value>]`
- Configuring options sets local values for key variables like username and email.
- The command splits reads (with one argument) from writes (with two arguments).
- In `june.cmd.Config`, June checks the argument length to select read or write actions.

### 13. `reset`

* **Syntax**: `june reset --hard [<commit-sha1>]`
- Hard resetting updates the workspace, index, and branch pointers to match a target commit.
- The command requires a `--hard` flag to confirm that local modifications will be overwritten.
- In `june.cmd.Reset`, June validates the hard reset flag and resolves the target commit hash.

### 14. `tag`

* **Syntax**: `june tag [-d] [<tag-name>] [<commit-sha1>]`
- Tags represent permanent named references to specific commits.
- The command lists existing tags, deletes reference files, or creates new tags pointing at HEAD or a resolved commit.
- In `june.cmd.Tag`, June checks for tag deletion flags or name parameters to update tag references.

### 15. `merge`

* **Syntax**: `june merge <branch-or-tag-or-commit>`
- Merging integrates history from a branch, tag, or commit into the current HEAD.
- June supports fast-forward merges only; it will update the active branch pointer when the target is a descendant of the current commit.
- In `june.cmd.Merge`, June resolves the target against branch refs, tag refs, and short commit hashes, then validates the fast-forward condition.

### 16. `log`

* **Syntax**: `june log [--oneline] [-n <count>] [--max-count <count>]`
- The commit log displays the history of commits in reverse chronological order.
- Logging traverses parent hashes from HEAD and supports compact formatting and count limits.
- In `june.cmd.Log`, June parses options like `--oneline`, `-n`, and `--max-count` to format and filter the output list.

## 3. Class Design of the Command Wrappers

Each command has a dedicated wrapper class under `cmd/` to separate argument parsing from logical execution:
* **Add.java**: Parses path lists and calls `repo.add()`.
* **Branch.java**: Detects listing vs deletion vs creation flags.
* **CatFile.java**: Enforces `-p` parameters and prints outputs.
* **Checkout.java**: Verifies target arguments and calls checkout.
* **Commit.java**: Parses `-am`, `-a`, and `-m` commit options.
* **Config.java**: Routes reading vs writing calls to config utilities.
* **Diff.java**: Checks for `--cached` / `--staged` flags.
* **Init.java**: Calls the repository initialization method directly.
* **Log.java**: Checks for `--oneline` and `-n` limits.
* **Merge.java**: Resolves target branch names to trigger fast-forward checks.
* **Mv.java**: Verifies source and destination parameters.
* **Reset.java**: Validates `--hard` flags and commit targets.
* **Restore.java**: Checks for `--staged` flags and path arrays.
* **Rm.java**: Extracts path specifications and `--cached` flags.
* **Status.java**: Prints the results of the repository status call.
* **Tag.java**: Parses tag listing, tag creation, or tag deletion flags.

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
| **Generate Diff** | `repo.diff(boolean staged)` | `Diff.java` |
| **Untrack Files** | `repo.rm(List<String> paths, boolean cached)` | `Rm.java` |
| **Rename Files** | `repo.mv(String src, String dest)` | `Mv.java` |
| **Log History** | `repo.log(int maxCount)` | `Log.java` |
| **Inspect Objects** | `repo.catFile(String ref)` | `CatFile.java` |
| **Manage Config** | `repo.getConfig(key)` / `repo.setConfig(key, val)` | `Config.java` |
| **Reset State** | `repo.reset(String target)` | `Reset.java` |
| **Merge Ancestry** | `repo.merge(String target)` | `Merge.java` |
| **Branch Operations**| `repo.createBranch(name)` / `repo.listBranches()` / `repo.deleteBranch(name, force)` | `Branch.java` |
| **Tag Operations** | `repo.createTag(name, sha)` / `repo.listTags()` / `repo.deleteTag(name)` | `Tag.java` |