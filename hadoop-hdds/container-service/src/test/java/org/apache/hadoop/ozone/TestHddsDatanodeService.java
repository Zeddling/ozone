/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.hadoop.ozone;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.hdds.DFSConfigKeysLegacy;
import org.apache.hadoop.hdds.HddsConfigKeys;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.scm.ScmConfigKeys;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.ozone.container.common.CleanUpManager;
import org.apache.hadoop.ozone.container.common.SCMTestUtils;
import org.apache.hadoop.ozone.container.common.ScmTestMock;
import org.apache.hadoop.ozone.container.common.utils.StorageVolumeUtil;
import org.apache.hadoop.ozone.container.common.volume.HddsVolume;
import org.apache.hadoop.ozone.container.common.volume.MutableVolumeSet;
import org.apache.hadoop.ozone.container.keyvalue.ContainerTestVersionInfo;
import org.apache.hadoop.thirdparty.com.google.common.io.Files;
import org.apache.ozone.test.GenericTestUtils;
import org.apache.hadoop.util.ServicePlugin;

import org.junit.*;

import static org.apache.hadoop.hdds.HddsConfigKeys.HDDS_BLOCK_TOKEN_ENABLED;
import static org.apache.hadoop.hdds.HddsConfigKeys.HDDS_CONTAINER_TOKEN_ENABLED;
import static org.apache.hadoop.ozone.OzoneConfigKeys.OZONE_SECURITY_ENABLED_KEY;
import static org.junit.Assert.*;

import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test class for {@link HddsDatanodeService}.
 */
@RunWith(Parameterized.class)
public class TestHddsDatanodeService {
  private static File testDir;
  private static OzoneConfiguration conf;
  private HddsDatanodeService service;
  private String[] args = new String[] {};
  private final String schemaVersion;
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final String clusterId = UUID.randomUUID().toString();
  private CleanUpManager[] managers;
  private final int scmServerCount = 1;
  private List<String> serverAddresses;
  private List<RPC.Server> scmServers;
  private List<ScmTestMock> mockServers;
  private ExecutorService executorService;


  public TestHddsDatanodeService(ContainerTestVersionInfo info) {
    this.schemaVersion = info.getSchemaVersion();
    conf = new OzoneConfiguration();
    ContainerTestVersionInfo.setTestSchemaVersion(schemaVersion, conf);
  }

  @Parameterized.Parameters
  public static Iterable<Object[]> parameters() {
    return ContainerTestVersionInfo.versionParameters();
  }

  @Before
  public void setUp() throws IOException {
    //  Set SCM

    serverAddresses = new ArrayList<>();
    scmServers = new ArrayList<>();
    mockServers = new ArrayList<>();

    for (int x = 0; x < scmServerCount; x++) {
      int port = SCMTestUtils.getReuseableAddress().getPort();
      String address = "127.0.0.1";
      serverAddresses.add(address + ":" + port);
      ScmTestMock mock = new ScmTestMock();
      scmServers.add(SCMTestUtils.startScmRpcServer(conf,
          mock, new InetSocketAddress(address, port), 10));
      mockServers.add(mock);
    }

    conf.setStrings(ScmConfigKeys.OZONE_SCM_NAMES, serverAddresses.toArray(new String[0]));

    testDir = GenericTestUtils.getRandomizedTestDir();

    conf.set(HddsConfigKeys.OZONE_METADATA_DIRS, testDir.getPath());
    conf.set(ScmConfigKeys.HDDS_DATANODE_DIR_KEY, testDir.getPath());
    conf.setClass(OzoneConfigKeys.HDDS_DATANODE_PLUGINS_KEY, MockService.class,
        ServicePlugin.class);

    // Tokens only work if security is enabled.  Here we're testing that a
    // misconfig in unsecure cluster does not prevent datanode from starting up.
    // see HDDS-7055
    conf.setBoolean(OZONE_SECURITY_ENABLED_KEY, false);
    conf.setBoolean(HDDS_BLOCK_TOKEN_ENABLED, true);
    conf.setBoolean(HDDS_CONTAINER_TOKEN_ENABLED, true);

    String volumeDir = testDir + "/disk1";
    conf.set(DFSConfigKeysLegacy.DFS_DATANODE_DATA_DIR_KEY, volumeDir);
  }



  @After
  public void tearDown() throws IOException {
    FileUtil.fullyDelete(testDir);
  }

  @Test
  public void testStartup() throws IOException {
    service = HddsDatanodeService.createHddsDatanodeService(args);
    service.start(conf);

    if (CleanUpManager.checkContainerSchemaV3Enabled(conf)) {
      //  Get volumeset and store volumes in temp folders
      //  so as to access them after service.stop()
      MutableVolumeSet volumeSet = service
          .getDatanodeStateMachine().getContainer().getVolumeSet();
      List<HddsVolume> volumes = StorageVolumeUtil.getHddsVolumesList(
          volumeSet.getVolumesList());
      int volumeSetSize = volumes.size();
      File[] tempHddsVolumes = new File[volumeSetSize];
      StringBuilder hddsDirs = new StringBuilder();
      managers = new CleanUpManager[volumeSetSize];

      for (int i = 0; i < volumeSetSize; i++) {
        HddsVolume volume = volumes.get(i);

        volume.format(clusterId);
        volume.createWorkingDir(clusterId, null);

        CleanUpManager manager = new CleanUpManager(volume);
        managers[i] = manager;

        tempHddsVolumes[i] = temporaryFolder.newFolder();
        hddsDirs.append(tempHddsVolumes[i]).append(",");

        //  Write to volume
        File testFile = new File(manager.getTmpPath() + "/testFile.txt");
        Files.touch(testFile);
        assertTrue(testFile.exists());
      }
      conf.set(ScmConfigKeys.HDDS_DATANODE_DIR_KEY, tempHddsVolumes.toString());
    }


    assertNotNull(service.getDatanodeDetails());
    assertNotNull(service.getDatanodeDetails().getHostName());
    assertFalse(service.getDatanodeStateMachine().isDaemonStopped());
    assertNotNull(service.getCRLStore());


    service.stop();
// CRL store must be stopped when the service stops
    assertNull(service.getCRLStore().getStore());
    service.join();
    service.close();

    if (CleanUpManager.checkContainerSchemaV3Enabled(conf) && managers != null) {
      for (CleanUpManager manager: managers) {
        assertTrue(manager.tmpDirIsEmpty());
      }
    }
  }

  static class MockService implements ServicePlugin {

    @Override
    public void close() throws IOException {
      // Do nothing
    }

    @Override
    public void start(Object arg0) {
      // Do nothing
    }

    @Override
    public void stop() {
      // Do nothing
    }
  }
}
