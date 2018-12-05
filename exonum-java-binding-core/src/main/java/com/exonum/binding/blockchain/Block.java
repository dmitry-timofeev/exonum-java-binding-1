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

package com.exonum.binding.blockchain;

import com.exonum.binding.blockchain.serialization.BlockSerializer;
import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.hash.Hashing;
import com.exonum.binding.common.serialization.Serializer;
import com.google.auto.value.AutoValue;

/**
 * Exonum block header data structure.
 *
 * <p>A block is essentially a list of transactions, which is a result of the consensus algorithm
 * (thus authenticated by the supermajority of validators) and is applied atomically to the
 * blockchain state.
 *
 * <p>This structure only contains the amount of transactions and the transactions root hash as well
 * as other information, but not the transactions themselves.
 */
@AutoValue
public abstract class Block {

  private final Serializer<Block> blockSerializer = BlockSerializer.INSTANCE;

  public static Block valueOf(
      int proposerId,
      long height,
      int numTransactions,
      HashCode previousBlockHash,
      HashCode txRootHash,
      HashCode stateHash) {
    return new AutoValue_Block(
        proposerId, height, numTransactions, previousBlockHash, txRootHash, stateHash);
  }

  /**
   * Identifier of the leader node which has proposed the block.
   */
  public abstract int getProposerId();

  /**
   * Height of the block, which also identifies the number of this particular block in the
   * blockchain starting from 0 ("genesis" block).
   */
  public abstract long getHeight();

  /**
   * Number of transactions in this block.
   */
  public abstract int getNumTransactions();

  /**
   * Hash link to the previous block in the blockchain.
   */
  public abstract HashCode getPreviousBlockHash();

  /**
   * Root hash of the Merkle tree of transactions in this block.
   *
   * Review: Would you please link the appropriate accessor of that collection, if it is available? {@link Blockchain#getBlockTransactions(Block)}
   */
  public abstract HashCode getTxRootHash();

  /**
   * Hash of the blockchain state after applying transactions in the block.
   */
  public abstract HashCode getStateHash();

  /*
  Review: This code shall just return a value, not serialize the block and compute the hash,
  because we cannot assume that protobuf serialization is deterministic across the languages,
  hence there is no guarantee it'll be the same as the binary stored in core database.

  I think it must be moved to serializer and operate on the bytes that it receives.
   */
  /**
   * Returns the SHA-256 hash of this block.
   */
  public HashCode getBlockHash() {
    return Hashing.defaultHashFunction()
        .hashBytes(blockSerializer.toBytes(this));
  }

}
