# June CLI Commands Reference Manual

June command endpoints parse inputs, enforce validation rules, and route arguments to the `june.lib` library.

## 1. Command Syntax and Router Dispatch

The entry point of the command interface is the `App` class in the `cmd` directory. When a user runs a command from the shell:
1. `App.main` parses the command name from the first argument (`args[0]`).
2. Slices the remaining arguments using `Arrays.copyOfRange(args, 1, args.length)` to isolate the parameters for the specific command.
3. Instantiates the repository state controller (`new Repository(new File("."))`).
   - Note: The `Repository` constructor checks for the JVM system property `june.dir` or the system environment variable `JUNE_DIR` to determine the custom parent directory for the `.june` metadata directory. If either is set, it resolves `.june` relative to that path.
4. Checks if the repository exists on disk (unless the command is `init`).
5. Dispatches execution to the corresponding command handler class directly in the `cmd` project (default package).

If a command fails because of invalid input or runtime errors, the CLI wrapper catches the exception, prints the error message prepended with "fatal: " to standard error, and exits with code 1.

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
- In `june.cmd.Diff`, June checks for `--cached` or `--staged` flags to select the target, and outputs the comparison as colored diff hunks (red/green/cyan) with 3 lines of unchanged context.

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
