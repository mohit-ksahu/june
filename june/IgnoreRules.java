package june;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class IgnoreRules {
  private IgnoreRules() {}

  public static final String IGNORE_FILE_NAME = ".juneignore";

  public static List<String> load(File rootDir) {
    List<String> patterns = new ArrayList<>();
    File ignoreFile = new File(rootDir, IGNORE_FILE_NAME);
    if (ignoreFile.exists()) {
      try {
        for (String line : Files.readAllLines(ignoreFile.toPath(), StandardCharsets.UTF_8)) {
          line = line.trim();
          if (!line.isEmpty() && !line.startsWith("#")) {
            patterns.add(line);
          }
        }
      } catch (IOException ignored) {
      }
    }
    return patterns;
  }

  public static boolean isIgnored(String path, List<String> patterns) {
    path = normalize(path);
    boolean ignored = false;
    for (String pattern : patterns) {
      boolean negate = pattern.startsWith("!");
      String glob = normalize(negate ? pattern.substring(1) : pattern);
      if (matches(path, glob)) {
        ignored = !negate;
      }
    }
    return ignored;
  }

  private static boolean matches(String path, String pattern) {
    if (pattern.isEmpty()) {
      return false;
    }
    boolean root = pattern.startsWith("/");
    if (root) {
      pattern = pattern.substring(1);
    }
    if (pattern.endsWith("/")) {
      pattern = pattern.substring(0, pattern.length() - 1);
    }
    String regex = globToRegex(pattern);
    if (root) {
      return path.matches("^" + regex + "(?:/.*)?$");
    }
    if (pattern.contains("/")) {
      return path.matches("^(?:.*/)?" + regex + "(?:/.*)?$");
    }
    for (String segment : path.split("/")) {
      if (segment.matches("^" + regex + "$")) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String path) {
    return File.separatorChar == '/' ? path : path.replace(File.separatorChar, '/');
  }

  private static String globToRegex(String glob) {
    StringBuilder regex = new StringBuilder();
    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      if (c == '*') {
        if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
          regex.append(".*");
          i++;
        } else {
          regex.append("[^/]*");
        }
      } else if (c == '?') {
        regex.append("[^/]");
      } else if ("\\.[]{}()+-^$|".indexOf(c) != -1) {
        regex.append("\\").append(c);
      } else {
        regex.append(c);
      }
    }
    return regex.toString();
  }
}