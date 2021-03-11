/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.ozone.container.upgrade;

import static org.apache.hadoop.hdds.upgrade.HDDSLayoutFeature.DATANODE_SCHEMA_V2;

import org.apache.hadoop.hdds.upgrade.HDDSLayoutFeature;
import org.apache.hadoop.hdds.upgrade.HDDSUpgradeAction;
import org.apache.hadoop.ozone.container.common.statemachine.DatanodeStateMachine;

/**
 * Catalog of HDDS features and their corresponding DataNode action.
 * It is OK to skip HDDS features from the catalog that do not have
 * any specific DataNodeActions.
 */
public enum DataNodeLayoutAction {
  DatanodeSchemaV2(DATANODE_SCHEMA_V2, new DatanodeSchemaV2FinalizeAction());

  //////////////////////////////  //////////////////////////////

  private HDDSLayoutFeature hddsFeature;
  private HDDSUpgradeAction<DatanodeStateMachine> action;

  DataNodeLayoutAction(HDDSLayoutFeature feature,
                       HDDSUpgradeAction<DatanodeStateMachine> action) {
    this.hddsFeature = feature;
    this.action = action;
    this.hddsFeature.setDataNodeUpgradeAction(action);
  }

  public HDDSLayoutFeature getHddsFeature() {
    return hddsFeature;
  }
}
