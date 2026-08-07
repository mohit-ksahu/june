package june.lib;

import java.io.IOException;
import java.util.List;
import june.Repository;
import june.OperationException;
import june.Helper;

public final class Branch {

  public record BranchResult(List<String> branches, String current, String message) {}

  private Branch() {}

  public static BranchResult list(Repository repo) throws IOException {
    List<String> branches = repo.getBranches();
    String current = repo.getCurrentBranch();
    if (branches.isEmpty() && current == null) {
      String head = repo.getHeadTarget();
      String defaultBranch = (head != null && head.startsWith(Repository.HEADS_REF_PREFIX))
          ? head.substring(Repository.HEADS_REF_PREFIX.length()).trim()
          : Repository.MAIN_BRANCH;
      return new BranchResult(List.of(defaultBranch), defaultBranch, null);
    }
    String detached = null;
    if (current == null) {
      String sha = repo.getHeadCommitSha1();
      if (sha != null) {
        detached = "(HEAD detached at " + sha.substring(0, 7) + ")";
      }
    }
    return new BranchResult(branches, current != null ? current : detached, null);
  }

  public static String create(Repository repo, String name) throws IOException {
    if (repo.branchExists(name)) {
      throw new OperationException("A branch named '" + name + "' already exists.");
    }
    String sha = repo.getHeadCommitSha1();
    if (sha == null) {
      throw new OperationException("Not a valid object name: '" + name + "'.");
    }
    repo.updateBranchRef(name, sha);
    return "Branch '" + name + "' created at " + sha.substring(0, 7);
  }

  public static String delete(Repository repo, String name, boolean force) throws IOException {
    if (name.equals(repo.getCurrentBranch())) {
      throw new OperationException(
          "Cannot delete branch '" + name + "' which you are currently on.");
    }
    String sha = repo.readBranchRef(name);
    if (sha == null) {
      throw new OperationException("branch '" + name + "' not found.");
    }
    if (!force) {
      String headSha = repo.getHeadCommitSha1();
      if (!Helper.isAncestor(repo, sha, headSha)) {
        throw new OperationException("The branch '" + name + "' is not fully merged.\n"
            + "If you are sure you want to delete it, run 'june branch -D " + name + "'.");
      }
    }
    repo.deleteBranchRef(name);
    return "Deleted branch " + name + " (was " + sha.substring(0, 7) + ").";
  }

  public static String rename(Repository repo, String oldName, String newName) throws IOException {
    if (oldName == null) {
      oldName = repo.getCurrentBranch();
      if (oldName == null) {
        throw new OperationException("Cannot rename detached HEAD.");
      }
    }
    if (!repo.branchExists(oldName)) {
      throw new OperationException("branch '" + oldName + "' not found.");
    }
    if (repo.branchExists(newName) && !oldName.equals(newName)) {
      throw new OperationException("branch '" + newName + "' already exists.");
    }
    String sha = repo.readBranchRef(oldName);
    repo.updateBranchRef(newName, sha);
    if (!oldName.equals(newName)) {
      repo.deleteBranchRef(oldName);
    }
    if (oldName.equals(repo.getCurrentBranch())) {
      repo.setHeadTarget(Repository.HEADS_REF_PREFIX + newName);
    }
    return "Renamed branch '" + oldName + "' to '" + newName + "'";
  }
}