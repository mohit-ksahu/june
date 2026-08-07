import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import june.Repository;

public final class Diff {
  public static void run(Repository repo, String[] args) throws Exception {
    boolean staged = false;
    for (String arg : args) {
      if (arg.equals("--cached") || arg.equals("--staged")) {
        staged = true;
      }
    }
    String result = repo.diff(staged);
    if (!result.isEmpty()) {
      System.out.print(formatCliDiff(result));
    }
  }

  private static String formatCliDiff(String fullDiff) {
    String[] lines = fullDiff.split("\n", -1);
    StringBuilder sb = new StringBuilder();
    List<String> fileHeader = new ArrayList<>();
    List<String> fileLines = new ArrayList<>();

    for (String line : lines) {
      if (line.startsWith("diff --git ")) {
        flushFileDiff(sb, fileHeader, fileLines);
        fileHeader.clear();
        fileLines.clear();
        fileHeader.add(line);
      } else if (fileHeader.isEmpty()) {
        if (!line.isEmpty()) sb.append(line).append("\n");
      } else if (line.startsWith("--- ") || line.startsWith("+++ ") || line.startsWith("index ") || line.startsWith("new file mode ") || line.startsWith("deleted file mode ") || line.startsWith("similarity index ") || line.startsWith("rename from ") || line.startsWith("rename to ")) {
        fileHeader.add(line);
      } else if (line.startsWith("Binary files differ")) {
        fileHeader.add(line);
        flushFileDiff(sb, fileHeader, fileLines);
        fileHeader.clear();
        fileLines.clear();
      } else if (line.startsWith("@@ ")) {
        // Skip global hunk header
      } else if (!line.isEmpty()) {
        fileLines.add(line);
      }
    }
    flushFileDiff(sb, fileHeader, fileLines);
    return sb.toString();
  }

  private static void appendColorLine(StringBuilder sb, String text) {
    if (text.startsWith("--- ") || text.startsWith("-")) {
      sb.append("\u001B[31m").append(text).append("\u001B[0m\n");
    } else if (text.startsWith("+++ ") || text.startsWith("+")) {
      sb.append("\u001B[32m").append(text).append("\u001B[0m\n");
    } else if (text.startsWith("@@ ")) {
      sb.append("\u001B[36m").append(text).append("\u001B[0m\n");
    } else {
      sb.append(text).append("\n");
    }
  }

  private static void flushFileDiff(StringBuilder sb, List<String> header, List<String> lines) {
    if (header.isEmpty()) return;

    for (String h : header) {
      appendColorLine(sb, h);
    }
    if (lines.isEmpty()) {
      return;
    }

    int countOld = 0, countNew = 0;
    for (String line : lines) {
      if (line.startsWith("-")) {
        countOld++;
      } else if (line.startsWith("+")) {
        countNew++;
      } else {
        countOld++;
        countNew++;
      }
    }

    String hunkHeader = "@@ -1," + countOld + " +1," + countNew + " @@";
    appendColorLine(sb, hunkHeader);

    for (String line : lines) {
      appendColorLine(sb, line);
    }
  }
}