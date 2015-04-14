/*
 * SonarQube :: Plugins :: SCM :: SVN
 * Copyright (C) 2014 SonarSource
 * dev@sonar.codehaus.org
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class SvnBlameCommand extends BlameCommand {

  private static final Logger LOG = LoggerFactory.getLogger(SvnBlameCommand.class);
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
      ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1);
      List<Future<Void>> tasks = new ArrayList<Future<Void>>();
      for (InputFile inputFile : input.filesToBlame()) {
        tasks.add(submitTask(fs, output, executorService, inputFile));
      }

      waitForTaskToComplete(executorService, tasks);
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

  private void waitForTaskToComplete(ExecutorService executorService, List<Future<Void>> tasks) {
    executorService.shutdown();
    for (Future<Void> task : tasks) {
      try {
        task.get();
      } catch (ExecutionException e) {
        // Unwrap ExecutionException
        throw e.getCause() instanceof RuntimeException ? (RuntimeException) e.getCause() : new IllegalStateException(e.getCause());
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private Future<Void> submitTask(final FileSystem fs, final BlameOutput result, ExecutorService executorService, final InputFile inputFile) {
    return executorService.submit(new Callable<Void>() {
      @Override
      public Void call() {
        blame(fs, inputFile, result);
        return null;
      }
    });
  }

  private void blame(final FileSystem fs, final InputFile inputFile, final BlameOutput output) {
    String filename = inputFile.relativePath();

    AnnotationHandler handler = new AnnotationHandler();
    try {
      SVNLogClient logClient = getClientManager().getLogClient();
      logClient.setDiffOptions(new SVNDiffOptions(true, true, true));
      logClient.doAnnotate(inputFile.file(), SVNRevision.UNDEFINED, SVNRevision.create(1), SVNRevision.HEAD, true, true, handler, null);
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
    String configDir = configuration.configDir();
    ISVNAuthenticationManager isvnAuthenticationManager = SVNWCUtil.createDefaultAuthenticationManager(configDir == null ? null : new File(configDir), configuration.username(),
      configuration.password(), false);
    SVNClientManager svnClientManager = SVNClientManager.newInstance(options, isvnAuthenticationManager);
    return svnClientManager;
  }
}
