/*
 * SonarQube :: Plugins :: SCM :: SVN
 * Copyright (C) 2014-2019 SonarSource SA
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
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;

import static org.sonar.plugins.scm.svn.SvnPlugin.newSvnClientManager;

public class FindLatestFork {

  private static final Logger LOG = Loggers.get(FindLatestFork.class);
  SvnConfiguration configuration;

  public FindLatestFork(SvnConfiguration configuration) {
    this.configuration = configuration;
  }

  public ForkPoint find(Path location) throws SVNException {
    Instant start = Instant.now();

    SVNLogEntryHolder handler = getLastSVNLogEntry(location);
    SVNLogEntry lastEntry = handler.getLastEntry();
    SVNLogEntryPath value = lastEntry.getChangedPaths().entrySet().iterator().next().getValue();

    Set<String> references = new HashSet<>();
    if (value.getCopyPath() != null) {
      references.add(value.getCopyPath());
    }

    if(value.getCopyRevision() == -1){
      // we walked the history to the root, and the last commit found had no copy reference. Must be the trunk, there is no fork point
      return null;
    }

    ForkPoint forkPoint = new ForkPoint(String.valueOf(value.getCopyRevision()), references);
    LOG.debug(forkPoint + " found in " + Duration.between(start, Instant.now()).toMillis() + "ms reading " + handler.getCpt() + " revision(s)");
    return forkPoint;
  }

  private SVNLogEntryHolder getLastSVNLogEntry(Path location) throws SVNException {
    SVNClientManager clientManager = newSvnClientManager(configuration);
    SVNRevision revision = getSvnRevision(location, clientManager);

    SVNLogEntryHolder handler = new SVNLogEntryHolder();
    SVNRevision startRevision = SVNRevision.create(revision.getNumber());
    SVNRevision endRevision = SVNRevision.create(1);
    clientManager.getLogClient().doLog(new File[] {location.toFile()}, startRevision, endRevision, true, true, -1, handler);
    return handler;
  }

  private SVNRevision getSvnRevision(Path location, SVNClientManager clientManager) throws SVNException {
    SVNStatus svnStatus = clientManager.getStatusClient().doStatus(location.toFile(), false);
    LOG.debug("latest revision is " + svnStatus.getRevision());
    return svnStatus.getRevision();
  }

  /**
   * Handler keeping only the last entry, and count how many entries have been seen.
   */
  private class SVNLogEntryHolder implements ISVNLogEntryHandler{
    SVNLogEntry value;
    long cpt = 0L;

    public SVNLogEntry getLastEntry() {
      return value;
    }

    public long getCpt() {
      return cpt;
    }

    @Override
    public void handleLogEntry(SVNLogEntry svnLogEntry) {
      this.value = svnLogEntry;
      cpt++;
    }
  }
}
