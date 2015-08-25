/*
 * SonarQube :: Plugins :: SCM :: SVN
 * Copyright (C) 2014 SonarSource
 * sonarqube@googlegroups.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.scm.svn;

import java.util.List;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class SvnBlameCommand extends BlameCommand {

  private static final Logger LOG = Loggers.get(SvnBlameCommand.class);
  private final SvnConfiguration configuration;

  public SvnBlameCommand(SvnConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public void blame(final BlameInput input, final BlameOutput output) {
    FileSystem fs = input.fileSystem();
    LOG.debug("Working directory: " + fs.baseDir().getAbsolutePath());
    SVNClientManager clientManager = null;
    try {
      clientManager = getClientManager();
      for (InputFile inputFile : input.filesToBlame()) {
        blame(clientManager, fs, inputFile, output);
      }
    } finally {
      if (clientManager != null) {
        try {
          clientManager.dispose();
        } catch (Exception e) {
          LOG.warn("Unable to dispose SVN ClientManager", e);
        }
      }
    }
  }

  private void blame(SVNClientManager clientManager, FileSystem fs, InputFile inputFile, BlameOutput output) {
    String filename = inputFile.relativePath();

    AnnotationHandler handler = new AnnotationHandler();
    try {
      SVNStatusClient statusClient = clientManager.getStatusClient();
      try {
        SVNStatus status = statusClient.doStatus(inputFile.file(), false);
        if (status == null) {
          LOG.debug("File " + inputFile + " returns no svn state. Skipping it.");
          return;
        }
        if (status.getContentsStatus() != SVNStatusType.STATUS_NORMAL) {
          LOG.debug("File " + inputFile + " is not versionned or contains local modifications. Skipping it.");
          return;
        }
      } catch (SVNException e) {
        if (SVNErrorCode.WC_PATH_NOT_FOUND.equals(e.getErrorMessage().getErrorCode())) {
          LOG.debug("File " + inputFile + " is not versionned. Skipping it.");
          return;
        }
        throw e;
      }
      SVNLogClient logClient = clientManager.getLogClient();
      logClient.setDiffOptions(new SVNDiffOptions(true, true, true));
      logClient.doAnnotate(inputFile.file(), SVNRevision.UNDEFINED, SVNRevision.create(1), SVNRevision.WORKING, true, true, handler, null);
    } catch (SVNException e) {
      throw new IllegalStateException("Error when executing blame for file " + filename, e);
    }

    List<BlameLine> lines = handler.getLines();
    if (lines.size() == inputFile.lines() - 1) {
      // SONARPLUGINS-3097 SVN do not report blame on last empty line
      lines.add(lines.get(lines.size() - 1));
    }
    output.blameResult(inputFile, lines);
  }

  public SVNClientManager getClientManager() {
    ISVNOptions options = SVNWCUtil.createDefaultOptions(true);
    ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(
      null,
      configuration.username(),
      configuration.password(),
      false);
    return SVNClientManager.newInstance(options, authManager);
  }
}
