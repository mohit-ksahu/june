package june;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Hash {
  private static final HexFormat HEX = HexFormat.of();

  public static MessageDigest digest() throws Exception {
    return MessageDigest.getInstance("SHA-1");
  }

  public static String hash(byte[] data) throws Exception {
    return HEX.formatHex(digest().digest(data));
  }

  public static String hash(File file) throws Exception {
    MessageDigest digest = digest();
    digest.update(("blob " + file.length() + "\0").getBytes(StandardCharsets.UTF_8));
    try (InputStream in = new FileInputStream(file);
        OutputStream out = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
      in.transferTo(out);
      return HEX.formatHex(digest.digest());
    }
  }

  public static String toHex(byte[] bytes) {
    return HEX.formatHex(bytes);
  }

  public static String toHex(byte[] bytes, int from, int to) {
    return HEX.formatHex(bytes, from, to);
  }

  public static byte[] fromHex(String hex) {
    return HEX.parseHex(hex);
  }
}
