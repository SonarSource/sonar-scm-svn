/*
 * SVN :: Integration Tests
 * Copyright (C) 2014 ${owner}
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
package com.sonarsource.it.scm;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.util.ZipUtils;
import com.sonar.orchestrator.version.Version;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.fest.assertions.MapAssert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.wsclient.jsonsimple.JSONArray;
import org.sonar.wsclient.jsonsimple.JSONObject;
import org.sonar.wsclient.jsonsimple.JSONValue;

import static org.fest.assertions.Assertions.assertThat;

public class SvnTest {

  public static final File PROJECTS_DIR = new File("target/projects");
  public static final File SOURCES_DIR = new File("scm-repo");

  private static Version artifactVersion;

  private static Version artifactVersion() {
    if (artifactVersion == null) {
      try (FileInputStream fis = new FileInputStream(new File("../target/maven-archiver/pom.properties"))) {
        Properties props = new Properties();
        props.load(fis);
        artifactVersion = Version.create(props.getProperty("version"));
        return artifactVersion;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    return artifactVersion;
  }

  @ClassRule
  public static Orchestrator orchestrator = Orchestrator.builderEnv()
    .addPlugin(FileLocation.of("../target/sonar-scm-svn-plugin-" + artifactVersion() + ".jar"))
    .setOrchestratorProperty("javaVersion", "LATEST_RELEASE")
    .addPlugin("java")
    .build();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void deleteData() {
    orchestrator.resetData();
  }

  @Test
  public void sample_svn_project() throws Exception {
    unzip("repo-svn.zip");

    String scmUrl = "scm:svn:file:///" + unixPath(project("repo-svn/dummy-svn"));
    checkout("dummy-svn", scmUrl);

    runSonar("dummy-svn");

    assertThat(getScmData("dummy:dummy:src/main/java/org/dummy/Dummy.java"))
      .includes(
        MapAssert.entry(1, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapAssert.entry(2, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapAssert.entry(3, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapAssert.entry(24, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")));
  }

  // SONARSCSVN-4, SONARSCSVN-5
  @Test
  public void dont_fail_on_uncommited_files() throws Exception {
    unzip("repo-svn.zip");

    String scmUrl = "scm:svn:file:///" + unixPath(project("repo-svn/dummy-svn"));
    checkout("dummy-svn", scmUrl);

    // Edit file
    FileUtils.write(new File(project("dummy-svn"), "src/main/java/org/dummy/Dummy.java"), "\n//bla\n//bla", Charsets.UTF_8, true);
    // New file
    FileUtils.write(new File(project("dummy-svn"), "src/main/java/org/dummy/Dummy2.java"), "package org.dummy;\npublic class Dummy2 {}", Charsets.UTF_8, true);

    BuildResult result = runSonar("dummy-svn");

    assertThat(result.getLogs()).contains("Missing blame information for the following files:");
    assertThat(result.getLogs()).contains("dummy-svn/src/main/java/org/dummy/Dummy.java");
    assertThat(result.getLogs()).contains("dummy-svn/src/main/java/org/dummy/Dummy2.java");

    assertThat(getScmData("dummy:dummy:src/main/java/org/dummy/Dummy.java")).isEmpty();
  }

  // SONAR-5843
  @Test
  public void sample_svn_project_with_merge() throws Exception {
    unzip("repo-svn-with-merge.zip");

    String scmUrl = "scm:svn:file:///" + unixPath(project("repo-svn/dummy-svn/trunk"));
    checkout("dummy-svn", scmUrl);

    runSonar("dummy-svn", "sonar.svn.use_merge_history", "true");

    assertThat(getScmData("dummy:dummy:src/main/java/org/dummy/Dummy.java"))
      .includes(
        MapAssert.entry(1, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapAssert.entry(2, new LineData("6", "2014-11-06T09:23:04+0100", "henryju")),
        MapAssert.entry(3, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapAssert.entry(24, new LineData("6", "2014-11-06T09:23:04+0100", "henryju")));
  }

  private static String unixPath(File file) {
    return file.getAbsolutePath().replace('\\', '/');
  }

  private static void checkout(String destination, String url) {
    orchestrator.executeBuilds(MavenBuild.create()
      .setGoals("org.apache.maven.plugins:maven-scm-plugin:1.7:checkout")
      .setProperty("connectionUrl", url)
      .setProperty("checkoutDirectory", destination)
      .setExecutionDir(PROJECTS_DIR));
  }

  public static void unzip(String zipName) {
    try {
      FileUtils.forceMkdir(PROJECTS_DIR);
      ZipUtils.unzip(new File(SOURCES_DIR, zipName), PROJECTS_DIR);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  public static BuildResult runSonar(String projectName, String... keyValues) {
    File pom = new File(project(projectName), "pom.xml");

    MavenBuild install = MavenBuild.create(pom).setGoals("clean install");
    MavenBuild sonar = MavenBuild.create(pom).setGoals("sonar:sonar");
    sonar.setProperty("sonar.scm.disabled", "false");
    sonar.setProperties(keyValues);
    orchestrator.executeBuild(install);
    return orchestrator.executeBuild(sonar);
  }

  public static File project(String name) {
    return new File(PROJECTS_DIR, name);
  }

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
  private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

  private class LineData {

    final String revision;
    final String date;
    final String author;

    public LineData(String revision, String datetime, String author) throws ParseException {
      boolean is_5_2_plus = orchestrator.getServer().version().isGreaterThanOrEquals("5.2");
      // Revision was added in 5.2
      this.revision = is_5_2_plus ? revision : null;
      // Time part was added in 5.2
      this.date = is_5_2_plus ? datetime : DATE_FORMAT.format(DATETIME_FORMAT.parse(datetime));
      this.author = author;
    }

    @Override
    public boolean equals(Object obj) {
      return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(revision).append(date).append(author).toHashCode();
    }

    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, ToStringStyle.SIMPLE_STYLE);
    }
  }

  private Map<Integer, LineData> getScmData(String fileKey) throws ParseException {
    boolean is_5_2_plus = orchestrator.getServer().version().isGreaterThanOrEquals("5.2");
    Map<Integer, LineData> result = new HashMap<Integer, LineData>();
    String json = orchestrator.getServer().adminWsClient().get("api/sources/scm", "commits_by_line", "true", "key", fileKey);
    JSONObject obj = (JSONObject) JSONValue.parse(json);
    JSONArray array = (JSONArray) obj.get("scm");
    for (int i = 0; i < array.size(); i++) {
      JSONArray item = (JSONArray) array.get(i);
      // Time part was added in 5.2
      String dateOrDatetime = (String) item.get(2);
      // Revision was added in 5.2
      result.put(((Long) item.get(0)).intValue(), new LineData(is_5_2_plus ? (String) item.get(3) : null,
        is_5_2_plus ? dateOrDatetime : DATETIME_FORMAT.format(DATE_FORMAT.parse(dateOrDatetime)), (String) item.get(1)));
    }
    return result;
  }

}
