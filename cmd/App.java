import java.io.File;
import java.util.List;
import java.util.ArrayList;
import june.Repository;
import june.Sha1;
import june.ObjectData;
import june.ObjectTypes;

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
