package june;
import java.io.ByteArrayOutputStream;
import java.util.zip.DeflaterOutputStream;
public final class Helper {
  private Helper() {}
  public static byte[] compress(byte[] data) throws java.io.IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    try (DeflaterOutputStream def = new DeflaterOutputStream(buf)) {
      def.write(data);
    }
    return buf.toByteArray();
  }
}
