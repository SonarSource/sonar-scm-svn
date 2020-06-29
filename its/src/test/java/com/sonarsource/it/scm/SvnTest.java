/*
 * SVN :: Integration Tests
 * Copyright (C) 2014-2020 SonarSource SA
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
package com.sonarsource.it.scm;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import com.sonar.orchestrator.util.ZipUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sonar.wsclient.jsonsimple.JSONArray;
import org.sonar.wsclient.jsonsimple.JSONObject;
import org.sonar.wsclient.jsonsimple.JSONValue;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import static org.apache.commons.lang.StringUtils.substringAfter;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class SvnTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  public static final File REPO_DIR = new File("scm-repo");

  @ClassRule
  public static Orchestrator orchestrator = Orchestrator.builderEnv()
    .setSonarVersion(System.getProperty("sonar.runtimeVersion", "LATEST_RELEASE[7.9]"))
    .addPlugin(FileLocation.byWildcardMavenFilename(new File("../sonar-scm-svn-plugin/target"), "sonar-scm-svn-plugin-*.jar"))
    .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", "LATEST_RELEASE"))
    .build();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private String serverVersion;
  private int wcVersion;
  private String wkSubPath;
  private String baseDirSubPath;

  @Parameters(name = "SVN server version {0}, WC version {1}, WC subPath \"{2}\", baseDir subPath \"{3}\"")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {{"1.6", 10, "dummy-svn", ""}, {"1.6", 10, "", "dummy-svn"}, {"1.8", 31, "dummy-svn", ""}, {"1.8", 31, "", "dummy-svn"}});
  }

  public SvnTest(String serverVersion, int wcVersion, String wkSubPath, String baseDirSubPath) {
    this.serverVersion = serverVersion;
    this.wcVersion = wcVersion;

    // SONARSCSVN-11: Manage the case of a project baseDir is in a subFolder of working copy
    this.wkSubPath = wkSubPath;
    this.baseDirSubPath = baseDirSubPath;
  }

  @Before
  public void deleteData() {
    orchestrator.resetData();
  }

  @Test
  public void sample_svn_project() throws Exception {
    File repo = unzip("repo-svn.zip");

    String scmUrl = "file:///" + unixPath(new File(repo, "repo-svn/" + wkSubPath));

    runSonar(new File(checkout(scmUrl), baseDirSubPath));

    assertThat(getScmData("dummy:dummy:src/main/java/org/dummy/Dummy.java"))
      .contains(
        MapEntry.entry(1, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapEntry.entry(2, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapEntry.entry(3, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapEntry.entry(24, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")));
  }

  // SONARSCSVN-4, SONARSCSVN-5
  @Test
  public void dont_fail_on_uncommited_files() throws Exception {
    File repo = unzip("repo-svn.zip");

    String scmUrl = "file:///" + unixPath(new File(repo, "repo-svn/" + wkSubPath));
    File baseDir = new File(checkout(scmUrl), baseDirSubPath);

    // Edit file
    FileUtils.write(new File(baseDir, "src/main/java/org/dummy/Dummy.java"), "\n//bla\n//bla", StandardCharsets.UTF_8, true);
    // New file
    FileUtils.write(new File(baseDir, "src/main/java/org/dummy/Dummy2.java"), "package org.dummy;\npublic class Dummy2 {}", StandardCharsets.UTF_8, true);

    BuildResult result = runSonar(baseDir);

    String logs = result.getLogs();
    assertThat(logs).contains("Missing blame information for the following files");
    String files = StringUtils.substringBefore(substringAfter(logs, "Missing blame information for the following files:"), "This may lead to missing/broken features");
    assertThat(files).contains("src/main/java/org/dummy/Dummy.java",
      "src/main/java/org/dummy/Dummy2.java");

    if (orchestrator.getServer().version().isGreaterThanOrEquals(7, 1)) {
      assertThat(getScmData("dummy:dummy:src/main/java/org/dummy/Dummy.java")).hasSize(29);
    } else {
      assertThat(getScmData("dummy:dummy:src/main/java/org/dummy/Dummy.java")).isEmpty();
    }
  }

  // SONAR-5843
  @Test
  public void sample_svn_project_with_merge() throws Exception {
    File repo = unzip("repo-svn-with-merge.zip");

    // The "repo-svn-with-merge" repository has a "trunk" subPath => suffix should be added
    String tmpWkSubPath = wkSubPath;
    if (StringUtils.isNotBlank(tmpWkSubPath)) {
      tmpWkSubPath = tmpWkSubPath + "/trunk";
    }
    String tmpBaseDirSubPath = baseDirSubPath;
    if (StringUtils.isNotBlank(tmpBaseDirSubPath)) {
      tmpBaseDirSubPath = tmpBaseDirSubPath + "/trunk";
    }

    String scmUrl = "file:///" + unixPath(new File(repo, "repo-svn/" + tmpWkSubPath));

    runSonar(new File(checkout(scmUrl), tmpBaseDirSubPath));

    assertThat(getScmData("dummy:dummy:src/main/java/org/dummy/Dummy.java"))
      .contains(
        MapEntry.entry(1, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapEntry.entry(2, new LineData("6", "2014-11-06T09:23:04+0100", "henryju")),
        MapEntry.entry(3, new LineData("2", "2012-07-19T11:44:57+0200", "dgageot")),
        MapEntry.entry(24, new LineData("6", "2014-11-06T09:23:04+0100", "henryju")));
  }

  private static String unixPath(File file) {
    return file.getAbsolutePath().replace('\\', '/');
  }

  private File checkout(String scmUrl) throws Exception {
    ISVNOptions options = SVNWCUtil.createDefaultOptions(true);
    ISVNAuthenticationManager isvnAuthenticationManager = SVNWCUtil.createDefaultAuthenticationManager(null, null, (char[]) null, false);
    SVNClientManager svnClientManager = SVNClientManager.newInstance(options, isvnAuthenticationManager);
    File out = temp.newFolder();
    SVNUpdateClient updateClient = svnClientManager.getUpdateClient();
    SvnCheckout co = updateClient.getOperationsFactory().createCheckout();
    co.setUpdateLocksOnDemand(updateClient.isUpdateLocksOnDemand());
    co.setSource(SvnTarget.fromURL(SVNURL.parseURIEncoded(scmUrl), SVNRevision.HEAD));
    co.setSingleTarget(SvnTarget.fromFile(out));
    co.setRevision(SVNRevision.HEAD);
    co.setDepth(SVNDepth.INFINITY);
    co.setAllowUnversionedObstructions(false);
    co.setIgnoreExternals(updateClient.isIgnoreExternals());
    co.setExternalsHandler(SvnCodec.externalsHandler(updateClient.getExternalsHandler()));
    co.setTargetWorkingCopyFormat(wcVersion);
    co.run();
    return out;
  }

  public File unzip(String zipName) {
    try {
      File out = temp.newFolder();
      ZipUtils.unzip(new File(new File(REPO_DIR, serverVersion), zipName), out);
      return out;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static BuildResult runSonar(File baseDir, String... keyValues) throws IOException {
    File pom = new File(baseDir, "pom.xml");
    Files.createDirectories(baseDir.toPath().resolve("target/classes"));

    MavenBuild sonar = MavenBuild.create(pom).setGoals("sonar:sonar");
    sonar.setProperty("sonar.scm.disabled", "false");
    sonar.setProperty("sonar.java.binaries", "target/classes");
    sonar.setProperties(keyValues);
    return orchestrator.executeBuild(sonar);
  }

  private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

  private class LineData {

    final String revision;
    final Date date;
    final String author;

    public LineData(String revision, String datetime, String author) throws ParseException {
      this.revision = revision;
      this.date = DATETIME_FORMAT.parse(datetime);
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

    Map<Integer, LineData> result = new HashMap<Integer, LineData>();
    String json = orchestrator.getServer().adminWsClient().get("api/sources/scm", "commits_by_line", "true", "key", fileKey);
    JSONObject obj = (JSONObject) JSONValue.parse(json);
    JSONArray array = (JSONArray) obj.get("scm");
    for (int i = 0; i < array.size(); i++) {
      JSONArray item = (JSONArray) array.get(i);
      // Time part was added in 5.2
      String dateOrDatetime = (String) item.get(2);
      // Revision was added in 5.2
      result.put(((Long) item.get(0)).intValue(), new LineData((String) item.get(3), dateOrDatetime, (String) item.get(1)));
    }
    return result;
  }

}
