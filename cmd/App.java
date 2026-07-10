import java.io.File;
import java.util.List;
import java.util.ArrayList;
import june.Repository;
import june.Sha1;
import june.ObjectData;
import june.ObjectTypes;
import june.Tree;
import june.Modes;
import june.Commit;
import june.ObjectStore;
import june.Index;
import june.IgnoreRules;

public class App {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println("Usage: java App <command> [<args>]");
      System.exit(1);
    }
    String cmd = args[0];
    if (cmd.equals("init")) {
      File repoTarget = new File(".");
      Repository repo = new Repository(repoTarget);
      repo.init();
      System.out.println("Initialized empty June repository in .june/");
      return;
    }
    if (cmd.equals("hash")) {
      if (args.length < 2) {
        System.out.println("Usage: java App hash <string>");
        System.exit(1);
      }
      System.out.println(Sha1.hash(args[1].getBytes()));
      return;
    }
    if (cmd.equals("serialize")) {
      if (args.length < 3) {
        System.out.println("Usage: java App serialize <type> <content>");
        System.exit(1);
      }
      ObjectData data = ObjectData.create(args[1], args[2].getBytes());
      System.out.println("Serialized object type: " + data.getType() + ", length: " + data.serialize().length + " bytes");
      return;
    }
    if (cmd.equals("serialize-tree")) {
      if (args.length < 4) {
        System.out.println("Usage: java App serialize-tree <mode> <name> <sha1>");
        System.exit(1);
      }
      Tree.Entry entry = new Tree.Entry(args[1], args[2], args[3]);
      Tree tree = new Tree(new ArrayList<>(List.of(entry)));
      System.out.println("Serialized tree length: " + tree.serialize().length + " bytes");
      return;
    }
    if (cmd.equals("serialize-commit")) {
      if (args.length < 4) {
        System.out.println("Usage: java App serialize-commit <treeSha1> <author> <message>");
        System.exit(1);
      }
      Commit commit = new Commit(args[1], List.of(), args[2], args[2], args[3]);
      System.out.println("Serialized commit:\n" + new String(commit.serialize()));
      return;
    }
    if (cmd.equals("compress")) {
      if (args.length < 2) {
        System.out.println("Usage: java App compress <string>");
        System.exit(1);
      }
      byte[] compressed = june.Helper.compress(args[1].getBytes());
      System.out.println("Compressed length: " + compressed.length + " bytes");
      return;
    }
    if (cmd.equals("write-object")) {
      if (args.length < 3) {
        System.out.println("Usage: java App write-object <type> <content>");
        System.exit(1);
      }
      File tempDb = new File(".june_temp");
      ObjectStore store = new ObjectStore(tempDb);
      store.mkdirs();
      ObjectData data = ObjectData.create(args[1], args[2].getBytes());
      String sha = store.write(data);
      System.out.println("Stored object SHA-1: " + sha);
      deleteDir(tempDb);
      return;
    }
    if (cmd.equals("read-object")) {
      if (args.length < 2) {
        System.out.println("Usage: java App read-object <sha1>");
        System.exit(1);
      }
      File tempDb = new File(".june_temp");
      ObjectStore store = new ObjectStore(tempDb);
      store.mkdirs();
      ObjectData data = store.read(args[1]);
      System.out.println("Object type: " + data.getType() + ", size: " + data.getData().length + " bytes");
      deleteDir(tempDb);
      return;
    }
    if (cmd.equals("write-index")) {
      if (args.length < 4) {
        System.out.println("Usage: java App write-index <sha1> <mode> <path>");
        System.exit(1);
      }
      File tempIndex = new File(".june_index_temp");
      Index index = new Index(tempIndex);
      index.add(args[1], args[2], args[3]);
      index.write();
      System.out.println("Added and wrote staging entry: " + args[3]);
      tempIndex.delete();
      return;
    }
    if (cmd.equals("update-ref")) {
      if (args.length < 3) {
        System.out.println("Usage: java App update-ref <refName> <sha1>");
        System.exit(1);
      }
      File repoTarget = new File(".");
      Repository repo = new Repository(repoTarget);
      repo.setHeadTarget("ref: " + args[1]);
      repo.updateHeadRefOrCommit(args[2]);
      System.out.println("Updated reference " + args[1] + " to commit " + args[2]);
      return;
    }
    if (cmd.equals("check-ignore")) {
      if (args.length < 2) {
        System.out.println("Usage: java App check-ignore <path>");
        System.exit(1);
      }
      File repoTarget = new File(".");
      Repository repo = new Repository(repoTarget);
      List<String> rules = new ArrayList<>();
      File ignoreFile = new File(repoTarget, ".juneignore");
      if (ignoreFile.exists()) {
        rules.addAll(java.nio.file.Files.readAllLines(ignoreFile.toPath()));
      }
      System.out.println("Path ignored: " + IgnoreRules.isIgnored(args[1], rules));
      return;
    }
    System.out.println("Unknown command: " + cmd);
  }

  private static void deleteDir(File f) {
    File[] list = f.listFiles();
    if (list != null) {
      for (File child : list) deleteDir(child);
    }
    f.delete();
  }
}
