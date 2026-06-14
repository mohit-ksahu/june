# June

June is a simple version control system written in Java.

## 1. System Design and Key Layers

June is written in Java and does not use any external packages. The code is split into two layers to keep the storage logic and the user interface separate.

### Why split the code this way?

Separating the command-line interface from the core storage logic keeps the codebase modular. This ensures the core logic is reusable and unaffected by changes to user commands.

### The two layers:

1. **The Storage and Utility Library (`june`)**: This layer creates the `.june/` directory structure, computes SHA-1 hashes, and serializes repository objects into typed binary payloads.
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

## 4. Dependencies & Build Requirements

June does not use any external packages. It is written in pure Java and only uses standard library packages:

### Required Standard Packages

* `java.io`: Handles file and directory reading and writing.
* `java.nio`: Handles path resolution, symbolic links, and file movements.
* `java.security`: Provides the SHA-1 hashing classes.
* `java.util`: Provides lists, maps, and property utilities.

## 5. System Implementation Sequence and Class Dependency Reference

This section outlines how each class is built and how they work together.

### 1. Hashing Library & Serialization Models (`Sha1.java` and `ObjectData.java`)

* **Role**: Establishes byte-level data serialization and SHA-1 hashing. `ObjectData.java` serves as the base model for serializing objects.
* **Integrations**: Receives raw byte payloads, formats standard metadata headers, and performs hex conversions used across all storage systems.

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