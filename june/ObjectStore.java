package june;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class ObjectStore {
  public static final String OBJECTS_DIR_NAME = "objects";
  private static final int BUFFER_SIZE = 8192;

  private final File objectsDir;

  public ObjectStore(File repoDir) {
    this.objectsDir = new File(repoDir, OBJECTS_DIR_NAME);
  }

  public void mkdirs() {
    objectsDir.mkdirs();
  }

  private File getObjectFile(String sha1, boolean createDirs) {
    File dir = new File(objectsDir, sha1.substring(0, 2));
    if (createDirs && !dir.exists()) {
      dir.mkdirs();
    }
    return new File(dir, sha1.substring(2));
  }

  public String write(ObjectData object) throws Exception {
    byte[] serialized = object.serialize();
    String sha1 = Sha1.hash(serialized);
    File file = getObjectFile(sha1, true);
    if (!file.exists()) {
      File tmp = File.createTempFile("tmp_", "", file.getParentFile());
      try (DeflaterOutputStream out = new DeflaterOutputStream(new FileOutputStream(tmp))) {
        out.write(serialized);
      }
      Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
    }
    return sha1;
  }

  public String writeBlob(File inputFile) throws Exception {
    byte[] header = ("blob " + inputFile.length() + "\0").getBytes(StandardCharsets.UTF_8);
    MessageDigest digest = Sha1.createDigest();

    File tmp = File.createTempFile("tmp_", "", objectsDir);
    try (DigestOutputStream out = new DigestOutputStream(
        new DeflaterOutputStream(new FileOutputStream(tmp)), digest)) {
      out.write(header);
      try (InputStream in = new FileInputStream(inputFile)) {
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = in.read(buf)) != -1) {
          out.write(buf, 0, n);
        }
      }
    }

    String sha1 = Sha1.toHex(digest.digest());
    File dest = getObjectFile(sha1, true);
    if (!dest.exists()) {
      Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
    } else {
      tmp.delete();
    }
    return sha1;
  }

  public ObjectData read(String sha1) throws IOException {
    try (ObjectStream s = getObjectStream(sha1)) {
      int initialSize = (s.size() > 0 && s.size() <= Integer.MAX_VALUE - 8) ? (int) s.size() : 8192;
      ByteArrayOutputStream buf = new ByteArrayOutputStream(initialSize);
      byte[] tmp = new byte[BUFFER_SIZE];
      int n;
      while ((n = s.inputStream().read(tmp)) != -1) {
        buf.write(tmp, 0, n);
      }
      return ObjectData.create(s.type(), buf.toByteArray());
    }
  }

  public ObjectStream getObjectStream(String sha1) throws IOException {
    if (sha1 == null || sha1.length() != 40) {
      throw new IllegalArgumentException("Invalid SHA-1: " + sha1);
    }
    File file = getObjectFile(sha1, false);
    if (!file.exists()) {
      throw new FileNotFoundException("Object not found: " + sha1);
    }

    InputStream fileIn = new FileInputStream(file);
    InputStream inflated = new InflaterInputStream(new java.io.BufferedInputStream(fileIn, BUFFER_SIZE));
    ByteArrayOutputStream hdr = new ByteArrayOutputStream();
    int b;
    while ((b = inflated.read()) != -1 && b != 0) {
      hdr.write(b);
    }
    String header = hdr.toString(StandardCharsets.UTF_8);
    int sp = header.indexOf(' ');
    if (sp == -1) {
      inflated.close();
      throw new IOException("Malformed object header: " + header);
    }
    return new ObjectStream(
        header.substring(0, sp), Long.parseLong(header.substring(sp + 1)), inflated);
  }

  public void readToFile(String sha1, File dest) throws IOException {
    if (dest.getParentFile() != null) {
      dest.getParentFile().mkdirs();
    }
    try (ObjectStream s = getObjectStream(sha1);
        OutputStream out = new FileOutputStream(dest)) {
      byte[] buf = new byte[BUFFER_SIZE];
      int n;
      while ((n = s.inputStream().read(buf)) != -1) {
        out.write(buf, 0, n);
      }
    }
  }

  public record ObjectStream(String type, long size, java.io.InputStream inputStream)
      implements AutoCloseable {
    @Override
    public void close() throws java.io.IOException {
      inputStream.close();
    }
  }
}