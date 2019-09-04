package org.sonar.plugins.scm.svn;

import java.io.IOException;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.tmatesoft.svn.core.SVNException;

public class FindLatestForkWalk {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  private static SvnTester svnTester;

  private static Path trunk;
  private static Path b1;
  private static Path b2;

  @BeforeClass
  public static void before() throws IOException, SVNException {
    svnTester = new SvnTester(temp.newFolder().toPath());

    trunk = temp.newFolder("trunk").toPath();
    svnTester.checkout(trunk, "trunk");
    createAndCommitFile(trunk, "file-1-commit-in-trunk.xoo");
    createAndCommitFile(trunk, "file-2-commit-in-trunk.xoo");
    createAndCommitFile(trunk, "file-3-commit-in-trunk.xoo");
    svnTester.checkout(trunk, "trunk");

    svnTester.createBranch("b1");
    b1 = temp.newFolder("branches", "b1").toPath();
    svnTester.checkout(b1, "branches/b1");
    createAndCommitFile(b1, "file-1-commit-in-b1.xoo");
    createAndCommitFile(b1, "file-2-commit-in-b1.xoo");
    createAndCommitFile(b1, "file-3-commit-in-b1.xoo");
    svnTester.checkout(b1, "branches/b1");

    svnTester.createBranch("branches/b1", "b2");
    b2 = temp.newFolder("branches", "b2").toPath();
    svnTester.checkout(b2, "branches/b2");

    createAndCommitFile(b2, "file-1-commit-in-b2.xoo");
    createAndCommitFile(b2, "file-2-commit-in-b2.xoo");
    createAndCommitFile(b2, "file-3-commit-in-b2.xoo");
    svnTester.checkout(b2, "branches/b2");
  }

  private static void createAndCommitFile(Path worktree, String filename, String content) throws IOException, SVNException {
    svnTester.createFile(worktree, filename, content);
    svnTester.add(worktree, filename);
    svnTester.commit(worktree);
  }

  private static void createAndCommitFile(Path worktree, String filename) throws IOException, SVNException {
    createAndCommitFile(worktree, filename, filename + "\n");
  }

  @Test
  public void testTrunk() throws SVNException {
    SvnConfiguration configurationMock = Mockito.mock(SvnConfiguration.class);
    FindLatestFork findLatestFork = new FindLatestFork(configurationMock);
    ForkPoint forkPoint = findLatestFork.find(trunk);
    Assertions.assertThat(forkPoint).isNull(); // no forkpoint for trunk
  }

  @Test
  public void testB1() throws SVNException {
    SvnConfiguration configurationMock = Mockito.mock(SvnConfiguration.class);
    FindLatestFork findLatestFork = new FindLatestFork(configurationMock);
    ForkPoint forkPoint = findLatestFork.find(b1);
    Assertions.assertThat(forkPoint.commit()).isEqualTo("5");
    Assertions.assertThat(forkPoint.references()).containsExactly("/trunk");
  }

  @Test
  public void testB2() throws SVNException {
    SvnConfiguration configurationMock = Mockito.mock(SvnConfiguration.class);
    FindLatestFork findLatestFork = new FindLatestFork(configurationMock);
    ForkPoint forkPoint = findLatestFork.find(b2);
    Assertions.assertThat(forkPoint.commit()).isEqualTo("9");
    Assertions.assertThat(forkPoint.references()).containsExactly("/branches/b1");
  }

}
