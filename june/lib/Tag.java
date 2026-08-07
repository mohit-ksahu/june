package june.lib;

import java.io.IOException;
import java.util.List;
import june.Repository;
import june.OperationException;

public final class Tag {
  private Tag() {}

  public static List<String> list(Repository repo) throws IOException {
    return repo.getTags();
  }

  public static void create(Repository repo, String name, String targetRef) throws IOException {
    String sha = repo.resolveRef(targetRef != null ? targetRef : "HEAD");
    if (sha == null) {
      throw new OperationException("Failed to resolve '" + targetRef + "' as a valid ref.");
    }
    repo.createTag(name, sha);
  }

  public static String delete(Repository repo, String name) throws IOException {
    return repo.deleteTagRef(name);
  }
}