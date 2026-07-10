package june.lib;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import june.Repository;
import june.Index;
import june.IgnoreRules;
import june.Commit;
import june.Helper;

public final class Status {

  public enum ChangeType { ADDED, DELETED, MODIFIED }

  public record FileChange(String path, ChangeType type) {}

  public record StatusResult(
      String branch, List<FileChange> staged, List<FileChange> unstaged, List<String> untracked) {}

  private Status() {}

  public static StatusResult status(Repository repo) throws IOException {
    Index index = new Index(repo.getIndexFile());
    File rootDir = repo.getRootDir();
    var ignorePatterns = IgnoreRules.load(rootDir);

    Map<String, Helper.FileInfo> headFiles = new HashMap<>();
    String headSha = repo.getHeadCommitSha1();
    if (headSha != null) {
      Commit headCommit = repo.readCommit(headSha);
      Helper.collectTreeFiles(headCommit.getTreeSha1(), "", repo, headFiles);
    }

    List<File> workspaceFilesList = new ArrayList<>();
    Helper.collectWorkspaceFiles(rootDir, rootDir, workspaceFilesList, ignorePatterns);
    Map<String, File> workspaceFilesMap = new HashMap<>();
    for (File file : workspaceFilesList) {
      workspaceFilesMap.put(
          rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/'),
          file);
    }

    List<FileChange> staged = new ArrayList<>();
    for (Index.Entry cacheEntry : index.getEntries()) {
      Helper.FileInfo headFile = headFiles.get(cacheEntry.path());
      if (headFile == null) {
        staged.add(new FileChange(cacheEntry.path(), ChangeType.ADDED));
      } else if (!headFile.sha1().equals(cacheEntry.sha1())) {
        staged.add(new FileChange(cacheEntry.path(), ChangeType.MODIFIED));
      }
    }
    for (String headPath : headFiles.keySet()) {
      if (index.getEntry(headPath) == null) {
        staged.add(new FileChange(headPath, ChangeType.DELETED));
      }
    }

    List<FileChange> unstaged = new ArrayList<>();
    Set<String> untrackedSet = new LinkedHashSet<>();
    for (var workspaceEntry : workspaceFilesMap.entrySet()) {
      Index.Entry indexEntry = index.getEntry(workspaceEntry.getKey());
      if (indexEntry == null) {
        untrackedSet.add(workspaceEntry.getKey());
      } else {
        File wf = workspaceEntry.getValue();
        String currentMode = Helper.entryMode(wf);
        String currentSha = Helper.entrySha1(wf, currentMode);
        if (!currentSha.equals(indexEntry.sha1()) || !currentMode.equals(indexEntry.mode())) {
          unstaged.add(new FileChange(workspaceEntry.getKey(), ChangeType.MODIFIED));
        }
      }
    }
    for (Index.Entry indexEntry : index.getEntries()) {
      if (!workspaceFilesMap.containsKey(indexEntry.path())) {
        unstaged.add(new FileChange(indexEntry.path(), ChangeType.DELETED));
      }
    }

    String branch;
    String currentBranch = repo.getCurrentBranch();
    if (currentBranch != null) {
      branch = "On branch " + currentBranch;
    } else if (headSha != null) {
      branch = "HEAD detached at " + headSha.substring(0, 7);
    } else {
      branch = "No commits yet.";
    }

    return new StatusResult(branch, staged, unstaged, new ArrayList<>(untrackedSet));
  }
}