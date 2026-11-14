# June CLI Commands

The June command-line interface handles argument parsing, input validation, terminal colors, and delegates operations directly to `june.Repository`.

## How the CLI Works

The main entry point is `App.java`. When you run a command:

1. **Pick the command**: It takes `args[0]` as the command name and passes the remaining arguments to that command's handler.
2. **Find the repository**: It creates a `new Repository(new File("."))`. If you set `JUNE_DIR` in your environment or pass `-Djune.dir` to Java, it uses that directory; otherwise, it looks for `.june` in the current folder or walks up parent folders.
3. **Verify repository exists**: Unless you are running `init`, it makes sure `.june` exists on disk before doing any work.
4. **Dispatch**: Passes the `Repository` instance directly to the static `run(repo, args)` method in the matching handler class under `cmd/`.
5. **Handle errors**: Catches any `OperationException` or `IOException`, prints `fatal: <message>` to `System.err`, and exits with status code 1.

## Command Reference

### `init`
- **Usage**: `june init`
- **What it does**: Creates `.june/`, `.june/objects/`, `.june/refs/heads/`, `.june/refs/tags/`, and sets the initial `HEAD` to `ref: refs/heads/main\n`.
- **Output**: Prints `Initialized empty repository in <path>/.june/`.

### `config`
- **Usage**: `june config <key> [<value>]`
- **What it does**:
  - `june config <key>`: Reads a setting from `.june/config`. Prints a blank line if it is not set.
  - `june config <key> <value>`: Saves or updates the setting in `.june/config`.
- **Errors**: Fails if you do not provide a key.

### `add`
- **Usage**: `june add <file>...`
- **What it does**: Resolves file paths relative to your repository root, checks `.juneignore` rules, writes compressed blob objects, and records their SHA-1, file mode, byte size, and modification time in `.june/index`. Recursively scans folders and unstages files that were deleted.
- **Errors**: Fails if you do not provide any paths or if a path does not match any files.

### `status`
- **Usage**: `june status`
- **What it does**: Scans your workspace and index to show:
  - Your active branch (or detached HEAD commit).
  - Staged changes ready to commit (in green).
  - Tracked files that are modified or deleted in the workspace (in red).
  - Untracked files and folders (in red).
  - Prints `nothing to commit, working tree clean` when everything is up to date.

### `commit`
- **Usage**: `june commit [-a] -m <message>` or `june commit -am <message>`
- **Options**:
  - `-m <message>`: Required commit message.
  - `-a`, `-am`: Automatically stages modified tracked files before creating the commit.
- **What it does**: Builds the Merkle tree from the index, writes a commit object with author details and timestamps, and updates your current branch pointer and HEAD.
- **Errors**: Fails if the commit message is missing or blank.

### `diff`
- **Usage**: `june diff [--cached | --staged]`
- **Options**:
  - Without flags: Shows changes between files on disk and the staging index.
  - `--cached` / `--staged`: Shows changes between the staging index and your current commit.
- **What it does**: Runs the Myers diff algorithm and outputs unified diff format with ANSI terminal colors (green for additions, red for deletions, cyan for hunk headers). Binary files display `Binary files differ`.

### `branch`
- **Usage**: `june branch [-d | -D | -m] [<branch-name>] [<new-name>]`
- **Options**:
  - No arguments: Lists local branches alphabetically, marking the current one with `*` in green.
  - `<name>`: Creates a new branch pointing to the current commit.
  - `-m <new-name>`: Renames the active branch.
  - `-m <old> <new>`: Renames a specific branch.
  - `-d <name>`: Deletes a branch if it has already been merged into HEAD.
  - `-D <name>`: Force deletes a branch even if it has unmerged commits.
- **Errors**: Fails if the branch already exists, does not exist, or if you try to delete the currently checked-out branch.

### `tag`
- **Usage**: `june tag [-d] [<tag-name>] [<commit-or-ref>]`
- **Options**:
  - No arguments: Lists existing tags in alphabetical order.
  - `<name> [<ref>]`: Creates a tag pointing to a specific commit or current HEAD.
  - `-d <name>`: Deletes a tag.
- **Errors**: Fails if the tag name already exists when creating, or does not exist when deleting.

### `checkout`
- **Usage**: `june checkout <branch-name> | <commit-or-tag>`
- **What it does**: Checks for uncommitted changes that would be overwritten, restores files in your workspace, updates the staging index, and points HEAD to the target branch (or directly to a commit hash in detached HEAD mode).
- **Errors**: Fails if the target does not exist or if local uncommitted changes would be lost.

### `restore`
- **Usage**: `june restore [--staged] <file>...`
- **Options**:
  - Default: Replaces modified files in your workspace with their versions from the staging index.
  - `--staged`: Unstages index entries back to the state of your last commit without changing files on disk.
- **Errors**: Fails if you do not specify any paths or if the path does not match any tracked file.

### `rm`
- **Usage**: `june rm [--cached] <file>...`
- **Options**:
  - Default: Removes the file from the staging index and deletes it from disk.
  - `--cached`: Removes the file from the staging index but keeps the file in your workspace.
- **Errors**: Fails if the file is not currently tracked.

### `mv`
- **Usage**: `june mv <source> <destination>`
- **What it does**: Renames or moves the file on disk and updates the index to match.
- **Errors**: Fails if the source file is untracked, does not exist, or if the destination file has uncommitted changes.

### `cat-file`
- **Usage**: `june cat-file -p <object-sha>`
- **What it does**: Decompresses and displays the contents of any object in `.june/objects/`:
  - `blob`: Prints raw text content.
  - `tree`: Prints mode, object type, SHA-1, and name for each entry.
  - `commit`: Prints headers (tree, parents, author, committer) and the commit message.
- **Errors**: Fails if `-p` is omitted or if the object hash cannot be found.

### `log`
- **Usage**: `june log [--oneline] [-n <count>] [--max-count <count>]`
- **Options**:
  - `--oneline`: Displays a compact 7-character hash and the commit summary line.
  - `-n <count>`, `--max-count <count>`: Limits output to the given number of commits.
- **What it does**: Walks through your commit history starting from HEAD in chronological order.

### `merge`
- **Usage**: `june merge <branch-or-commit>`
- **What it does**: Performs a fast-forward merge. It checks that your current HEAD is an ancestor of the target, updates workspace files and the index to match the target tree, and moves your branch pointer forward.
- **Errors**: Fails if the merge is not a fast-forward or if you have uncommitted workspace conflicts.

### `reset`
- **Usage**: `june reset --hard [<commit>]`
- **What it does**: Resets your working directory, index, and branch pointer to match the target commit (defaults to HEAD).
- **Errors**: Fails if the commit hash is not found or if the `--hard` flag is omitted.

## Command Classes

Every command lives in its own class in the `cmd/` package:
- `Add.java`, `Branch.java`, `CatFile.java`, `Checkout.java`, `Commit.java`, `Config.java`, `Diff.java`, `Init.java`, `Log.java`, `Merge.java`, `Mv.java`, `Reset.java`, `Restore.java`, `Rm.java`, `Status.java`, `Tag.java`.

## API Mapping

All CLI commands invoke operations directly on `june.Repository`:

| Operation | Direct Repository API | Handler |
| :--- | :--- | :--- |
| **Initialize** | `repo.init()` | `Init.java` |
| **Stage** | `repo.add(paths)` | `Add.java` |
| **Commit** | `repo.commit(message, auto)` | `Commit.java` |
| **Status** | `repo.status()` | `Status.java` |
| **Checkout** | `repo.checkout(target)` | `Checkout.java` |
| **Restore** | `repo.restore(paths, staged)` | `Restore.java` |
| **Diff** | `repo.diff(staged)` | `Diff.java` |
| **Remove** | `repo.rm(paths, cached)` | `Rm.java` |
| **Move** | `repo.mv(source, dest)` | `Mv.java` |
| **Log** | `repo.log(limit)` | `Log.java` |
| **Cat-File** | `repo.catFile(ref)` | `CatFile.java` |
| **Config** | `repo.configGet(key)` / `repo.configSet(key, val)` | `Config.java` |
| **Reset** | `repo.reset(target)` | `Reset.java` |
| **Merge** | `repo.merge(target)` | `Merge.java` |
| **Branch** | `repo.branchList()` / `repo.branchCreate(name)` / `repo.branchDelete(name, force)` | `Branch.java` |
| **Tag** | `repo.tagList()` / `repo.tagCreate(name, sha)` / `repo.tagDelete(name)` | `Tag.java` |
