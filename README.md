# June - feat: implement SHA-1 content hashing utility

June is a Java-based version control system implemented with the JDK. It implements a compact, serverless model to manage file snapshots, staging databases, reference graphs, and commit histories.

The implementation separates the command-line adapter layer from reusable repository and feature logic, making the codebase clean, modular, and easy to extend.

## Capabilities

June supports a standard subset of operations:
- **Repository Setup**: Initialize database directories (`.june/`) and configure default refs.

## Codebase Structure

The code is organized into two distinct directories:
1. **`june/`**: Contains the reusable base library under package `june`. This manages underlying operations (Repository, ObjectStore, Index, Diff) and the main logical workflows.
2. **`cmd/`**: Contains the CLI under the default package. It handles user inputs parsing (via `App.java` main method) and CLI terminal feedback formatters.

## Build and Setup

### 1. Requirements

- Java Development Kit (JDK).

### 2. Compilation

Clean and compile the library:

```bash
javac -d bin june/*.java
```

Compile the CLI (pointing classpath to the library):

```bash
javac -cp bin -d bin cmd/*.java
```

Or compile and package the application into JARs:
- Reusable Library JAR:
  ```bash
  jar --create --file june.jar -C bin .
  ```
- Executable CLI JAR:
  ```bash
  jar --create --file cmd.jar --main-class App -C bin .
  ```

### 3. Execution Entrypoint

Run commands using either the compiled classpath or the packaged JAR:

```bash
# Using classpath
java -cp bin App <command> [arguments]

# Using JAR
java -jar cmd.jar <command> [arguments]
```

## Detailed Usage Guide

### 1. `init` — Initialize a Repository

Create a fresh repository directory structure in the current directory:

```bash
java -cp bin App init
```
