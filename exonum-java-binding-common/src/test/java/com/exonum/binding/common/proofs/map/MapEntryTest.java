/*
 * Copyright 2018 The Exonum Team
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

package com.exonum.binding.common.proofs.map;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.ByteString;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class MapEntryTest {

  // TODO: this test fails
  /*
Review: ?
1. x equals throws NPE in case of null fields. May be fixed with Objects.equals(a, b)
2. x This configuration might lack prefab values
  `.withPrefabValues(ByteString.class, ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b")))`
3. [ ] The constructor probably has to check for nulls: com.exonum.binding.common.proofs.map.MapEntry.MapEntry(com.google.protobuf.ByteString, com.google.protobuf.ByteString)
   */
  @Test
  void constructorRejectsNulls() {
    new NullPointerTester()
        .testAllPublicConstructors(MapEntry.class);
  }

  @Test
  void verifyEquals() {
    EqualsVerifier.forClass(MapEntry.class)
        .withPrefabValues(
            ByteString.class, ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b"))
        .verify();
  }

}
