# June

June is a simple version control system written in Java.

## 1. System Design and Key Layers

June is written in Java and does not use any external packages. The code is split into two layers to keep the storage logic and the user interface separate.

### Why split the code this way?

Separating the command-line interface from the core storage logic keeps the codebase modular. This ensures the core logic is reusable and unaffected by changes to user commands.

### The two layers:

1. **The Storage and Utility Library (`june`)**: This layer manages repository paths, computes SHA-1 hashes, serializes objects, and compresses payloads using Zlib deflation.
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
│   ├── refs/
│   │   ├── heads/
│   │   └── tags/
│   └── objects/
```

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

## 4. Dependencies & Build Requirements

June does not use any external packages. It is written in pure Java and only uses standard library packages:

### Required Standard Packages

* `java.io`: Handles file and directory reading and writing.
* `java.nio`: Handles path resolution, symbolic links, and file movements.
* `java.security`: Provides the SHA-1 hashing classes.
* `java.util`: Provides lists, maps, and property utilities.
* `java.util.zip`: Handles file compression.
* `java.time`: Handles date and time for commits.

## 5. System Implementation Sequence and Class Dependency Reference

This section outlines how each class is built and how they work together.

### 1. Hashing Library & Serialization Models (`Sha1.java`, `ObjectData.java`, `Tree.java`, and `Commit.java`)

* **Role**: Establishes byte-level data serialization, SHA-1 hashing, and type-specific data mapping. `ObjectData.java` serves as the base model, while `Tree.java` (handling tree entry sorting and binary parsing) and `Commit.java` (handling parent reference list parsing and commit message extraction) inherit from it.
* **Integrations**: Receives raw byte payloads, formats standard metadata headers, and performs hex conversions used across all storage and reference systems.

### 2. Repository Metadata Model (`Repository.java` and `Modes.java`)

* **Role**: Resolves local repository paths.

# June

June commands parse user inputs, check arguments, and run the library code.

## 1. Command Syntax and Router Dispatch

The main entry point is the `App` class in the `cmd` directory. When you run a command:
1. `App.main` gets the command name from the first argument (`args[0]`).
2. It sets up the repository controller: `new Repository(new File("."))`. (If `JUNE_DIR` or `june.dir` is set, it uses that path instead of the current directory).

## 2. Command Specifications

### 1. `init`

* **Syntax**: `june init`
- You must initialize the repository before running other commands.
- In `init`, June calls `repo.init()` to create `.june/`, `.june/objects/`, `.june/refs/heads/`, and `.june/refs/tags/`.