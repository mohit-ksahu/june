package june;

import java.nio.charset.StandardCharsets;

public class ObjectData {
  public static final String BLOB = "blob";
  public static final String TREE = "tree";
  public static final String COMMIT = "commit";

  protected final String type;
  protected final byte[] data;

  public ObjectData(String type, byte[] data) {
    this.type = type;
    this.data = data;
  }

  public String type() {
    return type;
  }

  public byte[] data() {
    return data;
  }

  public byte[] serialize() {
    byte[] header = (type + " " + data.length + "\0").getBytes(StandardCharsets.UTF_8);
    byte[] result = new byte[header.length + data.length];
    System.arraycopy(header, 0, result, 0, header.length);
    System.arraycopy(data, 0, result, header.length, data.length);
    return result;
  }

  public static ObjectData create(String type, byte[] data) {
    return switch (type) {
      case BLOB -> new ObjectData(BLOB, data);
      case TREE -> new Tree(data);
      case COMMIT -> new Commit(data);
      default -> throw new OperationException("Unknown object type: " + type);
    };
  }
}
