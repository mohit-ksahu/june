package june;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha1 {
  private Sha1() {}

  public static String hash(byte[] data) {
    try {
      return toHex(MessageDigest.getInstance("SHA-1").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-1 not available", e);
    }
  }

  public static String hash(File file) {
    try (InputStream is = new FileInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      digest.update(("blob " + file.length() + "\0").getBytes(StandardCharsets.UTF_8));
      byte[] buf = new byte[8192];
      int n;
      while ((n = is.read(buf)) != -1) {
        digest.update(buf, 0, n);
      }
      return toHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static String objectId(String type, byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      digest.update((type + " " + data.length + "\0").getBytes(StandardCharsets.UTF_8));
      digest.update(data);
      return toHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-1 not available", e);
    }
  }

  public static String toHex(byte[] bytes) {
    return java.util.HexFormat.of().formatHex(bytes);
  }

  public static byte[] fromHex(String hex) {
    return java.util.HexFormat.of().parseHex(hex);
  }
}