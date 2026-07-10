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

  public static String reset(Repository repo, String target) throws IOException {
    String sha;
    if (target == null || target.equalsIgnoreCase("HEAD")) {
      sha = repo.getHeadCommitSha1();
      if (sha == null) {
        throw new OperationException("fatal: HEAD is empty; nothing to reset to");
      }
    } else {
      sha = Helper.resolveShortSha1(repo.getRepoDir(), target);
      if (sha == null) {
        throw new OperationException("fatal: commit '" + target + "' not found");
      }
    }
    Commit commit = repo.readCommit(sha);
    Map<String, Helper.FileInfo> files = new HashMap<>();
    Helper.collectTreeFiles(commit.getTreeSha1(), "", repo, files);
    Helper.syncWorkingTreeFromCommit(repo, files);
    repo.updateHeadRefOrCommit(sha);
    return "HEAD is now at " + sha.substring(0, 7) + " " + commit.getMessage().split("\n")[0];
  }
}