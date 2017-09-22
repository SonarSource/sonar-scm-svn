/*
 * SonarQube :: Plugins :: SCM :: SVN
 * Copyright (C) 2014-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.scm.svn;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.scm.ScmProvider;
import org.tmatesoft.svn.core.SVNException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SvnScmProviderTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private SvnTester svnTester;

  @Before
  public void before() throws IOException, SVNException {
    svnTester = new SvnTester(temp.newFolder().toPath());

    Path worktree = temp.newFolder().toPath();
    svnTester.checkout(worktree, "trunk");
    createAndCommitFile(worktree, "file-in-first-commit.xoo");
  }

  @Test
  public void sanityCheck() {
    SvnBlameCommand blameCommand = new SvnBlameCommand(mock(SvnConfiguration.class));
    SvnScmProvider svnScmProvider = new SvnScmProvider(mock(SvnConfiguration.class), blameCommand);
    assertThat(svnScmProvider.key()).isEqualTo("svn");
    assertThat(svnScmProvider.blameCommand()).isEqualTo(blameCommand);
  }

  @Test
  public void testAutodetection() throws IOException {
    ScmProvider scmBranchProvider = newScmProvider();

    File baseDirEmpty = temp.newFolder();
    assertThat(scmBranchProvider.supports(baseDirEmpty)).isFalse();

    File svnBaseDir = temp.newFolder();
    Files.createDirectory(svnBaseDir.toPath().resolve(".svn"));
    assertThat(scmBranchProvider.supports(svnBaseDir)).isTrue();

    File svnBaseDirSubFolder = temp.newFolder();
    Files.createDirectory(svnBaseDirSubFolder.toPath().resolve(".svn"));
    File projectBaseDir = new File(svnBaseDirSubFolder, "folder");
    Files.createDirectory(projectBaseDir.toPath());
    assertThat(scmBranchProvider.supports(projectBaseDir)).isTrue();
  }

  @Test
  public void branchChangedFiles_from_diverged() throws IOException, SVNException {
    Path trunk = temp.newFolder().toPath();
    svnTester.checkout(trunk, "trunk");
    createAndCommitFile(trunk, "file-m1.xoo");
    createAndCommitFile(trunk, "file-m2.xoo");
    createAndCommitFile(trunk, "file-m3.xoo");

    // create branch from trunk
    svnTester.createBranch("b1");

    // still on trunk
    appendToAndCommitFile(trunk, "file-m3.xoo");
    createAndCommitFile(trunk, "file-m4.xoo");

    Path b1 = temp.newFolder().toPath();
    svnTester.checkout(b1, "branches/b1");
    createAndCommitFile(b1, "file-b1.xoo");
    appendToAndCommitFile(b1, "file-m1.xoo");
    deleteAndCommitFile(b1, "file-m2.xoo");

    svnTester.update(b1);

    assertThat(newScmProvider().branchChangedFiles("trunk", b1))
      .containsExactlyInAnyOrder(
        b1.resolve("file-b1.xoo"),
        b1.resolve("file-m1.xoo"));
  }

  @Test
  public void branchChangedFiles_should_return_empty_when_no_local_changes() throws IOException, SVNException {
    Path b1 = temp.newFolder().toPath();
    svnTester.createBranch("b1");
    svnTester.checkout(b1, "branches/b1");

    assertThat(newScmProvider().branchChangedFiles("b1", b1)).isEmpty();
  }

  @Test
  public void branchChangedFiles_should_return_null_when_repo_nonexistent() throws IOException {
    assertThat(newScmProvider().branchChangedFiles("trunk", temp.newFolder().toPath())).isNull();
  }

  @Test
  public void branchChangedFiles_should_return_null_dir_nonexistent() throws IOException {
    assertThat(newScmProvider().branchChangedFiles("trunk", temp.getRoot().toPath().resolve("nonexistent"))).isNull();
  }

  private void createAndCommitFile(Path worktree, String filename) throws IOException, SVNException {
    svnTester.createFile(worktree, filename);
    svnTester.add(worktree, filename);
    svnTester.commit(worktree);
  }

  private void appendToAndCommitFile(Path worktree, String filename) throws IOException, SVNException {
    svnTester.modifyFile(worktree, filename);
    svnTester.commit(worktree);
  }

  private void deleteAndCommitFile(Path worktree, String filename) throws IOException, SVNException {
    svnTester.deleteFile(worktree, filename);
    svnTester.commit(worktree);
  }

  private SvnScmProvider newScmProvider() {
    return new SvnScmProvider(mock(SvnConfiguration.class), new SvnBlameCommand(mock(SvnConfiguration.class)));
  }
}
