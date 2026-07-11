package june;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XDiff {
  private static final int CONTEXT_LINES = 3;

  private static class DiffOp {
    final char type;
    final int oldLine;
    final int newLine;
    final String text;

    DiffOp(char type, int oLine, int nLine, String text) {
      this.type = type;
      this.oldLine = oLine;
      this.newLine = nLine;
      this.text = text;
    }
  }

  public static List<String> diffLines(List<String> originalLines, List<String> newLines) {
    return diffLines(originalLines, newLines, false);
  }

  public static List<String> diffLines(List<String> originalLines, List<String> newLines, boolean fullContext) {
    int n = originalLines.size();
    int m = newLines.size();
    int max = n + m;

    if (max == 0) {
      return new ArrayList<>();
    }


    int[] v = new int[2 * max + 1];
    List<int[]> history = new ArrayList<>();

    v[max + 1] = 0;
    boolean found = false;
    int d;

    for (d = 0; d <= max; d++) {
      int[] vClone = v.clone();

      for (int k = -d; k <= d; k += 2) {
        boolean down =
            (k == -d || (k != d && vClone[max + k - 1] < vClone[max + k + 1]));
        int kPrev = down ? k + 1 : k - 1;

        int xStart = vClone[max + kPrev];
        int x = down ? xStart : xStart + 1;
        int y = x - k;

        while (x < n && y < m && originalLines.get(x).equals(newLines.get(y))) {
          x++;
          y++;
        }

        v[max + k] = x;

        if (x >= n && y >= m) {
          found = true;
          break;
        }
      }
      history.add(v.clone());
      if (found) {
        break;
      }
    }

    List<DiffOp> ops = new ArrayList<>();
    int x = n;
    int y = m;

    for (int step = d; step >= 1; step--) {
      int k = x - y;
      int[] vPrev = history.get(step - 1);
      boolean down =
          (k == -step || (k != step && vPrev[max + k - 1] < vPrev[max + k + 1]));
      int kPrev = down ? k + 1 : k - 1;

      int xPrev = vPrev[max + kPrev];
      int xTrans = down ? xPrev : xPrev + 1;

      while (x > xTrans) {
        x--;
        y--;
        ops.add(new DiffOp(' ', x + 1, y + 1, originalLines.get(x)));
      }

      if (down) {
        y--;
        ops.add(new DiffOp('+', -1, y + 1, newLines.get(y)));
      } else {
        x--;
        ops.add(new DiffOp('-', x + 1, -1, originalLines.get(x)));
      }

      x = xPrev;
      y = xPrev - kPrev;
    }

    while (x > 0 && y > 0) {
      x--;
      y--;
      ops.add(new DiffOp(' ', x + 1, y + 1, originalLines.get(x)));
    }

    Collections.reverse(ops);

    if (fullContext) {
      List<String> output = new ArrayList<>();
      output.add("@@ -1," + n + " +1," + m + " @@");
      for (DiffOp op : ops) {
        output.add(op.type + op.text);
      }
      return output;
    }

    List<String> output = new ArrayList<>();
    int i = 0;
    while (i < ops.size()) {
      while (i < ops.size() && ops.get(i).type == ' ') {
        i++;
      }
      if (i >= ops.size()) {
        break;
      }

      int start = Math.max(0, i - CONTEXT_LINES);
      int end = i;

      while (end < ops.size()) {
        int nextChange = end + 1;
        while (nextChange < ops.size() && ops.get(nextChange).type == ' ') {
          nextChange++;
        }
        if (nextChange >= ops.size() || nextChange - end > 2 * CONTEXT_LINES) {
          end = Math.min(ops.size() - 1, end + CONTEXT_LINES);
          break;
        }
        end = nextChange;
      }

      int oldStart = -1;
      int newStart = -1;
      int oldCount = 0;
      int newCount = 0;

      for (int j = start; j <= end; j++) {
        DiffOp op = ops.get(j);
        if (oldStart == -1 && op.oldLine != -1) {
          oldStart = op.oldLine;
        }
        if (newStart == -1 && op.newLine != -1) {
          newStart = op.newLine;
        }
        switch (op.type) {
          case ' ' -> {
            oldCount++;
            newCount++;
          }
          case '-' -> oldCount++;
          case '+' -> newCount++;
          default -> {}
        }
      }

      if (oldStart == -1) {
        oldStart = 1;
      }
      if (newStart == -1) {
        newStart = 1;
      }

      output.add("@@ -" + oldStart + "," + oldCount + " +" + newStart + "," + newCount + " @@");

      for (int j = start; j <= end; j++) {
        DiffOp op = ops.get(j);
        output.add(op.type + op.text);
      }

      i = end + 1;
    }

    return output;
  }
}
