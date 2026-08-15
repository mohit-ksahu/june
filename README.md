# June

June is a Java-based version control system implemented with the JDK. It implements a compact, serverless model to manage file snapshots, staging databases, reference graphs, and commit histories.

The implementation separates the command-line adapter layer from reusable repository and feature logic, making the codebase clean, modular, and easy to extend.

## Capabilities

June supports a standard subset of operations:
- **Repository Setup**: Initialize database directories (`.june/`) and configure default refs.
- **Staging Index**: Track files, symlinks, and directories recursively, capture file deletions, and maintain an alphabetically sorted stage.
- **Commit Snapshots**: Create commit snapshots with author signatures, timestamps, and commit messages.
- **Status Checks**: Scan the workspace and compare it with the staging index and HEAD commit to display modified, deleted, and untracked files.
- **Branches and Tags**: Create, list, rename (`-m` / `mv`), and delete branch pointers and tags. Full support for nested reference paths (e.g., `feature/login`).
- **Checkout Operations**: Switch workspace states to target branches, tags, or raw commit hashes.
- **State Restoration**: Unstage changes or discard modifications in the working directory.
- **Myers Diff**: Full-File Diff Viewer mode with optimized memory slicing for unified line-by-line diffs.
- **Refactoring Sync**: Rename/move files or untrack them from the database.
- **Log Traversal**: Print chronological commit histories in standard or single-line formats.
- **Fast-forward Merges**: Merge branch histories without generating merge commits.
- **Hard Resets**: Align the working directory and staging index directly with a target commit.
- **Ignore Rules**: Exclude build outputs and temporary files using `.juneignore` matching.
- **Atomic Locks**: Protect index updates with file locks to avoid write conflicts.

## Codebase Structure

The code is organized into two distinct directories:
1. **`june/`**: Contains the reusable base library under package `june` and package `june.lib`. This manages underlying operations (Repository, ObjectStore, Index, Diff) and the main logical workflows. See the [June Architecture & Specification](june/README.md) for more details.
2. **`cmd/`**: Contains the CLI wrapper under the default package. It handles user inputs parsing (via `App.java` main method) and CLI terminal feedback formatters. See the [June CLI Commands Reference](cmd/README.md) for command specifications.

## Build and Setup

### 1. Requirements

- Java Development Kit (JDK) 17 or higher.

### 2. Compilation

Compile the codebase (both core library and CLI classes) into the `out/` directory:

```bash
javac -d out june/**/*.java cmd/**/*.java
```

Or compile and package the application into JARs:
- **Reusable Library JAR** (excludes CLI):
  ```bash
  jar --create --file june.jar -C out june
  ```
- **Executable CLI Wrapper JAR**:
  ```bash
  jar --create --file cmd.jar --main-class App -C out .
  ```

### 3. Execution Entrypoint

Run command wrappers using either the compiled classpath or the packaged JAR:

```bash
# Using classpath
java -cp out App <command> [arguments]

# Using JAR
java -jar cmd.jar <command> [arguments]
```

### 4. Bundling a Custom JRE with jlink

To package a standalone execution environment that runs without requiring a pre-installed JDK/JRE system-wide, you can assemble a custom JRE bundle using `jlink`:

1. Build a minimized JRE image containing only the `java.base` module:
   ```bash
   jlink --add-modules java.base \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output jre
   ```

2. Run the application using the bundled JRE:
   ```bash
   # Using classpath
   ./jre/bin/java -cp out App <command> [arguments]

   # Using JAR
   ./jre/bin/java -jar cmd.jar <command> [arguments]
   ```

## Detailed Usage Guide

### 1. `init` — Initialize a Repository

Create a fresh repository directory structure in the current directory:

```bash
java -cp out App init
```
This initializes the `.june/` directory structure containing `.june/objects/`, `.june/refs/heads/`, `.june/refs/tags/`, and sets the active HEAD reference to `refs/heads/main`.

#### Customizing the Metadata Directory (`.june`) Location

By default, June looks for or creates the `.june/` directory in the current working directory. You can configure a custom parent location for the `.june` metadata directory using either:

1. **Environment Variable (`JUNE_DIR`)**:
   ```bash
   export JUNE_DIR=/path/to/custom/parent/folder
   java -cp out App init
   ```

2. **JVM System Property (`june.dir`)**:
   ```bash
   java -Djune.dir=/path/to/custom/parent/folder -cp out App init
   ```

### 2. `config` — Manage Configuration Settings

Read or write repository configuration parameters stored in `.june/config`:

```bash
# Set user name
java -cp out App config user.name "John Doe"

# Set user email
java -cp out App config user.email "john@example.com"

# Read a configuration value
java -cp out App config user.name
```

### 3. `add` — Stage File Changes

Stage files and directories to prepare them for the next commit:

```bash
# Stage a single file
java -cp out App add src/Main.java

# Stage multiple files
java -cp out App add README.md build.gradle

# Stage directory paths recursively
java -cp out App add docs/
```

### 4. `status` — Show Workspace Changes

View the difference between the working directory, staging index, and HEAD commit:

```bash
java -cp out App status
```
This outputs the active branch name and lists staged changes, unstaged changes, and untracked files.

### 5. `commit` — Record a Commit Snapshot

Save staged index modifications to the repository log history:

```bash
# Commit standard changes with a message
java -cp out App commit -m "Create initial project structure"

# Automatically stage modified tracked files and commit
java -cp out App commit -a -m "Update configuration details"
# Or combine the flags:
java -cp out App commit -am "Update configuration details"
```

### 6. `diff` — View Line Differences

Compare lines between workspace files, the staging index, and HEAD:

```bash
# View unstaged changes in the working directory
java -cp out App diff

# View staged changes in the index compared to HEAD
java -cp out App diff --staged
# Or use the alias:
java -cp out App diff --cached
```

### 7. `branch` — Manage Branches

Manage branch references to track independent lines of development:

```bash
# List all local branches (active branch is marked with an asterisk)
java -cp out App branch

# Create a new branch pointing to the current commit
java -cp out App branch feature-auth

# Rename active branch to master
java -cp out App branch -m master
# Or using the mv subcommand:
java -cp out App branch mv master

# Rename specific branch
java -cp out App branch -m old-name new-name

# Delete a branch (checks for unmerged history)
java -cp out App branch -d feature-auth

# Force delete a branch
java -cp out App branch -D feature-auth
```

### 8. `tag` — Manage Tags

Create, list, or delete reference tags:

```bash
# List all tags in the repository
java -cp out App tag

# Create a tag pointing to the current commit
java -cp out App tag v1.0.0

# Create a tag pointing to a specific commit hash or resolved ref
java -cp out App tag v1.0.0 a94a8f

# Delete a tag
java -cp out App tag -d v1.0.0
```

### 9. `checkout` — Switch Active States

Switch the active branch or align workspace files with a tag/commit hash:

```bash
# Switch to an existing branch
java -cp out App checkout feature-auth

# Switch to a specific tag or commit hash (detached HEAD state)
java -cp out App checkout v1.0.0
java -cp out App checkout a94a8f
```

### 10. `restore` — Discard Workspace and Index Changes

Discard local edits or unstage files in the index:

```bash
# Discard working directory modifications and restore from the staging index
java -cp out App restore src/Main.java

# Unstage files in the index (reverts index state back to HEAD commit)
java -cp out App restore --staged src/Main.java
```

### 11. `rm` — Remove Files from Tracking

Stop tracking files and optionally remove them from the physical workspace:

```bash
# Remove tracking and delete the file from the disk
java -cp out App rm src/Legacy.java

# Remove tracking but keep the physical file in the working directory
java -cp out App rm --cached src/Legacy.java
```

### 12. `mv` — Move or Rename Files

Rename a file or directory path, updating both the workspace and staging index:

```bash
java -cp out App mv src/OldName.java src/NewName.java
```

### 13. `cat-file` — Inspect Repository Objects

Decompress and print raw database object details (blobs, trees, commits):

```bash
# Pretty-print the resolved contents of an object hash
java -cp out App cat-file -p a94a8f
```

### 14. `log` — View Commit History

Display the commit logs in reverse chronological order:

```bash
# Print standard commit log history
java -cp out App log

# Print condensed single-line log history
java -cp out App log --oneline

# Limit the log to a specific count of commits
java -cp out App log -n 5
# Or use the alias:
java -cp out App log --max-count 5

# Combine formatting and count limit
java -cp out App log --oneline -n 5
```

### 15. `merge` — Merge Branch History

Integrate another branch, tag, or commit history into the currently checked-out branch using fast-forward merges only:

```bash
# Fast-forward the active branch pointer to feature-auth's commit
java -cp out App merge feature-auth
```

### 16. `reset` — Hard Reset Reference State

Hard reset the working directory, staging index, and active branch references to a commit hash:

```bash
java -cp out App reset --hard a94a8f
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
