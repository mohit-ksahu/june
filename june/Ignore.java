package june;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Ignore {
  public static final String IGNORE_FILE_NAME = ".juneignore";

  public record Rule(boolean negate, Pattern pattern, Pattern segmentPattern) {}

  public static Rule compile(String pattern) {
    boolean negate = pattern.startsWith("!");
    String glob = normalize(negate ? pattern.substring(1) : pattern);
    if (glob.isEmpty()) {
      return null;
    }
    boolean root = glob.startsWith("/");
    if (root) {
      glob = glob.substring(1);
    }
    if (glob.endsWith("/")) {
      glob = glob.substring(0, glob.length() - 1);
    }
    boolean slashed = glob.contains("/");
    String regex = regex(glob);
    Pattern mainPattern = root
        ? Pattern.compile("^" + regex + "(?:/.*)?$")
        : (slashed ? Pattern.compile("^(?:.*/)?" + regex + "(?:/.*)?$") : null);
    Pattern segPattern = !root && !slashed
        ? Pattern.compile("^" + regex + "$") : null;
    return new Rule(negate, mainPattern, segPattern);
  }

  public static List<Rule> load(File rootDir) throws IOException {
    List<Rule> rules = new ArrayList<>();
    File ignoreFile = new File(rootDir, IGNORE_FILE_NAME);
    if (!ignoreFile.isFile()) {
      return rules;
    }
    for (String line : Files.readAllLines(ignoreFile.toPath(), StandardCharsets.UTF_8)) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      Rule rule = compile(line);
      if (rule != null) {
        rules.add(rule);
      }
    }
    return rules;
  }

  public static boolean isIgnored(String path, List<Rule> rules) {
    path = normalize(path);
    boolean ignored = false;
    String[] segments = null;
    for (Rule rule : rules) {
      if (rule.segmentPattern != null && segments == null) {
        segments = path.split("/");
      }
      if (matches(rule, path, segments)) {
        ignored = !rule.negate;
      }
    }
    return ignored;
  }

  private static boolean matches(Rule rule, String path, String[] segments) {
    if (rule.pattern != null) {
      return rule.pattern.matcher(path).matches();
    }
    if (rule.segmentPattern == null || segments == null) {
      return false;
    }
    return segment(rule.segmentPattern, segments);
  }

  private static boolean segment(Pattern pattern, String[] segments) {
    for (String segment : segments) {
      if (pattern.matcher(segment).matches()) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String path) {
    return File.separatorChar == '/' ? path : path.replace(File.separatorChar, '/');
  }

  private static String regex(String glob) {
    StringBuilder regex = new StringBuilder();
    for (int i = 0; i < glob.length(); i++) {
      char ch = glob.charAt(i);
      if (ch == '*') {
        i = wildcard(glob, i, regex);
      } else if (ch == '?') {
        regex.append("[^/]");
      } else if ("\\.[]{}()+-^$|".indexOf(ch) != -1) {
        regex.append("\\").append(ch);
      } else {
        regex.append(ch);
      }
    }
    return regex.toString();
  }

  private static int wildcard(String glob, int i, StringBuilder regex) {
    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
      regex.append(".*");
      return i + 1;
    }
    regex.append("[^/]*");
    return i;
  }
}
