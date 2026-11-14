# June Architecture & Technical Specification

June is a version control engine written in pure Java 21 with zero third-party dependencies. It operates directly on files in your workspace, keeping all objects, references, and index state in a local `.june` directory.

## 1. System Design and Architecture

The engine is contained in a single flat package (`june`), eliminating intermediary wrappers, trampoline forwarders, and nested subpackages.

- **Unified Facade (`Repository.java`)**: The single entry point for all operations. It exposes high-level workflow routines (`add`, `commit`, `checkout`, `status`, `diff`, `log`, `branch*`, `tag*`, `merge`, `reset`, `restore`, `rm`, `mv`, `catFile`, `config*`) alongside direct reference and storage primitives.
- **Working Tree Operations (`Worktree.java`)**: Manages working directory scanning, 3-way status reconciliation, checkout, reset, restore, rm, and mv synchronization.
- **Revision Engine (`Revision.java`)**: Manages commit creation, Merkle tree generation, commit DAG graph traversal, log history, ancestry BFS, and fast-forward merges.
- **Reference Management (`Refs.java`)**: Manages branch and tag references, short SHA-1 resolution, and repository configuration properties.
- **Storage & Primitives**: Content-addressable object database (`Odb.java`), index ledger (`Index.java`), Merkle tree builder (`Tree.java`), commit parser and serializer (`Commit.java`), cryptographic hashing (`Hash.java`), ignore rule matching (`Ignore.java`), Myers diff algorithm (`Diff.java`), mode definitions (`Mode.java`), and object data models (`ObjectData.java`).

This flat architecture yields immediate execution speed: 0 abstraction hops, direct stack frames, and minimal classloader startup overhead.

## 2. Database and File Layout

All metadata and compressed content live in the `.june` directory at your workspace root:

```
[workspace_root]/
├── .june/
│   ├── HEAD                     # Active reference pointer or detached commit SHA
│   ├── config                   # Local configuration properties
│   ├── index                    # Staging index (paths, hashes, modes, size, mtime)
│   ├── refs/
│   │   ├── heads/               # Branch references (e.g., refs/heads/main)
│   │   └── tags/                # Tag references (e.g., refs/tags/v1.0)
│   └── objects/                 # Content-addressable object database
│       └── [2-char hex]/        # Partitioned subdirectories
│           └── [38-char hex]    # Deflated object files
```

### Custom Repository Directory
By default, June creates and looks for `.june/` in the workspace root. You can redirect it to another location if needed:
- Environment variable: `export JUNE_DIR=/path/to/custom/dir`
- System property: `-Djune.dir=/path/to/custom/dir`
- In code: `new Repository(workspaceDir, customParentDir)`

### References and HEAD
- **Branch and Tag references**: Plain-text files containing a 40-character hex SHA-1 and a trailing newline (`\n`). Nested reference paths like `refs/heads/feature/login` automatically create the necessary parent directories on disk.
- **HEAD pointer**:
  - Symbolic reference: `ref: refs/heads/main\n`
  - Detached HEAD: `[40-character commit hash]\n`

```java
public String headTarget() throws IOException {
  return read(headFile);
}

public String headSha() throws IOException {
  String head = headTarget();
  if (head == null) return null;
  return head.startsWith(REF_PREFIX) ? read(headFile(head)) : head;
}
```

### Staging Index (`.june/index`)
The staging index is a flat text file that tracks files staged for commit. Each line records one entry with 5 fields separated by null bytes (`\0`):

`hash\0mode\0size\0mtime\0path\n`

- **Fields**:
  - `hash`: 40-character hex hash of the object blob.
  - `mode`: File permission mode (`100644` for normal files, `100755` for executables, `120000` for symlinks).
  - `size`: Byte size of the file on disk (-1 if unrecorded).
  - `mtime`: Last modified timestamp in epoch milliseconds (-1 if unrecorded).
  - `path`: File path relative to workspace root (always normalized with `/` separators).
- **Memory Representation**: Loaded into a `TreeMap<String, Entry>` sorted alphabetically by path, ensuring deterministic Merkle tree construction.
- **Cache Optimization**: Matching the stored `size` and `mtime` against disk attributes allows `status` to verify clean files without re-reading or re-hashing file content.

## 3. Object Model & Serialization

Every object in June prepends a standard header before hashing and compression:
`[type] [length]\0[body]`

```java
public byte[] serialize() {
  byte[] header = (type + " " + data.length + "\0").getBytes(StandardCharsets.UTF_8);
  byte[] result = new byte[header.length + data.length];
  System.arraycopy(header, 0, result, 0, header.length);
  System.arraycopy(data, 0, result, header.length, data.length);
  return result;
}
```

### Object Types

1. **Blob (`blob`)**: Raw file or symlink content.
2. **Tree (`tree`)**: Binary directory manifest containing sorted entries:
   `[mode] [name]\0[20-byte binary SHA-1]`
   - **Sorting Rule**: Directory entries sort as though suffixed with `/`. This ensures canonical Merkle tree ordering:
     ```java
     public int compareTo(Entry other) {
       String first = name + (mode.equals(Mode.TREE) ? "/" : "");
       String second = other.name + (other.mode.equals(Mode.TREE) ? "/" : "");
       return first.compareTo(second);
     }
     ```
3. **Commit (`commit`)**: Text document containing the Merkle tree hash, parent commit hashes, author signature, committer signature, and the commit message:
   ```text
   tree [tree-hash]
   parent [parent-hash]
   author Name <email> [timestamp] [tz]
   committer Name <email> [timestamp] [tz]

   [message]
   ```

### Storage Compression
Objects are compressed using `java.util.zip.DeflaterOutputStream` and decompressed through `InflaterInputStream`. When writing blobs, June streams bytes directly through a `DigestOutputStream` to calculate the SHA-1 hash while deflating directly to disk.

## 4. Atomic File Locking

Metadata updates (`.june/index` and references) use atomic lock files to prevent race conditions during concurrent modifications:

1. **Lock Acquisition**: Tries to create `<filename>.lock` atomically with `File.createNewFile()`.
2. **Stale Lock Recovery**: If a lock exists for longer than 5 minutes (configurable via system property `june.lock.staleMillis`), the stale lock is unlinked and recreated.
3. **Commit Phase**: Content is written to the lock file, then renamed to the destination file using `Files.move(..., StandardCopyOption.ATOMIC_MOVE)`.

## 5. Core Algorithms

### Myers Diff Algorithm (`Diff.java`)
Calculates the Shortest Edit Script (SES) between two line sequences:
- Explores diagonal paths $k = x - y$ across edit distances.
- Keeps track of furthest-reaching paths and backtracks to generate addition (`+`), deletion (`-`), and context (` `) operations represented by `record DiffOp(char type, String text)`.

### Ignore Rule Matching (`Ignore.java`)
Translates `.juneignore` glob rules into standard regular expressions:
- `**`: Recursive directory matching (`.*`).
- `*`: Single path segment matching (`[^/]*`).
- `?`: Single character matching (`[^/]`).
- `!`: Inversion flag (includes previously ignored path).
- Leading `/` or interior slashes: Anchors pattern to repository root; otherwise matches against individual path segments.

### Conflict Detection (`Worktree.java`)
Before modifying the working directory during checkout, merge, or reset:
1. Identifies untracked files that would be overwritten by incoming files.
2. Identifies tracked files with uncommitted local modifications.
3. If conflicts exist, aborts without modifying any files on disk.

### Ancestry Traversal (`Revision.java`)
- Performs a Breadth-First Search (BFS) over the commit Directed Acyclic Graph (DAG) using a visited set.
- Validates whether an incoming commit is a descendant of the current branch for fast-forward merging, and prevents deletion of unmerged branches.

## 6. System Behaviors

- **User Identity**: Resolves commit author from `.june/config` (`user.name`/`user.email`), falling back to environment variables (`JUNE_AUTHOR_NAME`/`JUNE_AUTHOR_EMAIL`), system properties, or system username.
- **Binary Detection**: Scans first 8,000 bytes for null (`0`) bytes. Binary files skip line-by-line Myers diffs and output `Binary files differ`.
- **Short SHA-1 Resolution**: Supports abbreviated hashes ($\ge 4$ characters). Scans the corresponding `.june/objects/xx/` directory; throws `OperationException` if ambiguous or not found.
- **File Modes**:
  - `100644`: Standard file.
  - `100755`: Executable file (`dest.setExecutable(true)`).
  - `120000`: Symbolic link (`Files.createSymbolicLink()`).

## 7. Built-in Java APIs and Features

June uses standard built-in Java platform libraries and modern language features without third-party dependencies:

### Cryptography and Compression
- `java.security.MessageDigest`: Computes cryptographic 160-bit SHA-1 hashes for content-addressable storage.
- `java.security.DigestOutputStream`: Streams data through message digests to calculate hashes inline without secondary read passes.
- `java.util.zip.DeflaterOutputStream`: Compresses objects using zlib deflation before writing to disk.
- `java.util.zip.InflaterInputStream`: Decompresses stored objects on the fly when reading.

### Collections and Data Structures
- `java.util.TreeMap`: Red-Black tree providing $O(\log N)$ sorted path indexing for `.june/index`, ensuring deterministic in-order iteration during Merkle tree construction.
- `java.util.HashMap`: Key-value lookups for prefix tree construction and commit object caching.
- `java.util.HashSet`: Visited-node tracking during graph traversal to prevent cycles.
- `java.util.ArrayDeque` / `Queue`: FIFO frontier queue for breadth-first ancestry search.
- `java.util.PriorityQueue`: Bounded top-$K$ commit extraction ordered by timestamp in log graph traversal.
- `java.util.HexFormat`: Fast conversion between binary hash byte arrays and 40-character hex strings.
- `java.util.Properties`: Reads and updates repository configuration key-value pairs in `.june/config`.

### File I/O and Concurrency
- `java.nio.file.Files`: Workspace file operations (`Files.walk`, `Files.readString`, `Files.writeString`, `Files.createSymbolicLink`).
- `java.nio.file.StandardCopyOption.ATOMIC_MOVE`: Atomic file replacement guaranteeing safe index and reference updates.
- `java.io.File.createNewFile()`: Atomic mutual-exclusion test-and-set for `.lock` creation.
- `java.io.InputStream.transferTo()`: Direct stream-to-stream byte transfers without buffering full files into memory.

### Pattern Matching and Dates
- `java.util.regex.Pattern`: Compiles glob wildcard rules into anchored regular expressions for `.juneignore`.
- `java.time.Instant`, `ZonedDateTime`, `DateTimeFormatter`: Formats standard commit timestamps and timezone offsets.

### Modern Language Features
- **Records (`record`)**: Immutable data models for `Entry`, `DiffOp`, `StatusResult`, `BranchResult`, `CommitResult`, and `LogEntry`.
- **Try-With-Resources**: Safe resource cleanup on file streams and directory walkers.
- **Type Inference (`var`)**: Clean local variable declarations where types are clear from assignment.

## 8. Programmatic Embedding

`Repository` can be embedded directly into any JVM application:

```java
import june.Repository;
import june.Repository.CommitResult;
import june.Repository.StatusResult;
import java.io.File;
import java.util.List;

public class EmbeddedExample {
  public static void main(String[] args) throws Exception {
    File workspace = new File("/path/to/project");
    Repository repo = new Repository(workspace);
    repo.init();

    // Stage paths
    repo.add(List.of("src"));

    // Check status
    StatusResult status = repo.status();
    System.out.println("Branch: " + status.branch());

    // Commit snapshot
    CommitResult commit = repo.commit("Initial commit", false);
    System.out.println("Commit Hash: " + commit.hash());

    // Switch branch
    repo.checkout("main");
  }
}
```
