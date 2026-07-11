import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import june.Repository;

public final class Diff {
  public static void run(Repository repo, String[] args) throws IOException {
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

    boolean hasChanges = false;
    int size = lines.size();
    boolean[] keep = new boolean[size];

    for (int index = 0; index < size; index++) {
      String text = lines.get(index);
      if (text.startsWith("-") || text.startsWith("+")) {
        hasChanges = true;
        for (int subIndex = Math.max(0, index - 3); subIndex <= Math.min(size - 1, index + 3); subIndex++) {
          keep[subIndex] = true;
        }
      }
    }

    if (!hasChanges || size == 0) {
      for (String h : header) {
        appendColorLine(sb, h);
      }
      return;
    }

    for (String h : header) {
      appendColorLine(sb, h);
    }

    int line = 1, newLine = 1, index = 0;
    while (index < size) {
      String text = lines.get(index);

      if (!keep[index]) {
        if (text.startsWith("-")) line++;
        else if (text.startsWith("+")) newLine++;
        else { line++; newLine++; }
        index++;
        continue;
      }

      int startIndex = index, start = line, newStart = newLine, count = 0, newCount = 0;
      while (index < size && keep[index]) {
        String innerText = lines.get(index);
        if (innerText.startsWith("-")) { count++; line++; }
        else if (innerText.startsWith("+")) { newCount++; newLine++; }
        else { count++; newCount++; line++; newLine++; }
        index++;
      }

      String hunkHeader = "@@ -" + start + "," + count + " +" + newStart + "," + newCount + " @@";
      appendColorLine(sb, hunkHeader);

      for (int subIndex = startIndex; subIndex < index; subIndex++) {
        appendColorLine(sb, lines.get(subIndex));
      }
    }
  }
}