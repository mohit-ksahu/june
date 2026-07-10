package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import june.Helper;
import june.Index;
import june.Repository;
import june.Sha1;
import june.XDiff;

public final class Diff {
  private Diff() {}

  public static String diff(Repository repo, boolean staged) throws IOException {
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
          appendColorLine(output, "--- /dev/null", true);
          appendColorLine(output, "+++ b/" + entry.path(), false);
          byte[] data = repo.read(entry.sha1()).getData();
          if (!Helper.isBinary(data)) {
            for (String line : new String(data, StandardCharsets.UTF_8).split("\n", -1)) {
              appendColorLine(output, "+" + line, false);
            }
          }
        } else if (!headFile.sha1().equals(entry.sha1())) {
          appendHeader(output, entry.path(), null);
          appendColorLine(output, "--- a/" + entry.path(), true);
          appendColorLine(output, "+++ b/" + entry.path(), false);
          appendDiff(output, repo.read(headFile.sha1()).getData(), repo.read(entry.sha1()).getData());
        }
      }
      for (var headEntry : headFiles.entrySet()) {
        if (index.getEntry(headEntry.getKey()) == null) {
          appendHeader(output, headEntry.getKey(), "deleted file mode " + headEntry.getValue().mode());
          appendColorLine(output, "--- a/" + headEntry.getKey(), true);
          appendColorLine(output, "+++ /dev/null", false);
        }
      }
    } else {
      for (Index.Entry entry : index.getEntries()) {
        File targetFile = new File(repo.getRootDir(), entry.path());
        boolean exists = targetFile.exists() || Files.isSymbolicLink(targetFile.toPath());
        if (!exists) {
          appendHeader(output, entry.path(), "deleted file mode " + entry.mode());
          appendColorLine(output, "--- a/" + entry.path(), true);
          appendColorLine(output, "+++ /dev/null", false);
          byte[] data = repo.read(entry.sha1()).getData();
          if (!Helper.isBinary(data)) {
            for (String line : new String(data, StandardCharsets.UTF_8).split("\n", -1)) {
              appendColorLine(output, "-" + line, true);
            }
          }
        } else {
          String currentSha = Helper.entrySha1(targetFile, entry.mode());
          if (!currentSha.equals(entry.sha1())) {
            appendHeader(output, entry.path(), null);
            appendColorLine(output, "--- a/" + entry.path(), true);
            appendColorLine(output, "+++ b/" + entry.path(), false);
            byte[] oldBytes = repo.read(entry.sha1()).getData();
            byte[] newBytes = entry.mode().equals(june.Modes.SYMLINK)
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

  private static void appendColorLine(StringBuilder sb, String text, boolean red) {
    sb.append(red ? "\u001B[31m" : "\u001B[32m").append(text).append("\u001B[0m").append("\n");
  }

  private static void appendDiff(StringBuilder sb, byte[] oldBytes, byte[] newBytes) {
    if (Helper.isBinary(oldBytes) || Helper.isBinary(newBytes)) {
      sb.append("Binary files differ\n");
    } else {
      for (String line : XDiff.diffLines(
          Arrays.asList(new String(oldBytes, StandardCharsets.UTF_8).split("\n", -1)),
          Arrays.asList(new String(newBytes, StandardCharsets.UTF_8).split("\n", -1)))) {
        if (line.startsWith("-")) {
          appendColorLine(sb, line, true);
        } else if (line.startsWith("+")) {
          appendColorLine(sb, line, false);
        } else {
          sb.append(line).append("\n");
        }
      }
    }
  }
}