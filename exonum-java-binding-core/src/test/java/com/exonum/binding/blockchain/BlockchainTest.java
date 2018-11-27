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
 *
 */

package com.exonum.binding.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exonum.binding.common.blockchain.Block;
import com.exonum.binding.common.blockchain.TransactionLocation;
import com.exonum.binding.common.blockchain.TransactionResult;
import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.storage.indices.ListIndexProxy;
import com.exonum.binding.storage.indices.MapIndex;
import com.exonum.binding.storage.indices.ProofListIndexProxy;
import com.exonum.binding.storage.indices.ProofMapIndexProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainTest {

  private static final long HEIGHT = 10L;

  private Blockchain blockchain;

  @Mock
  private CoreSchemaProxy mockSchema;

  @BeforeEach
  void setUp() {
    blockchain = new Blockchain(mockSchema);
  }

  @Test
  void getHeight() {
    when(mockSchema.getHeight()).thenReturn(HEIGHT);

    assertThat(blockchain.getHeight()).isEqualTo(HEIGHT);
  }

  @Test
  void getAllBlockHashes() {
    ListIndexProxy mockListIndex = mock(ListIndexProxy.class);

    when(mockSchema.getAllBlockHashes()).thenReturn(mockListIndex);

    assertThat(blockchain.getAllBlockHashes()).isEqualTo(mockListIndex);
  }

  @Test
  void getBlockTransactionsByHeight() {
    ProofListIndexProxy mockListIndex = mock(ProofListIndexProxy.class);

    when(mockSchema.getBlockTransactions(HEIGHT)).thenReturn(mockListIndex);

    assertThat(blockchain.getBlockTransactions(HEIGHT)).isEqualTo(mockListIndex);
  }

  @Test
  void getBlockTransactionsByBlockId() {
    ProofListIndexProxy mockListIndex = mock(ProofListIndexProxy.class);
    MapIndex mockMapIndex = mock(MapIndex.class);
    HashCode blockId = HashCode.fromString("ab");
    /* Review:
    Here and elsewhere: please do not mock values, use a builder/predefined factory method
    if you'd like to hide irrelevant details about the value (e.g., its hash).
     */

    Block mockBlock = mock(Block.class);

    when(mockSchema.getBlocks()).thenReturn(mockMapIndex);
    when(mockMapIndex.get(blockId)).thenReturn(mockBlock);
    when(mockBlock.getHeight()).thenReturn(HEIGHT);
    when(mockSchema.getBlockTransactions(HEIGHT)).thenReturn(mockListIndex);

    assertThat(blockchain.getBlockTransactions(blockId)).isEqualTo(mockListIndex);
  }

  @Test
  void getNonexistentBlockTransactionsByBlockId() {
    MapIndex mockMapIndex = mock(MapIndex.class);
    HashCode blockId = HashCode.fromString("ab");

    when(mockSchema.getBlocks()).thenReturn(mockMapIndex);
    when(mockMapIndex.get(blockId)).thenReturn(null);

    assertThat(blockchain.getBlockTransactions(blockId)).isEqualTo(null);
  }

  @Test
  void getBlockTransactionsByBlock() {
    ProofListIndexProxy mockListIndex = mock(ProofListIndexProxy.class);

    when(mockSchema.getBlockTransactions(HEIGHT)).thenReturn(mockListIndex);
    Block mockBlock = mock(Block.class);
    when(mockBlock.getHeight()).thenReturn(HEIGHT);

    assertThat(blockchain.getBlockTransactions(mockBlock)).isEqualTo(mockListIndex);
  }

  @Test
  void getTxMessages() {
    MapIndex mockMapIndex = mock(MapIndex.class);
    when(mockSchema.getTxMessages()).thenReturn(mockMapIndex);

    assertThat(blockchain.getTxMessages()).isEqualTo(mockMapIndex);
  }

  @Test
  void getTxResults() {
    ProofMapIndexProxy mockMapIndex = mock(ProofMapIndexProxy.class);
    when(mockSchema.getTxResults()).thenReturn(mockMapIndex);

    assertThat(blockchain.getTxResults()).isEqualTo(mockMapIndex);
  }

  @Test
  void getTxResult() {
    ProofMapIndexProxy mockMapIndex = mock(ProofMapIndexProxy.class);
    HashCode messageHash = HashCode.fromString("ab");
    TransactionResult mockTxResult = mock(TransactionResult.class);

    when(mockMapIndex.get(messageHash)).thenReturn(mockTxResult);
    when(mockSchema.getTxResults()).thenReturn(mockMapIndex);

    assertThat(blockchain.getTxResult(messageHash)).isEqualTo(mockTxResult);
  }

  @Test
  void getTxLocations() {
    MapIndex mockMapIndex = mock(MapIndex.class);
    when(mockSchema.getTxLocations()).thenReturn(mockMapIndex);

    assertThat(blockchain.getTxLocations()).isEqualTo(mockMapIndex);
  }

  @Test
  void getTxLocation() {
    MapIndex mockMapIndex = mock(MapIndex.class);
    HashCode messageHash = HashCode.fromString("ab");
    TransactionLocation mockTxLocation = mock(TransactionLocation.class);

    when(mockMapIndex.get(messageHash)).thenReturn(mockTxLocation);
    when(mockSchema.getTxLocations()).thenReturn(mockMapIndex);

    assertThat(blockchain.getTxLocation(messageHash)).isEqualTo(mockTxLocation);
  }

  @Test
  void getBlocks() {
    MapIndex mockMapIndex = mock(MapIndex.class);
    when(mockSchema.getBlocks()).thenReturn(mockMapIndex);

    assertThat(blockchain.getBlocks()).isEqualTo(mockMapIndex);
  }

  @Test
  void getBlocksByHeight() {
    // TODO: implement
  }

  @Test
  void getBlock() {
    MapIndex mockMapIndex = mock(MapIndex.class);
    HashCode blockHash = HashCode.fromString("ab");
    Block mockBlock = mock(Block.class);

    when(mockMapIndex.get(blockHash)).thenReturn(mockBlock);
    when(mockSchema.getBlocks()).thenReturn(mockMapIndex);

    assertThat(blockchain.getBlock(blockHash)).isEqualTo(mockBlock);
  }

  @Test
  void getLastBlock() {
    Block mockBlock = mock(Block.class);

    when(mockSchema.getLastBlock()).thenReturn(mockBlock);

    assertThat(blockchain.getLastBlock()).isEqualTo(mockBlock);
  }
}
