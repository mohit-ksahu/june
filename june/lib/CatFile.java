package june.lib;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import june.Commit;
import june.Helper;
import june.Modes;
import june.ObjectData;
import june.ObjectStore.ObjectStream;
import june.ObjectTypes;
import june.OperationException;
import june.Repository;
import june.Tree;

public final class CatFile {
  private CatFile() {}

  public static String catFile(Repository repo, String ref) throws IOException {
    String sha = ref.equals("HEAD") ? repo.getHeadCommitSha1() : null;
    if (sha == null) {
      if (repo.branchExists(ref)) {
        sha = repo.readBranchRef(ref);
      } else if (repo.tagExists(ref)) {
        sha = repo.readTagRef(ref);
      } else {
        sha = Helper.resolveShortSha1(repo.getRepoDir(), ref);
      }
    }
    if (sha == null) {
      throw new OperationException("object " + ref + " not found");
    }
    try (ObjectStream stream = repo.getObjectStream(sha)) {
      if (stream.type().equals(ObjectTypes.BLOB)) {
        return new String(stream.inputStream().readAllBytes(), StandardCharsets.UTF_8);
      }
    }
    ObjectData item = repo.read(sha);
    if (item instanceof Commit c) {
      return new String(c.getData(), StandardCharsets.UTF_8);
    }
    if (item instanceof Tree t) {
      StringBuilder sb = new StringBuilder();
      for (Tree.Entry e : t.getEntries()) {
        if (sb.length() > 0) {
          sb.append("\n");
        }
        String type = e.mode().equals(Modes.TREE) ? ObjectTypes.TREE : ObjectTypes.BLOB;
        sb.append(e.mode()).append(" ").append(type).append(" ").append(e.sha1())
          .append("\t").append(e.name());
      }
      return sb.toString();
    }
    return "";
  }
}