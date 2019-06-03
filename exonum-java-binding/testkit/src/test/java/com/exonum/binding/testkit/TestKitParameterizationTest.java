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

package com.exonum.binding.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.exonum.binding.blockchain.Blockchain;
import com.exonum.binding.storage.database.Snapshot;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TestKitParameterizationTest {

  @RegisterExtension
  static TestKitExtension testKitExtension = new TestKitExtension(
      TestKit.builder()
          .withService(TestServiceModule.class));

  @Test
  void testDefaultTestKit(TestKit testKit) {
    /*
     Review: 'validator`.
     Also, as this test relies on that and the num of validators, please set that explicitly
     on the builder (as TEMPLATE_NODE_TYPE, TEMPLATE_NUM_VALIDATORS).
     */
    // Check that main TestKit node is an auditor
    assertThat(testKit.getEmulatedNode().getValidatorId()).isNotEmpty();
    testKit.withSnapshot(verifyNumValidators(1));
  }

  @Test
  void testTestKitAuditor(@Auditor TestKit testKit) {
    // Check that main TestKit node is an auditor
    assertThat(testKit.getEmulatedNode().getValidatorId()).isEmpty();
  }

  @Test
  void testTestKitValidatorCount(@ValidatorCount(validatorCount = 8) TestKit testKit) {
    testKit.withSnapshot(verifyNumValidators(8));
  }

  // Review: PS
  private static Function<Snapshot, Void> verifyNumValidators(int expected) {
    return (view) -> {
      Blockchain blockchain = Blockchain.newInstance(view);
      assertThat(blockchain.getActualConfiguration().validatorKeys().size())
          .isEqualTo(expected);
      return null;
    };
  }
}
