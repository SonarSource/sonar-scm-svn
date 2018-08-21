/*
 * SonarQube :: Plugins :: SCM :: SVN
 * Copyright (C) 2014-2018 SonarSource SA
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.ScmProvider;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import static org.sonar.plugins.scm.svn.SvnPlugin.newSvnClientManager;

public class SvnScmProvider extends ScmProvider {

  private static final Logger LOG = Loggers.get(SvnScmProvider.class);

  private final SvnConfiguration configuration;
  private final SvnBlameCommand blameCommand;

  public SvnScmProvider(SvnConfiguration configuration, SvnBlameCommand blameCommand) {
    this.configuration = configuration;
    this.blameCommand = blameCommand;
  }

  @Override
  public String key() {
    return "svn";
  }

  @Override
  public boolean supports(File baseDir) {
    File folder = baseDir;
    while (folder != null) {
      if (new File(folder, ".svn").exists()) {
        return true;
      }
      folder = folder.getParentFile();
    }
    return false;
  }

  @Override
  public BlameCommand blameCommand() {
    return blameCommand;
  }

  @CheckForNull
  @Override
  public Set<Path> branchChangedFiles(String targetBranchName, Path rootBaseDir) {
    SVNClientManager clientManager = null;
    try {
      clientManager = newSvnClientManager(configuration);
      return computeChangedPaths(rootBaseDir, clientManager);
    } catch (SVNException e) {
      LOG.warn(e.getMessage());
    } finally {
      if (clientManager != null) {
        try {
          clientManager.dispose();
        } catch (Exception e) {
          LOG.warn("Unable to dispose SVN ClientManager", e);
        }
      }
    }

    return null;
  }

  private static Set<Path> computeChangedPaths(Path rootBaseDir, SVNClientManager clientManager) throws SVNException {
    SVNWCClient wcClient = clientManager.getWCClient();
    SVNInfo svnInfo = wcClient.doInfo(rootBaseDir.toFile(), null);
    String base = "/" + Paths.get(svnInfo.getRepositoryRootURL().getPath()).relativize(Paths.get(svnInfo.getURL().getPath()));

    // We inspect "svn log" from latest revision until copy-point.
    // The same path may appear in multiple commits, the ordering of changes and removals is important.
    Set<Path> paths = new HashSet<>();
    Set<Path> removed = new HashSet<>();

    SVNLogClient svnLogClient = clientManager.getLogClient();
    svnLogClient.doLog(new File[] {rootBaseDir.toFile()}, null, null, null, true, true, 0, svnLogEntry -> {
      for (SVNLogEntryPath entry : svnLogEntry.getChangedPaths().values()) {
        if (entry.getKind().equals(SVNNodeKind.FILE)) {
          Path path = rootBaseDir.resolve(Paths.get(base).relativize(Paths.get(entry.getPath())));
          if (isModified(entry)) {
            // Skip if the path is removed in a more recent commit
            if (!removed.contains(path)) {
              paths.add(path);
            }
          } else if (entry.getType() == SVNLogEntryPath.TYPE_DELETED) {
            removed.add(path);
          }
        }
      }
    });
    return paths;
  }

  private static boolean isModified(SVNLogEntryPath entry) {
    return entry.getType() == SVNLogEntryPath.TYPE_ADDED
      || entry.getType() == SVNLogEntryPath.TYPE_MODIFIED;
  }

  @CheckForNull
  public Map<Path, Set<Integer>> branchChangedLines(String targetBranchName, Path rootBaseDir, Set<Path> changedFiles) {
    SVNClientManager clientManager = null;
    try {
      clientManager = newSvnClientManager(configuration);

      // find reference revision number: the copy point
      SVNLogClient svnLogClient = clientManager.getLogClient();
      long[] revisionCounter = {0};
      svnLogClient.doLog(new File[] {rootBaseDir.toFile()}, null, null, null, true, true, 0,
        svnLogEntry -> revisionCounter[0] = svnLogEntry.getRevision());

      long startRev = revisionCounter[0];

      SVNDiffClient svnDiffClient = clientManager.getDiffClient();
      File path = rootBaseDir.toFile();
      ChangedLinesComputer computer = newChangedLinesComputer(changedFiles);
      svnDiffClient.doDiff(path, SVNRevision.create(startRev), path, SVNRevision.WORKING, SVNDepth.INFINITY, false, computer.receiver(), null);
      return computer.changedLines();
    } catch (Exception e) {
      LOG.warn("Failed to get changed lines from Subversion", e);
    } finally {
      if (clientManager != null) {
        try {
          clientManager.dispose();
        } catch (Exception e) {
          LOG.warn("Unable to dispose SVN ClientManager", e);
        }
      }
    }

    return null;
  }

  ChangedLinesComputer newChangedLinesComputer(Set<Path> changedFiles) {
    return new ChangedLinesComputer(changedFiles);
  }
}
