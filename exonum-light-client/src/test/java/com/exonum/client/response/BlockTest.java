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

package com.exonum.client.response;

import static com.exonum.client.Blocks.BLOCK_1;
import static com.exonum.client.Blocks.BLOCK_1_JSON;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.serialization.json.JsonSerializer;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class BlockTest {

  @Test
  void isEmpty() {
    Block block = aBlock()
        .numTransactions(0)
        .build();

    assertTrue(block.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, Integer.MAX_VALUE})
  void nonEmptyBlock(int numTransactions) {
    Block nonEmptyBlock = aBlock()
        .numTransactions(numTransactions)
        .build();

    assertFalse(nonEmptyBlock.isEmpty());
  }

  @ParameterizedTest
  @EnumSource(FieldNamingPolicy.class)
  void jsonRepresentationIndependentOfNamingPolicy(FieldNamingPolicy policy) {
    Gson gson = JsonSerializer.builder()
        .setFieldNamingPolicy(policy)
        .create();

    Block block = gson.fromJson(BLOCK_1_JSON, Block.class);
    assertThat(block, equalTo(BLOCK_1));
  }

  private static Block.BlockBuilder aBlock() {
    return Block.builder()
        .proposerId(3)
        .height(100)
        .numTransactions(1)
        .previousBlockHash(HashCode.fromString("abc8"))
        .txRootHash(HashCode.fromString("cd5a"))
        .stateHash(HashCode.fromString("efa2"));
  }
}
