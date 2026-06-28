package june;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Tree extends ObjectData {
  public record Entry(String mode, String name, String sha1) implements Comparable<Entry> {

    @Override
    public int compareTo(Entry o) {
      return this.name.compareTo(o.name);
    }
  }

  private final List<Entry> entries;

  public Tree(List<Entry> entries) {
    super(ObjectTypes.TREE, serialize(entries));
    this.entries = entries;
  }

  public Tree(byte[] rawData) {
    super(ObjectTypes.TREE, rawData);
    this.entries = parseEntries(rawData);
  }

  public List<Entry> getEntries() {
    return entries;
  }

  private static byte[] serialize(List<Entry> entries) {
    Collections.sort(entries);
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    for (Entry e : entries) {
      out.writeBytes((e.mode + " " + e.name + "\0").getBytes(StandardCharsets.UTF_8));
      out.writeBytes(Sha1.fromHex(e.sha1));
    }
    return out.toByteArray();
  }

  private static List<Entry> parseEntries(byte[] data) {
    List<Entry> list = new ArrayList<>();
    int i = 0;
    while (i < data.length) {
      int sp = indexOf(data, (byte) ' ', i);
      if (sp == -1) {
        break;
      }
      String mode = new String(data, i, sp - i, StandardCharsets.UTF_8);

      int nul = indexOf(data, (byte) 0, sp + 1);
      if (nul == -1) {
        break;
      }
      String name = new String(data, sp + 1, nul - sp - 1, StandardCharsets.UTF_8);

      if (nul + 21 > data.length) {
        throw new IllegalArgumentException("Malformed tree: truncated SHA-1");
      }
      byte[] hash = new byte[20];
      System.arraycopy(data, nul + 1, hash, 0, 20);

      list.add(new Entry(mode, name, Sha1.toHex(hash)));
      i = nul + 21;
    }
    return list;
  }

  private static int indexOf(byte[] data, byte target, int from) {
    for (int i = from; i < data.length; i++) {
      if (data[i] == target) {
        return i;
      }
    }
    return -1;
  }
}