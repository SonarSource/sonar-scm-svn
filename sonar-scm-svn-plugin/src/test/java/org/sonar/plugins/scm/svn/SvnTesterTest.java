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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tmatesoft.svn.core.SVNException;

import static org.assertj.core.api.Assertions.assertThat;

public class SvnTesterTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private SvnTester tester;

  @Before
  public void before() throws IOException, SVNException {
    tester = new SvnTester(temp.newFolder().toPath());
  }

  @Test
  public void test_init() throws SVNException {
    assertThat(tester.list()).containsExactlyInAnyOrder("trunk", "branches");
  }

  @Test
  public void test_add_and_commit() throws IOException, SVNException {
    assertThat(tester.list("trunk")).isEmpty();

    Path worktree = temp.newFolder().toPath();
    tester.checkout(worktree, "trunk");
    tester.createFile(worktree, "file1");

    tester.add(worktree, "file1");
    tester.commit(worktree);

    assertThat(tester.list("trunk")).containsOnly("file1");
  }

  @Test
  public void test_createBranch() throws IOException, SVNException {
    tester.createBranch("b1");
    assertThat(tester.list()).containsExactlyInAnyOrder("trunk", "branches", "branches/b1");
    assertThat(tester.list("branches")).containsOnly("b1");
  }

  @Test
  public void test_listFilesModifiedInBranch() throws IOException, SVNException {
    Path trunk = temp.newFolder().toPath();
    tester.checkout(trunk, "trunk");
    tester.createFile(trunk, "file-0");
    tester.add(trunk, "file-0");
    tester.createFile(trunk, "file-1");
    tester.add(trunk, "file-1");
    tester.commit(trunk);

    tester.createBranch("b1");

    Path branch = temp.newFolder().toPath();
    tester.checkout(branch, "branches/b1");
    tester.createFile(branch, "file-b1");
    tester.add(branch, "file-b1");
    tester.modifyFile(branch, "file-1");
    tester.commit(branch);

    assertThat(tester.list("trunk")).containsOnly("file-0", "file-1");
    assertThat(tester.list("branches/b1")).containsOnly("file-0", "file-1", "file-b1");

    Path trunk2 = temp.newFolder().toPath();
    tester.checkout(trunk2, "trunk");
    tester.createFile(trunk2, "file-0-2");
    tester.add(trunk2, "file-0-2");
    tester.commit(trunk2);

    assertThat(tester.list("trunk")).containsOnly("file-0", "file-1", "file-0-2");
    assertThat(tester.list("branches/b1")).containsOnly("file-0", "file-1", "file-b1");

    Path branch2 = temp.newFolder().toPath();
    tester.checkout(branch2, "branches/b1");
    assertThat(tester.branchChangedFiles(branch2)).containsOnly(Paths.get("file-1"), Paths.get("file-b1"));
  }
}
