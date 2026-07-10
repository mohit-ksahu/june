package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import june.Repository;
import june.OperationException;

public final class Config {
  private Config() {}

  private static File configFile(Repository repo) {
    return new File(repo.getRepoDir(), "config");
  }

  private static java.util.Properties load(Repository repo) {
    java.util.Properties properties = new java.util.Properties();
    File file = configFile(repo);
    if (file.exists()) {
      try (java.io.Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
        properties.load(reader);
      } catch (IOException e) {
        throw new OperationException("fatal: could not read config: " + e.getMessage());
      }
    }
    return properties;
  }

  public static String get(Repository repo, String key) {
    if (!repo.exists()) {
      return null;
    }
    return load(repo).getProperty(key);
  }

  public static void set(Repository repo, String key, String value) throws IOException {
    java.util.Properties properties = load(repo);
    properties.setProperty(key, value);
    try (java.io.Writer writer = Files.newBufferedWriter(
        configFile(repo).toPath(), StandardCharsets.UTF_8)) {
      properties.store(writer, "June Configuration");
    }
  }

  public static Map<String, String> all(Repository repo) {
    Map<String, String> result = new java.util.HashMap<>();
    if (!repo.exists()) {
      return result;
    }
    java.util.Properties p = load(repo);
    for (String key : p.stringPropertyNames()) {
      result.put(key, p.getProperty(key));
    }
    return result;
  }
}
