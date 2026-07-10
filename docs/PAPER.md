# June

June is a simple version control system written in Java.

## 1. System Design and Key Layers

June is written in Java and does not use any external packages. The code is split into two layers to keep the storage logic and the user interface separate.

### Why split the code this way?

Separating the command-line interface from the core storage logic keeps the codebase modular. This ensures the core logic is reusable and unaffected by changes to user commands.

### The two layers:

1. **The Storage and Utility Library (`june`)**: This layer manages repository paths, serializes and stores compressed objects, updates the staging index, locks files, manages references, compiles ignore rules, and calculates line diffs.
2. **The CLI (App and command classes)**: This layer parses command-line arguments, checks user inputs, prints formatted messages to the console, and exits with a non-zero code if something goes wrong.

### Command Dispatcher (`App.java`)

- June routes all command-line inputs through `App.java` in the `cmd/` directory.
- By keeping command routing separate, other programs can call the library directly without needing to validate user commands.
- The main entry point splits the arguments so that each command handler only gets the arguments it needs. It then sets up the repository directory path and runs the correct command handler.

```java
public static void main(String[] args) throws Exception {
  if (args.length == 0) {
    System.out.println("Usage: java App <command> [<args>]");
    System.exit(1);
  }
  String cmd = args[0];
  if (cmd.equals("init")) {
    Repository repo = new Repository(new File("."));
    repo.init();
    System.out.println("Initialized empty June repository in .june/");
    return;
  }
  System.out.println("Unknown command: " + cmd);
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

## 6. System Operations and Low-Level Behaviors

This section explains how June handles specific situations and settings.

### 1. Binary File Identification

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

### 2. Platform-Independent Link and Permission Mapping

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

# June

June commands parse user inputs, check arguments, and run the library code.

## 1. Command Syntax and Router Dispatch

The main entry point is the `App` class in the `cmd` directory. When you run a command:
1. `App.main` gets the command name from the first argument (`args[0]`).
2. It sets up the repository controller: `new Repository(new File("."))`. (If `JUNE_DIR` or `june.dir` is set, it uses that path instead of the current directory).

## 2. Command Specifications

### 1. `init`

* **Syntax**: `june init`
- Setting up a repository directory structure is necessary before running any other version control operations.
- Creating the database directories initializes a fresh tracking scope. If the HEAD reference file is missing, June writes a default pointer targeting the main branch.
- In `init`, June invokes `repo.init()` which generates `.june/`, `.june/objects/`, `.june/refs/heads/`, and `.june/refs/tags/` directories.