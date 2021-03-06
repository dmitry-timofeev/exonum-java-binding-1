/*
 * Copyright 2019 The Exonum Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exonum.binding.core.storage.indices;

import static com.exonum.binding.core.storage.indices.ProofMapIndexProxyNoKeyHashingIntegrationTest.TEST_KEYS;

import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.serialization.StandardSerializers;
import com.exonum.binding.core.storage.database.View;
import com.google.common.collect.ImmutableMap;

class ProofMapIndexProxyGroupNoKeyHashingIntegrationTest
    extends BaseMapIndexGroupTestable<HashCode> {

  private static final HashCode PK1 = TEST_KEYS.get(0);
  private static final HashCode PK2 = TEST_KEYS.get(1);
  private static final HashCode PK3 = TEST_KEYS.get(2);

  private static final String GROUP_NAME = "proof_map_group_IT";

  @Override
  ImmutableMap<String, ImmutableMap<HashCode, String>> getTestEntriesById() {
    return ImmutableMap.<String, ImmutableMap<HashCode, String>>builder()
        .put("1", ImmutableMap.of())
        .put("2", ImmutableMap.of(PK1, "V1"))
        .put("3", ImmutableMap.of(PK2, "V2", PK3, "V3"))
        .put("4", ImmutableMap.of(PK3, "V3", PK2, "V2"))
        .put("5", ImmutableMap.of(PK1, "V5", PK2, "V6", PK3, "V7"))
        .build();
  }

  @Override
  ProofMapIndexProxy<HashCode, String> createInGroup(byte[] mapId, View view) {
    return ProofMapIndexProxy.newInGroupUnsafeNoKeyHashing(GROUP_NAME, mapId, view,
        StandardSerializers.hash(), StandardSerializers.string());
  }
}
