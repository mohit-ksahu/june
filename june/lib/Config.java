package june.lib;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import june.Repository;
import june.OperationException;

public final class Config {
  private Config() {}

  private static File configFile(Repository repo) {
    return new File(repo.getRepoDir(), "config");
  }

  private static java.util.Properties load(Repository repo) throws IOException {
    java.util.Properties properties = new java.util.Properties();
    File file = configFile(repo);
    if (file.isFile()) {
      try (java.io.Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
        properties.load(reader);
      }
    }
    return properties;
  }

  public static String get(Repository repo, String key) throws IOException {
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
}