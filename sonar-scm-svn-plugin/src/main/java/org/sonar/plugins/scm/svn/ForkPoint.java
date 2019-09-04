/*
 * SonarQube :: Plugins :: SCM :: Git
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

import java.util.Set;

public class ForkPoint {
  private String commit;
  private Set<String> references;

  public ForkPoint(String commit, Set<String> references) {
    this.commit = commit;
    this.references = references;
  }

  public String commit() {
    return commit;
  }

  public Set<String> references() {
    return references;
  }

  @Override
  public String toString() {
    return "ForkPoint{" +
      "commit='" + commit + '\'' +
      ", references=" + references +
      '}';
  }
}
