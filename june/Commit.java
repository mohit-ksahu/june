package june;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Commit extends ObjectData {
  private final String tree;
  private final List<String> parents;
  private final String author;
  private final String committer;
  private final String message;

  public Commit(String tree, List<String> parents,
      String author, String committer, String message) {
    super(COMMIT, serialize(tree, parents, author, committer, message));
    this.tree = tree;
    this.parents = List.copyOf(parents);
    this.author = author;
    this.committer = committer;
    this.message = message;
  }

  public Commit(byte[] rawData) {
    super(COMMIT, rawData);
    String text = new String(rawData, StandardCharsets.UTF_8);
    int sep = text.indexOf("\n\n");
    String headers = sep != -1 ? text.substring(0, sep) : text;
    String raw = sep != -1 ? text.substring(sep + 2) : "";
    this.message = raw.endsWith("\n") ? raw.substring(0, raw.length() - 1) : raw;

    String treeSha = null;
    List<String> parentList = new ArrayList<>();
    String authorStr = null;
    String committerStr = null;

    for (String line : headers.split("\n")) {
      if (line.startsWith("tree ")) {
        treeSha = line.substring(5).trim();
      } else if (line.startsWith("parent ")) {
        parentList.add(line.substring(7).trim());
      } else if (line.startsWith("author ")) {
        authorStr = line.substring(7).trim();
      } else if (line.startsWith("committer ")) {
        committerStr = line.substring(10).trim();
      }
    }

    if (treeSha == null || authorStr == null) {
      throw new OperationException("malformed commit object");
    }
    this.tree = treeSha;
    this.parents = parentList;
    this.author = authorStr;
    this.committer = committerStr != null ? committerStr : authorStr;
  }

  public String tree() {
    return tree;
  }

  public List<String> parents() {
    return parents;
  }

  public String author() {
    return author;
  }

  public String message() {
    return message;
  }

  private static byte[] serialize(String tree, List<String> parents,
      String author, String committer, String message) {
    StringBuilder buf = new StringBuilder();
    buf.append("tree ").append(tree).append("\n");
    for (String parent : parents) {
      buf.append("parent ").append(parent).append("\n");
    }
    buf.append("author ").append(author).append("\n");
    buf.append("committer ").append(committer).append("\n");
    buf.append("\n").append(message).append("\n");
    return buf.toString().getBytes(StandardCharsets.UTF_8);
  }
}
