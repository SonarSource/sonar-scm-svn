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

import org.junit.Test;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;

import static org.assertj.core.api.Assertions.assertThat;

public class SvnConfigurationTest {

  @Test
  public void sanityCheck() {
    Settings settings = new Settings(new PropertyDefinitions(SvnConfiguration.getProperties()));
    SvnConfiguration config = new SvnConfiguration(settings);

    assertThat(config.username()).isNull();
    assertThat(config.password()).isNull();

    settings.setProperty(SvnConfiguration.USER_PROP_KEY, "foo");
    assertThat(config.username()).isEqualTo("foo");

    settings.setProperty(SvnConfiguration.PASSWORD_PROP_KEY, "pwd");
    assertThat(config.password()).isEqualTo("pwd");
  }

}
