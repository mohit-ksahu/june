package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import june.Helper;
import june.Index;
import june.Repository;
import june.XDiff;

public final class Diff {
  private Diff() {}

  public static String diff(Repository repo, boolean staged) throws Exception {
    Index index = new Index(repo.getIndexFile());
    StringBuilder output = new StringBuilder();
    if (staged) {
      Map<String, Helper.FileInfo> headFiles = new HashMap<>();
      String headSha = repo.getHeadCommitSha1();
      if (headSha != null) {
        Helper.collectTreeFiles(repo.readCommit(headSha).getTreeSha1(), "", repo, headFiles);
      }
      for (Index.Entry entry : index.getEntries()) {
        Helper.FileInfo headFile = headFiles.get(entry.path());
        if (headFile == null) {
          appendHeader(output, entry.path(), "new file mode " + entry.mode());
          appendLine(output, "--- /dev/null");
          appendLine(output, "+++ b/" + entry.path());
          byte[] data = repo.read(entry.sha1()).getData();
          if (!Helper.isBinary(data)) {
            for (String line : new String(data, StandardCharsets.UTF_8).split("\n", -1)) {
              appendLine(output, "+" + line);
            }
          }
        } else if (!headFile.sha1().equals(entry.sha1())) {
          appendHeader(output, entry.path(), null);
          appendLine(output, "--- a/" + entry.path());
          appendLine(output, "+++ b/" + entry.path());
          appendDiff(output, repo.read(headFile.sha1()).getData(), repo.read(entry.sha1()).getData());
        }
      }
      for (var headEntry : headFiles.entrySet()) {
        if (index.getEntry(headEntry.getKey()) == null) {
          appendHeader(output, headEntry.getKey(), "deleted file mode " + headEntry.getValue().mode());
          appendLine(output, "--- a/" + headEntry.getKey());
          appendLine(output, "+++ /dev/null");
          byte[] data = repo.read(headEntry.getValue().sha1()).getData();
          if (!Helper.isBinary(data)) {
            for (String line : new String(data, StandardCharsets.UTF_8).split("\n", -1)) {
              appendLine(output, "-" + line);
            }
          }
        }
      }
    } else {
      for (Index.Entry entry : index.getEntries()) {
        File targetFile = new File(repo.getRootDir(), entry.path());
        boolean exists = targetFile.exists() || Files.isSymbolicLink(targetFile.toPath());
        if (!exists) {
          appendHeader(output, entry.path(), "deleted file mode " + entry.mode());
          appendLine(output, "--- a/" + entry.path());
          appendLine(output, "+++ /dev/null");
          byte[] data = repo.read(entry.sha1()).getData();
          if (!Helper.isBinary(data)) {
            for (String line : new String(data, StandardCharsets.UTF_8).split("\n", -1)) {
              appendLine(output, "-" + line);
            }
          }
        } else {
          String currentSha = Helper.entrySha1(targetFile, entry.mode());
          if (!currentSha.equals(entry.sha1())) {
            appendHeader(output, entry.path(), null);
            appendLine(output, "--- a/" + entry.path());
            appendLine(output, "+++ b/" + entry.path());
            byte[] oldBytes = repo.read(entry.sha1()).getData();
            byte[] newBytes = Files.isSymbolicLink(targetFile.toPath())
                ? Files.readSymbolicLink(targetFile.toPath()).toString().getBytes(StandardCharsets.UTF_8)
                : Files.readAllBytes(targetFile.toPath());
            appendDiff(output, oldBytes, newBytes);
          }
        }
      }
    }
    return output.toString();
  }

  private static void appendHeader(StringBuilder sb, String path, String extra) {
    sb.append("diff --git a/").append(path).append(" b/").append(path).append("\n");
    if (extra != null) {
      sb.append(extra).append("\n");
    }
  }

  private static void appendLine(StringBuilder sb, String text) {
    sb.append(text).append("\n");
  }

  private static void appendDiff(StringBuilder sb, byte[] bytes, byte[] newBytes) {
    if (Helper.isBinary(bytes) || Helper.isBinary(newBytes)) {
      sb.append("Binary files differ\n");
    } else {
      for (String line : XDiff.diffLines(
          Arrays.asList(new String(bytes, StandardCharsets.UTF_8).split("\n", -1)),
          Arrays.asList(new String(newBytes, StandardCharsets.UTF_8).split("\n", -1)))) {
        appendLine(sb, line);
      }
    }
  }
}