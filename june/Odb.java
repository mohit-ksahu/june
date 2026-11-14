package june;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class Odb {
  public static final String OBJECTS_DIR_NAME = "objects";
  private static final int BUFFER_SIZE = 64 * 1024;

  private final File objectsDir;

  public Odb(File objectsDir) {
    this.objectsDir = objectsDir;
  }

  public void mkdirs() {
    objectsDir.mkdirs();
  }

  private File objectFile(String hash, boolean createDirs) {
    File dir = new File(objectsDir, hash.substring(0, 2));
    if (createDirs && !dir.exists()) {
      dir.mkdirs();
    }
    return new File(dir, hash.substring(2));
  }

  public String write(ObjectData object) throws Exception {
    byte[] serialized = object.serialize();
    String hash = Hash.hash(serialized);
    File file = objectFile(hash, true);
    if (file.exists()) {
      return hash;
    }
    File tmp = File.createTempFile("tmp_", "", file.getParentFile());
    tmp.deleteOnExit();
    try (DeflaterOutputStream out = new DeflaterOutputStream(
        new BufferedOutputStream(new FileOutputStream(tmp), 16384))) {
      out.write(serialized);
    }
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
    return hash;
  }

  public String writeBlob(File inputFile) throws Exception {
    long len = inputFile.length();
    if (len <= BUFFER_SIZE) {
      byte[] bytes = Files.readAllBytes(inputFile.toPath());
      return write(new ObjectData(ObjectData.BLOB, bytes));
    }
    byte[] header = ("blob " + len + "\0").getBytes(StandardCharsets.UTF_8);
    MessageDigest digest = Hash.digest();
    File tmp = File.createTempFile("tmp_", "", objectsDir);
    tmp.deleteOnExit();
    try (DigestOutputStream out = new DigestOutputStream(
        new DeflaterOutputStream(new BufferedOutputStream(new FileOutputStream(tmp), BUFFER_SIZE)), digest);
        InputStream in = new BufferedInputStream(new FileInputStream(inputFile), BUFFER_SIZE)) {
      out.write(header);
      in.transferTo(out);
    }
    String hash = Hash.toHex(digest.digest());
    File dest = objectFile(hash, true);
    if (!dest.exists()) {
      Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
    } else {
      tmp.delete();
    }
    return hash;
  }

  public ObjectData read(String hash) throws IOException {
    try (ObjectStream stream = readStream(hash)) {
      int size = (int) stream.size();
      byte[] data = stream.inputStream().readNBytes(size);
      return ObjectData.create(stream.type(), data);
    }
  }

  public ObjectStream readStream(String hash) throws IOException {
    if (hash == null || hash.length() != 40) {
      throw new OperationException("Invalid hash: " + hash);
    }
    File file = objectFile(hash, false);
    if (!file.exists()) {
      throw new FileNotFoundException("Object not found: " + hash);
    }
    InputStream fileIn = new FileInputStream(file);
    InputStream inflated = new InflaterInputStream(new BufferedInputStream(fileIn, BUFFER_SIZE));
    byte[] headerBuf = new byte[64];
    int len = 0;
    int b;
    while ((b = inflated.read()) != -1 && b != 0) {
      if (len < headerBuf.length) {
        headerBuf[len++] = (byte) b;
      }
    }
    String header = new String(headerBuf, 0, len, StandardCharsets.UTF_8);
    int space = header.indexOf(' ');
    if (space == -1) {
      inflated.close();
      throw new IOException("Malformed object header: " + header);
    }
    return new ObjectStream(
        header.substring(0, space), Long.parseLong(header.substring(space + 1)), inflated);
  }

  public void write(String hash, File dest) throws IOException {
    if (dest.getParentFile() != null) {
      dest.getParentFile().mkdirs();
    }
    try (ObjectStream stream = readStream(hash);
        OutputStream out = new BufferedOutputStream(new FileOutputStream(dest), BUFFER_SIZE)) {
      stream.inputStream().transferTo(out);
    }
  }

  public record ObjectStream(String type, long size, InputStream inputStream)
      implements AutoCloseable {
    @Override
    public void close() throws IOException {
      inputStream.close();
    }
  }
}
