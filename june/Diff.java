package june;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Diff {
  public static List<String> lines(List<String> original, List<String> updated) {
    int oldCount = original.size();
    int newCount = updated.size();
    int max = oldCount + newCount;
    if (max == 0) {
      return List.of();
    }
    List<int[]> history = new ArrayList<>();
    int depth = path(original, updated, max, history);
    List<String> ops = backtrack(original, updated, max, depth, history);
    return format(oldCount, newCount, ops);
  }

  private static int path(
      List<String> original, List<String> updated, int max, List<int[]> history) {
    int[] v = new int[2 * max + 1];
    v[max + 1] = 0;
    for (int dist = 0; dist <= max; dist++) {
      if (diagonal(original, updated, max, dist, v)) {
        return dist;
      }
      int sliceLen = 2 * dist + 1;
      int[] slice = new int[sliceLen];
      System.arraycopy(v, max - dist, slice, 0, sliceLen);
      history.add(slice);
    }
    return max;
  }

  private static boolean diagonal(
      List<String> original, List<String> updated, int max, int dist, int[] v) {
    int oldLen = original.size();
    int newLen = updated.size();
    for (int k = -dist; k <= dist; k += 2) {
      boolean down = (k == -dist || (k != dist && v[max + k - 1] < v[max + k + 1]));
      int kPrev = down ? k + 1 : k - 1;
      int xStart = v[max + kPrev];
      int xPos = down ? xStart : xStart + 1;
      int yPos = xPos - k;
      while (xPos < oldLen && yPos < newLen && original.get(xPos).equals(updated.get(yPos))) {
        xPos++;
        yPos++;
      }
      v[max + k] = xPos;
      if (xPos >= oldLen && yPos >= newLen) {
        return true;
      }
    }
    return false;
  }

  private static List<String> backtrack(
      List<String> original, List<String> updated, int max, int depth, List<int[]> history) {
    List<String> ops = new ArrayList<>();
    int[] pos = {original.size(), updated.size()};
    for (int step = depth; step >= 1; step--) {
      step(original, updated, max, step, history.get(step - 1), pos, ops);
    }
    while (pos[0] > 0 && pos[1] > 0) {
      pos[0]--;
      pos[1]--;
      ops.add(" " + original.get(pos[0]));
    }
    Collections.reverse(ops);
    return ops;
  }

  private static void step(
      List<String> original, List<String> updated, int max, int step, int[] vPrev,
      int[] pos, List<String> ops) {
    int k = pos[0] - pos[1];
    int offset = max - (step - 1);
    int prevX = (max + k - 1 - offset >= 0 && max + k - 1 - offset < vPrev.length)
        ? vPrev[max + k - 1 - offset] : -1;
    int nextX = (max + k + 1 - offset >= 0 && max + k + 1 - offset < vPrev.length)
        ? vPrev[max + k + 1 - offset] : -1;
    boolean down = (k == -step || (k != step && prevX < nextX));
    int kPrev = down ? k + 1 : k - 1;
    int xPrev = vPrev[max + kPrev - offset];
    int xTrans = down ? xPrev : xPrev + 1;

    while (pos[0] > xTrans) {
      pos[0]--;
      pos[1]--;
      ops.add(" " + original.get(pos[0]));
    }
    if (down) {
      pos[1]--;
      ops.add("+" + updated.get(pos[1]));
    } else {
      pos[0]--;
      ops.add("-" + original.get(pos[0]));
    }
    pos[0] = xPrev;
    pos[1] = xPrev - kPrev;
  }

  private static List<String> format(int oldCount, int newCount, List<String> ops) {
    List<String> output = new ArrayList<>(ops.size() + 1);
    output.add("@@ -1," + oldCount + " +1," + newCount + " @@");
    output.addAll(ops);
    return output;
  }
}
