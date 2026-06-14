package june;

import java.nio.charset.StandardCharsets;

public class ObjectData {
  protected final String type;
  protected final byte[] data;

  public ObjectData(String type, byte[] data) {
    this.type = type;
    this.data = data;
  }

  public String getType() {
    return type;
  }

  public byte[] getData() {
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
    return new ObjectData(type, data);
  }
}