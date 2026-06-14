# June

June is a Java-based version control system implemented with the JDK. It implements a compact, serverless model to manage file snapshots, staging databases, reference graphs, and commit histories.

The implementation separates the command-line adapter layer from reusable repository and feature logic, making the codebase clean and modular.

## Codebase Structure

1. **`june/`**: Contains the core library package (`Repository`, `OperationException`, etc.).
2. **`cmd/`**: Contains the CLI package (`App`).

## Build and Setup

Compile the codebase:
```bash
javac -d bin june/*.java
javac -cp bin -d bin cmd/*.java
```

Run the application:
```bash
java -cp bin App <command> [arguments]
```

## Usage

### `init` — Initialize a Repository

```bash
java -cp bin App init
```
