package june;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XDiff {
  private static final int CONTEXT_LINES = 3;
  private static final int MAX_LINES = 5_000;

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
    int n = originalLines.size();
    int m = newLines.size();
    int max = n + m;

    if (max == 0) {
      return new ArrayList<>();
    }
    if (max > MAX_LINES) {
      return List.of("@@ file too large to diff (" + max + " lines; limit " + MAX_LINES + ") @@");
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

    List<String> output = new ArrayList<>();
    for (DiffOp op : ops) {
      output.add(op.type + op.text);
    }
    return output;
  }
}