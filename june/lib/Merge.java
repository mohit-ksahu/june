package june.lib;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import june.Commit;
import june.Helper;
import june.Index;
import june.OperationException;
import june.Repository;

public final class Merge {
  private Merge() {}

  public static String merge(Repository repo, String target) throws Exception {
    if (target.isEmpty()) {
      throw new OperationException(
          "merge requires a branch name, tag, or commit hash to merge");
    }
    String currentSha = repo.getHeadCommitSha1();
    if (currentSha == null) {
      throw new OperationException("HEAD is empty; cannot merge into an empty branch");
    }
    String targetSha = repo.resolveRef(target);
    if (targetSha == null) {
      throw new OperationException("target '" + target + "' not found");
    }
    if (!(repo.read(targetSha) instanceof Commit)) {
      throw new OperationException("object " + targetSha + " is not a commit");
    }
    if (currentSha.equals(targetSha)) {
      return "Already up to date.";
    }
    if (Helper.isAncestor(repo, currentSha, targetSha)) {
      Map<String, Helper.FileInfo> targetFiles = new HashMap<>();
      Helper.collectTreeFiles(repo.readCommit(targetSha).getTreeSha1(), "", repo, targetFiles);
      Helper.checkConflicts(repo, targetFiles, new Index(repo.getIndexFile()), "merge");
      Helper.syncWorkingTreeFromCommit(repo, targetFiles);
      repo.updateHeadRefOrCommit(targetSha);
      return "Updating " + currentSha.substring(0, 7) + ".." + targetSha.substring(0, 7)
          + "\nFast-forward";
    }
    if (Helper.isAncestor(repo, targetSha, currentSha)) {
      return "Already up to date.";
    }
    throw new OperationException(
        "Not a fast-forward merge (non-fast-forward merges are not supported).");
  }
}