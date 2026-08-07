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

  public record Rule(boolean negate, boolean root, boolean containsSlash, java.util.regex.Pattern pattern, java.util.regex.Pattern segmentPattern) {}

  public static Rule compileRule(String pattern) {
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
    boolean containsSlash = glob.contains("/");
    String regex = globToRegex(glob);
    java.util.regex.Pattern mainPattern = root
        ? java.util.regex.Pattern.compile("^" + regex + "(?:/.*)?$")
        : (containsSlash ? java.util.regex.Pattern.compile("^(?:.*/)?" + regex + "(?:/.*)?$") : null);
    java.util.regex.Pattern segPattern = !root && !containsSlash ? java.util.regex.Pattern.compile("^" + regex + "$") : null;
    return new Rule(negate, root, containsSlash, mainPattern, segPattern);
  }

  public static List<Rule> loadRules(File rootDir) throws IOException {
    List<Rule> rules = new ArrayList<>();
    File ignoreFile = new File(rootDir, IGNORE_FILE_NAME);
    if (!ignoreFile.isFile()) {
      return rules;
    }
    for (String line : Files.readAllLines(ignoreFile.toPath(), StandardCharsets.UTF_8)) {
      line = line.trim();
      if (!line.isEmpty() && !line.startsWith("#")) {
        Rule r = compileRule(line);
        if (r != null) {
          rules.add(r);
        }
      }
    }
    return rules;
  }

  public static boolean isIgnoredRules(String path, List<Rule> rules) {
    path = normalize(path);
    boolean ignored = false;
    String[] segments = null;
    for (Rule rule : rules) {
      boolean matches = false;
      if (rule.pattern != null) {
        matches = rule.pattern.matcher(path).matches();
      } else if (rule.segmentPattern != null) {
        if (segments == null) {
          segments = path.split("/");
        }
        for (String segment : segments) {
          if (rule.segmentPattern.matcher(segment).matches()) {
            matches = true;
            break;
          }
        }
      }
      if (matches) {
        ignored = !rule.negate;
      }
    }
    return ignored;
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