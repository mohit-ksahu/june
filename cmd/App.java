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
