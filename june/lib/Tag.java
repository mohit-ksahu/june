package june.lib;

import java.io.IOException;
import java.util.List;
import june.Repository;
import june.OperationException;

public final class Tag {
  private Tag() {}

  public static List<String> list(Repository repo) {
    return repo.getTags();
  }

  public static void create(Repository repo, String name, String sha) throws IOException {
    if (repo.tagExists(name)) {
      throw new OperationException("fatal: tag '" + name + "' already exists");
    }
    repo.createTag(name, sha);
  }

  public static String delete(Repository repo, String name) throws IOException {
    return repo.deleteTagRef(name);
  }
}