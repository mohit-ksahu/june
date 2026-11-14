# June

June is a version control system written in pure Java (JDK 21) with zero third-party dependencies. It works directly with files on disk, implementing version control concepts like content-addressable storage, snapshot staging, Merkle trees, and commit histories.

## Features

- **Set up repositories**: Creates the `.june/` directory and configures your initial `HEAD`.
- **Stage files**: Tracks files, directories, symlinks, and deletions in a clean, sorted index.
- **Save commits**: Records snapshots with author names, emails, timestamps, and messages.
- **Check status**: Quickly scans your workspace for modified, deleted, and untracked files using cached file sizes and edit times.
- **Work with branches and tags**: Create, list, rename (`-m`), and delete branches and tags, including nested paths like `feature/login`.
- **Checkout versions**: Switch between branches or inspect specific commits and tags safely without overwriting uncommitted work.
- **Restore files**: Discard unwanted workspace edits or unstage files from the index.
- **View diffs**: Review line-by-line differences with clean ANSI colored output.
- **Rename and remove files**: Move files (`mv`) and stop tracking paths (`rm`).
- **Read history**: Browse commit history in standard or single-line (`--oneline`) format.
- **Merge histories**: Fast-forward active branches when changes line up directly.
- **Hard reset**: Point your workspace and index directly to any commit hash.
- **Ignore files**: Keep build folders and temp files out of your repository with `.juneignore`.
- **Lock files safely**: Uses atomic file locks to prevent concurrent writes from corrupting data.

## Codebase Layout

- **`june/`**: The core version control engine. A single flat package where `Repository` acts as the unified facade providing both direct workflow operations and storage primitives.
- **`cmd/`**: The command-line interface. Parses arguments, handles user options, and formats terminal output.

## Building and Running

### Requirements
- JDK 21 or newer.

### Compile
```bash
javac -d out $(find june cmd test -name "*.java")
```

### Build JARs
```bash
# Core library (without CLI)
jar --create --file june.jar -C out june

# Standalone CLI tool
jar --create --file cmd.jar --main-class App -C out .
```

### Run
```bash
# From compiled classes
java -cp out App <command> [args]

# From executable JAR
java -jar cmd.jar <command> [args]
```

## Command Guide

### `init`
Start a new repository in the current folder:
```bash
java -cp out App init
```
To place the `.june` folder elsewhere, set `JUNE_DIR=/path` in your environment or pass `-Djune.dir=/path` to Java.

### `config`
View or update repository settings in `.june/config`:
```bash
java -cp out App config user.name "Your Name"
java -cp out App config user.email "you@example.com"
java -cp out App config user.name
```

### `add`
Stage files or folders for your next commit:
```bash
java -cp out App add src/Main.java
java -cp out App add .
```

### `status`
See what has changed in your working tree:
```bash
java -cp out App status
```

### `commit`
Save your staged changes to history:
```bash
java -cp out App commit -m "Add login authentication"
java -cp out App commit -a -m "Auto-stage tracked files and commit"
java -cp out App commit -am "Short auto-stage and commit"
```

### `diff`
Review changes line-by-line:
```bash
java -cp out App diff           # Unstaged changes in working tree vs index
java -cp out App diff --staged  # Staged changes in index vs current commit
java -cp out App diff --cached  # Same as --staged
```

### `branch`
Work with development branches:
```bash
java -cp out App branch                    # List local branches (* shows current)
java -cp out App branch feature            # Create a branch at current commit
java -cp out App branch -m new-name        # Rename current branch
java -cp out App branch -m old new         # Rename a specific branch
java -cp out App branch -d feature         # Delete a merged branch
java -cp out App branch -D feature         # Force delete an unmerged branch
```

### `tag`
Create and manage commit tags:
```bash
java -cp out App tag                       # List all tags
java -cp out App tag v1.0                  # Tag current commit
java -cp out App tag v1.0 a94a8f           # Tag a specific commit
java -cp out App tag -d v1.0               # Delete a tag
```

### `checkout`
Switch branches or inspect an older snapshot:
```bash
java -cp out App checkout feature          # Switch to branch
java -cp out App checkout a94a8f           # Inspect a specific commit (detached HEAD)
```

### `restore`
Discard edits or unstage files:
```bash
java -cp out App restore src/Main.java          # Discard edits and restore from index
java -cp out App restore --staged src/Main.java # Unstage changes back to last commit
```

### `rm`
Stop tracking files:
```bash
java -cp out App rm legacy.txt          # Remove from index and delete from disk
java -cp out App rm --cached config.xml # Remove from index but keep on disk
```

### `mv`
Rename or move a file:
```bash
java -cp out App mv src/Old.java src/New.java
```

### `cat-file`
Inspect an object directly by its hash:
```bash
java -cp out App cat-file -p a94a8f
```

### `log`
Browse through commit history:
```bash
java -cp out App log                       # Full log with authors and dates
java -cp out App log --oneline             # Compact, one line per commit
java -cp out App log -n 5                  # Show only the last 5 commits
```

### `merge`
Fast-forward your active branch to another branch, tag, or commit:
```bash
java -cp out App merge feature
```

### `reset`
Reset your working tree, index, and branch pointer to any commit:
```bash
java -cp out App reset --hard a94a8f
```

## License

MIT License. See LICENSE for details.
