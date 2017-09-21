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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.ScmBranchProvider;

import java.io.File;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;

public class SvnScmProvider extends ScmBranchProvider {

  private static final Logger LOG = Loggers.get(SvnScmProvider.class);

  private final SvnBlameCommand blameCommand;
  private final SvnClientManagerProvider svnClientManagerProvider;

  public SvnScmProvider(SvnBlameCommand blameCommand, SvnClientManagerProvider svnClientManagerProvider) {
    this.blameCommand = blameCommand;
    this.svnClientManagerProvider = svnClientManagerProvider;
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

  @Nullable
  @Override
  public Collection<Path> branchChangedFiles(String targetBranchName, Path rootBaseDir) {
    try {
      SVNWCClient wcClient = svnClientManagerProvider.get().getWCClient();
      SVNInfo svnInfo = wcClient.doInfo(rootBaseDir.toFile(), null);
      String base = "/" + Paths.get(svnInfo.getRepositoryRootURL().getPath()).relativize(Paths.get(svnInfo.getURL().getPath()));

      SVNLogClient svnLogClient = svnClientManagerProvider.get().getLogClient();
      List<Path> paths = new ArrayList<>();
      svnLogClient.doLog(new File[] {rootBaseDir.toFile()}, null, null, null, true, true, 0, svnLogEntry -> {
        svnLogEntry.getChangedPaths().values().forEach(entry -> {
          if (entry.getCopyPath() == null) {
            paths.add(rootBaseDir.resolve(Paths.get(base).relativize(Paths.get(entry.getPath()))));
          }
        });
      });
      return paths;
    } catch (SVNException e) {
      LOG.warn(e.getMessage(), e);
    }

    return null;
  }
}
