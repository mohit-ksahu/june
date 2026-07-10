import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import june.Repository;

public final class Tag {
  public static void run(Repository repo, String[] args) throws IOException {
    if (args.length == 0) {
      repo.listTags().forEach(System.out::println);
    } else if (args[0].equals("-d")) {
      String res = repo.deleteTag(args[1]);
      if (res != null && !res.isEmpty()) {
        System.out.println(res);
      }
    } else {
      try {
        String target = args.length > 1 ? args[1] : "HEAD";
        String sha = resolveRef(repo, target);
        repo.writeWithLock(repo.tagRefFile(args[0]), sha + "\n");
        System.out.println("Created tag " + args[0] + " pointing to " + sha);
      } catch (Exception e) {
        throw new IOException(e);
      }
    }
  }

  private static String resolveRef(Repository repo, String target) throws Exception {
    if (target.equals("HEAD")) return repo.getHeadCommitSha1();
    File branchFile = repo.branchRefFile(target);
    if (branchFile.isFile()) return Files.readString(branchFile.toPath()).trim();
    File tagFile = repo.tagRefFile(target);
    if (tagFile.isFile()) return Files.readString(tagFile.toPath()).trim();
    String sha = june.Helper.resolveShortSha1(repo.getRepoDir(), target);
    if (sha != null) return sha;
    return target;
  }
}