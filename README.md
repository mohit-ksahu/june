# June

June is a Java-based version control system implemented with the JDK. It implements a compact, serverless model to manage file snapshots, staging databases, reference graphs, and commit histories.

The implementation separates the command-line adapter layer from reusable repository and feature logic, making the codebase clean, modular, and easy to extend.

## Capabilities

June supports a standard subset of operations:
- **Repository Setup**: Initialize database directories (`.june/`) and configure default refs.
- **Staging Index**: Track files, symlinks, and directories recursively, capture file deletions, and maintain an alphabetically sorted stage.
- **Commit Snapshots**: Create commit snapshots with author signatures, timestamps, and commit messages.
- **Ignore Rules**: Exclude build outputs and temporary files using `.juneignore` matching.
- **Atomic Locks**: Protect index updates with file locks to avoid write conflicts.

## Codebase Structure

The code is organized into two distinct directories:
1. **`june/`**: Contains the reusable base library under package `june` and package `june.lib`. This manages underlying operations (Repository, ObjectStore, Index, Diff) and the main logical workflows.
2. **`cmd/`**: Contains the CLI under the default package. It handles user inputs parsing (via `App.java` main method) and CLI terminal feedback formatters.

## Build and Setup

### 1. Requirements

- Java Development Kit (JDK).

### 2. Compilation

Clean and compile the library:

```bash
javac -d bin/june june/*.java june/lib/*.java
```

Compile the CLI (pointing classpath to the library):

```bash
javac -cp bin/june -d bin/cmd cmd/*.java
```

Or compile and package the application into JARs:
- Reusable Library JAR:
  ```bash
  jar --create --file june.jar -C bin/june .
  ```
- Executable CLI JAR:
  ```bash
  jar --create --file cmd.jar --main-class App -C bin/june . -C bin/cmd .
  ```

### 3. Execution Entrypoint

Run commands using either the compiled classpath or the packaged JAR:

```bash
# Using classpath
java -cp bin/june:bin/cmd App <command> [arguments]

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
   ./jre/bin/java -cp bin/june:bin/cmd App <command> [arguments]

   # Using JAR
   ./jre/bin/java -jar cmd.jar <command> [arguments]
   ```

## Detailed Usage Guide

### 1. `init` — Initialize a Repository

Create a fresh repository directory structure in the current directory:

```bash
java -cp bin App init
```


### 3. `add` — Stage File Changes

Stage files and directories to prepare them for the next commit:

```bash
# Stage a single file
java -cp bin App add src/Main.java

# Stage multiple files
java -cp bin App add README.md build.gradle

# Stage directory paths recursively
java -cp bin App add docs/
```

### 5. `commit` — Record a Commit Snapshot

Save staged index modifications to the repository log history:

```bash
# Commit standard changes with a message
java -cp bin App commit -m "Create initial project structure"

# Automatically stage modified tracked files and commit
java -cp bin App commit -a -m "Update configuration details"
# Or combine the flags:
java -cp bin App commit -am "Update configuration details"
```

### 11. `rm` — Remove Files from Tracking

Stop tracking files and optionally remove them from the physical workspace:

```bash
# Remove tracking and delete the file from the disk
java -cp bin App rm src/Legacy.java

# Remove tracking but keep the physical file in the working directory
java -cp bin App rm --cached src/Legacy.java
```
