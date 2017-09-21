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
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc2.SvnList;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteMkDir;
import org.tmatesoft.svn.core.wc2.SvnTarget;

class SvnTester {
  private final SVNClientManager manager = SVNClientManager.newInstance(new SvnOperationFactory());

  private final SVNURL localRepository;

  SvnTester(Path root) throws SVNException, IOException {
    localRepository = SVNRepositoryFactory.createLocalRepository(root.toFile(), false, false);
    mkdir("trunk");
    mkdir("branches");
  }

  private void mkdir(String relpath) throws IOException, SVNException {
    SvnRemoteMkDir remoteMkDir = manager.getOperationFactory().createRemoteMkDir();
    remoteMkDir.addTarget(SvnTarget.fromURL(localRepository.appendPath(relpath, false)));
    remoteMkDir.run();
  }

  void createBranch(String branchName) throws IOException, SVNException {
    SVNCopyClient copyClient = manager.getCopyClient();
    SVNCopySource source = new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, localRepository.appendPath("trunk", false));
    copyClient.doCopy(new SVNCopySource[] {source}, localRepository.appendPath("branches/" + branchName, false), false, false, true, "Create branch", null);
  }

  void checkout(Path worktree, String path) throws SVNException {
    SVNUpdateClient updateClient = manager.getUpdateClient();
    updateClient.doCheckout(localRepository.appendPath(path, false),
      worktree.toFile(), null, null, SVNDepth.INFINITY, false);
  }

  void add(Path worktree, String filename) throws SVNException {
    manager.getWCClient().doAdd(worktree.resolve(filename).toFile(), false, false, false, null, false, false, false);
  }

  void commit(Path worktree) throws SVNException {
    manager.getCommitClient().doCommit(new File[] {worktree.toFile()}, false, "commit " + worktree, null, null, false, false, null);
  }

  Collection<String> list(String... paths) throws SVNException {
    Set<String> results = new HashSet<>();

    SvnList list = manager.getOperationFactory().createList();
    if (paths.length == 0) {
      list.addTarget(SvnTarget.fromURL(localRepository));
    } else {
      for (String path : paths) {
        list.addTarget(SvnTarget.fromURL(localRepository.appendPath(path, false)));
      }
    }
    list.setDepth(SVNDepth.INFINITY);
    list.setReceiver((svnTarget, svnDirEntry) -> {
      String path = svnDirEntry.getRelativePath();
      if (!path.isEmpty()) {
        results.add(path);
      }
    });
    list.run();

    return results;
  }

  Collection<Path> branchChangedFiles(Path worktree) throws SVNException {
    SVNWCClient wcClient = manager.getWCClient();
    SVNInfo svnInfo = wcClient.doInfo(worktree.toFile(), null);
    String base = "/" + Paths.get(svnInfo.getRepositoryRootURL().getPath()).relativize(Paths.get(svnInfo.getURL().getPath()));

    SVNLogClient svnLogClient = manager.getLogClient();
    List<Path> paths = new ArrayList<>();
    svnLogClient.doLog(new File[] {worktree.toFile()}, null, null, null, true, true, 0, svnLogEntry -> {
      svnLogEntry.getChangedPaths().values().forEach(entry -> {
        if (entry.getCopyPath() == null) {
          paths.add(Paths.get(base).relativize(Paths.get(entry.getPath())));
        }
      });
    });

    return paths;
  }

  void createFile(Path worktree, String filename) throws IOException {
    Files.write(worktree.resolve(filename), filename.getBytes());
  }

  void modifyFile(Path worktree, String filename) throws IOException {
    Files.write(worktree.resolve(filename), filename.getBytes(), StandardOpenOption.APPEND);
  }
}
