package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import june.Commit;
import june.Helper;
import june.Index;
import june.OperationException;
import june.Repository;

public final class Checkout {
  private Checkout() {}

  public static String checkout(Repository repo, String target) throws Exception {
    String commitSha;
    boolean isBranch = false;
    String branchRef = null;
    File branchFile = repo.branchRefFile(target);
    File tagFile = repo.tagRefFile(target);
    if (branchFile.exists()) {
      isBranch = true;
      branchRef = Repository.HEADS_REF_PREFIX + target;
      commitSha = Files.readString(branchFile.toPath()).trim();
    } else {
      commitSha = repo.resolveRef(target);
      if (commitSha == null) {
        throw new OperationException("pathspec '" + target
            + "' did not match any file(s) known to june");
      }
      if (!(repo.read(commitSha) instanceof Commit)) {
        throw new OperationException("object " + commitSha + " is not a commit");
      }
    }
    Commit commit = repo.readCommit(commitSha);
    Map<String, Helper.FileInfo> targetFiles = new HashMap<>();
    Helper.collectTreeFiles(commit.getTreeSha1(), "", repo, targetFiles);
    Index index = new Index(repo.getIndexFile());
    Helper.checkConflicts(repo, targetFiles, index, "checkout");
    Helper.syncWorkingTreeFromCommit(repo, targetFiles);
    if (isBranch) {
      repo.setHeadTarget(branchRef);
      return "Switched to branch '" + target + "'";
    }
    repo.setHeadTarget(commitSha);
    return "Note: checking out '" + target + "'.\nYou are in 'detached HEAD' state.\n"
        + "HEAD is now at " + commitSha.substring(0, 7) + " " + commit.getMessage().split("\n")[0];
  }
}