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
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.hdds.DFSConfigKeysLegacy;
import org.apache.hadoop.hdds.HddsConfigKeys;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.scm.ScmConfigKeys;
import org.apache.hadoop.hdds.scm.container.common.helpers.StorageContainerException;
import org.apache.hadoop.hdfs.server.datanode.StorageLocation;
import org.apache.hadoop.ozone.container.ContainerTestHelper;
import org.apache.hadoop.ozone.container.common.CleanUpManager;
import org.apache.hadoop.ozone.container.common.impl.ContainerLayoutVersion;
import org.apache.hadoop.ozone.container.common.interfaces.VolumeChoosingPolicy;
import org.apache.hadoop.ozone.container.common.volume.*;
import org.apache.hadoop.ozone.container.keyvalue.ContainerTestVersionInfo;
import org.apache.hadoop.ozone.container.keyvalue.KeyValueContainer;
import org.apache.hadoop.ozone.container.keyvalue.KeyValueContainerData;
import org.apache.ozone.test.GenericTestUtils;
import org.apache.hadoop.util.ServicePlugin;

import org.junit.*;

import static org.apache.hadoop.hdds.HddsConfigKeys.HDDS_BLOCK_TOKEN_ENABLED;
import static org.apache.hadoop.hdds.HddsConfigKeys.HDDS_CONTAINER_TOKEN_ENABLED;
import static org.apache.hadoop.ozone.OzoneConfigKeys.OZONE_SECURITY_ENABLED_KEY;
import static org.apache.hadoop.ozone.container.common.ContainerTestUtils.createDbInstancesForTestIfNeeded;
import static org.junit.Assert.*;

import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test class for {@link HddsDatanodeService}.
 */
@RunWith(Parameterized.class)
public class TestHddsDatanodeService {
  private File testDir;
  private OzoneConfiguration conf;

  private static OzoneConfiguration cleanupConf;
  private HddsDatanodeService service;
  private String[] args = new String[] {};

  private CleanUpManager cleanUpManager;

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private final ContainerLayoutVersion layout;
  private final String schemaVersion;
  private static String hddsPath;
  private MutableVolumeSet volumeSet;
  private static VolumeChoosingPolicy volumeChoosingPolicy;
  private static final String SCM_ID = UUID.randomUUID().toString();
  private static final String DATANODE_UUID = UUID.randomUUID().toString();
  private KeyValueContainerData keyValueContainerData;

  public TestHddsDatanodeService(ContainerTestVersionInfo versionInfo) {
    this.layout = versionInfo.getLayout();
    this.schemaVersion = versionInfo.getSchemaVersion();
    ContainerTestVersionInfo.setTestSchemaVersion(schemaVersion, cleanupConf);
  }

  @Parameterized.Parameters
  public static Iterable<Object[]> parameters() {
    return ContainerTestVersionInfo.versionParameters();
  }

  @BeforeClass
  public static void init() {
    cleanupConf = new OzoneConfiguration();
    hddsPath = GenericTestUtils
        .getTempPath(TestHddsDatanodeService.class.getSimpleName());
    cleanupConf.set(ScmConfigKeys.HDDS_DATANODE_DIR_KEY, hddsPath);
    cleanupConf.set(OzoneConfigKeys.OZONE_METADATA_DIRS, hddsPath);
    volumeChoosingPolicy = new RoundRobinVolumeChoosingPolicy();
  }

  @AfterClass
  public static void shutdown() throws IOException {
    FileUtils.deleteDirectory(new File(hddsPath));
  }

  @Before
  public void setUp() throws Exception {
    testDir = GenericTestUtils.getRandomizedTestDir();
    conf = new OzoneConfiguration();
    conf.set(HddsConfigKeys.OZONE_METADATA_DIRS, testDir.getPath());
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

    if (schemaVersion.equals(OzoneConsts.SCHEMA_V3)) {
      volumeChoosingPolicy = new RoundRobinVolumeChoosingPolicy();
      //containerSet = new ContainerSet(1000);
      volumeSet = new MutableVolumeSet(DATANODE_UUID, cleanupConf, null,
          StorageVolume.VolumeType.DATA_VOLUME, null);
      createDbInstancesForTestIfNeeded(volumeSet, SCM_ID, SCM_ID, cleanupConf);

      for (String dir : cleanupConf.getStrings(ScmConfigKeys.HDDS_DATANODE_DIR_KEY)) {
        StorageLocation location = StorageLocation.parse(dir);
        FileUtils.forceMkdir(new File(location.getNormalizedUri()));
      }
    }
  }

  private long getTestContainerID() {
    return ContainerTestHelper.getTestContainerID();
  }
  public KeyValueContainer createContainer() throws StorageContainerException {
    long cID = getTestContainerID();
    KeyValueContainerData data = new KeyValueContainerData(cID,
        layout,
        ContainerTestHelper.CONTAINER_MAX_SIZE, UUID.randomUUID().toString(),
        UUID.randomUUID().toString());
    data.addMetadata("VOLUME", "shire");
    data.addMetadata("owner)", "bilbo");

    KeyValueContainer container = new KeyValueContainer(data, cleanupConf);
    container.create(volumeSet, volumeChoosingPolicy, SCM_ID);
    container.close();

    keyValueContainerData = container.getContainerData();
    return container;
  }

  @After
  public void tearDown() throws IOException {
    FileUtil.fullyDelete(testDir);

    // Clean up SCM metadata
    FileUtils.deleteDirectory(new File(hddsPath));

    // Clean up SCM datanode container metadata/data
    for (String dir : cleanupConf.getStrings(ScmConfigKeys.HDDS_DATANODE_DIR_KEY)) {
      StorageLocation location = StorageLocation.parse(dir);
      FileUtils.deleteDirectory(new File(location.getNormalizedUri()));
    }
  }

  @Test
  public void testStartup() throws IOException {
    service = HddsDatanodeService.createHddsDatanodeService(args);
    service.start(conf);
    if (schemaVersion.equals(OzoneConsts.SCHEMA_V3)) {
      KeyValueContainer container = createContainer();
      HddsVolume volume = container.getContainerData().getVolume();
      cleanUpManager = new CleanUpManager(volume);
      cleanUpManager.renameDir(keyValueContainerData);
      assertFalse(cleanUpManager.tmpDirIsEmpty());
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

    if (schemaVersion.equals(OzoneConsts.SCHEMA_V3)) {
      assertTrue(cleanUpManager.tmpDirIsEmpty());
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
