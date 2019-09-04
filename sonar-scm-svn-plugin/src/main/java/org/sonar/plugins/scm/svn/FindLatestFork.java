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
    SVNClientManager clientManager = newSvnClientManager(configuration);

    File[] files = new File[] {location.toFile()};

    SVNStatus svnStatus = clientManager.getStatusClient().doStatus(location.toFile(), false);
    SVNRevision revision = svnStatus.getRevision();

    SVNLogEntryHolder handler = new SVNLogEntryHolder(); // keep only the last SVNLogEntry
    SVNRevision startRevision = SVNRevision.create(revision.getNumber());
    SVNRevision endRevision = SVNRevision.create(1);
    clientManager.getLogClient().doLog(files, startRevision, endRevision, true, true, -1, handler);
    SVNLogEntry lastEntry = handler.getLastEntry();

    SVNLogEntryPath value = lastEntry.getChangedPaths().entrySet().iterator().next().getValue();

    Set<String> references = new HashSet<>();
    if (value.getCopyPath() != null) {
      references.add(value.getCopyPath());
    }

    LOG.debug("latest revision is " + revision);

    if(value.getCopyRevision() == -1){
      // we walked the history to the root, and the last commit found had no copy reference. Must be the trunk, there is no fork point
      return null;
    }

    ForkPoint forkPoint = new ForkPoint(String.valueOf(value.getCopyRevision()), references);
    LOG.debug(forkPoint + " found in " + Duration.between(start, Instant.now()).toMillis() + "ms reading " + handler.getCpt() + " revision(s)");
    return forkPoint;
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
