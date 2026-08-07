package june.lib;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import june.Commit;
import june.Helper;
import june.OperationException;
import june.Repository;

public final class Reset {
  private Reset() {}

  public static String reset(Repository repo, String target) throws Exception {
    String sha = repo.resolveRef(target == null ? "HEAD" : target);
    if (sha == null) {
      throw new OperationException(target == null || target.equalsIgnoreCase("HEAD")
          ? "HEAD is empty; nothing to reset to"
          : "commit '" + target + "' not found");
    }
    Commit commit = repo.readCommit(sha);
    Map<String, Helper.FileInfo> files = new HashMap<>();
    Helper.collectTreeFiles(commit.getTreeSha1(), "", repo, files);
    Helper.syncWorkingTreeFromCommit(repo, files);
    repo.updateHeadRefOrCommit(sha);
    return "HEAD is now at " + sha.substring(0, 7) + " " + commit.getMessage().split("\n")[0];
  }
}