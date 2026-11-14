package june;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Tree extends ObjectData {
  public record Entry(String mode, String name, String hash) implements Comparable<Entry> {
    @Override
    public int compareTo(Entry other) {
      String first = name + (mode.equals(Mode.TREE) ? "/" : "");
      String second = other.name + (other.mode.equals(Mode.TREE) ? "/" : "");
      return first.compareTo(second);
    }
  }

  private final List<Entry> entries;

  public Tree(List<Entry> entries) {
    super(TREE, serialize(entries));
    this.entries = entries;
  }

  public Tree(byte[] rawData) {
    super(TREE, rawData);
    this.entries = parse(rawData);
  }

  public List<Entry> entries() {
    return entries;
  }

  private static byte[] serialize(List<Entry> entries) {
    Collections.sort(entries);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Entry entry : entries) {
      out.writeBytes((entry.mode + " " + entry.name + "\0").getBytes(StandardCharsets.UTF_8));
      out.writeBytes(Hash.fromHex(entry.hash));
    }
    return out.toByteArray();
  }

  private static List<Entry> parse(byte[] data) {
    List<Entry> list = new ArrayList<>();
    int i = 0;
    while (i < data.length) {
      int space = index(data, (byte) ' ', i);
      if (space == -1) {
        break;
      }
      String mode = new String(data, i, space - i, StandardCharsets.UTF_8);

      int nul = index(data, (byte) 0, space + 1);
      if (nul == -1) {
        break;
      }
      String name = new String(data, space + 1, nul - space - 1, StandardCharsets.UTF_8);

      if (nul + 21 > data.length) {
        throw new OperationException("Malformed tree: truncated hash");
      }
      list.add(new Entry(mode, name, Hash.toHex(data, nul + 1, nul + 21)));
      i = nul + 21;
    }
    return list;
  }

  private static int index(byte[] data, byte target, int from) {
    for (int i = from; i < data.length; i++) {
      if (data[i] == target) {
        return i;
      }
    }
    return -1;
  }
}
