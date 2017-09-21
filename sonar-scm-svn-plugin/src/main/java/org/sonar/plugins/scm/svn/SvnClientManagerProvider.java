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

import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class SvnClientManagerProvider {

  private final SVNClientManager svnClientManager;

  public SvnClientManagerProvider(SvnConfiguration configuration) {
    svnClientManager = provide(configuration);
  }

  public SVNClientManager get() {
    return svnClientManager;
  }

  private SVNClientManager provide(SvnConfiguration configuration) {
    ISVNOptions options = SVNWCUtil.createDefaultOptions(true);
    String password = configuration.password();
    final char[] passwordValue = password != null ? password.toCharArray() : null;
    String passPhrase = configuration.passPhrase();
    final char[] passPhraseValue = passPhrase != null ? passPhrase.toCharArray() : null;
    ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(
      null,
      configuration.username(),
      passwordValue,
      configuration.privateKey(),
      passPhraseValue,
      false);
    return SVNClientManager.newInstance(options, authManager);
  }
}
