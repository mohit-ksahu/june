package june;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Commit extends ObjectData {
  private final String treeSha1;
  private final List<String> parentSha1s;
  private final String author;
  private final String committer;
  private final String message;

  public Commit(String treeSha1, List<String> parentSha1s,
      String author, String committer, String message) {
    super(ObjectTypes.COMMIT, serialize(treeSha1, parentSha1s, author, committer, message));
    this.treeSha1 = treeSha1;
    this.parentSha1s = new ArrayList<>(parentSha1s);
    this.author = author;
    this.committer = committer;
    this.message = message;
  }

  public Commit(byte[] rawData) {
    super(ObjectTypes.COMMIT, rawData);
    String text = new String(rawData, StandardCharsets.UTF_8);
    String[] lines = text.split("\n", -1);

    String tree = null;
    List<String> parents = new ArrayList<>();
    String auth = null;
    String comm = null;
    StringBuilder msgBuf = new StringBuilder();
    boolean readingMessage = false;

    for (String line : lines) {
      if (readingMessage) {
        msgBuf.append(line).append("\n");
      } else if (line.isEmpty()) {
        readingMessage = true;
      } else if (line.startsWith("tree ")) {
        tree = line.substring(5).trim();
      } else if (line.startsWith("parent ")) {
        parents.add(line.substring(7).trim());
      } else if (line.startsWith("author ")) {
        auth = line.substring(7).trim();
      } else if (line.startsWith("committer ")) {
        comm = line.substring(10).trim();
      }
    }

    if (tree == null || auth == null) {
      throw new OperationException("fatal: malformed commit object");
    }
    this.treeSha1 = tree;
    this.parentSha1s = parents;
    this.author = auth;
    this.committer = comm != null ? comm : auth;
    String msg = msgBuf.toString();
    this.message = msg.endsWith("\n") ? msg.substring(0, msg.length() - 1) : msg;
  }

  public String getTreeSha1() {
    return treeSha1;
  }

  public List<String> getParentSha1s() {
    return parentSha1s;
  }

  public String getAuthor() {
    return author;
  }

  public String getCommitter() {
    return committer;
  }

  public String getMessage() {
    return message;
  }

  private static byte[] serialize(String treeSha1, List<String> parentSha1s,
      String author, String committer, String message) {
    StringBuilder sb = new StringBuilder();
    sb.append("tree ").append(treeSha1).append("\n");
    for (String parent : parentSha1s) {
      sb.append("parent ").append(parent).append("\n");
    }
    sb.append("author ").append(author).append("\n");
    sb.append("committer ").append(committer).append("\n");
    sb.append("\n").append(message).append("\n");
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }
}