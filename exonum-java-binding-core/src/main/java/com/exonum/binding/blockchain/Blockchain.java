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

import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.storage.database.View;
import com.exonum.binding.storage.indices.ListIndex;
import com.exonum.binding.storage.indices.ProofListIndexProxy;
import com.google.common.annotations.VisibleForTesting;

/*
Review:
Not "informational".

Provides read-only access to the blockchain state that is maintained by Exonum core:
blocks, transaction messages, execution results.

I'd also link https://docs.rs/exonum/0.9.4/exonum/blockchain/struct.Schema.html
 */
/**
 * Provides read-only access to the blockchain state
 * and informational indexes maintained by Exonum core.
 */
public final class Blockchain {

  private final CoreSchemaProxy schema;

  @VisibleForTesting
  Blockchain(CoreSchemaProxy schema) {
    this.schema = schema;
  }

  /**
   * Constructs a new blockchain instance for the given database view.
   */
  public static Blockchain newInstance(View view) {
    CoreSchemaProxy coreSchema = CoreSchemaProxy.newInstance(view);
    return new Blockchain(coreSchema);
  }

  /*
Review: Which height does the genesis block have? The first block? Is it equal to the number of blocks (if not, I'd add a warning)?
   */
  /**
   * Returns the height of the latest committed block.
   *
   * @throws RuntimeException if the "genesis block" was not created
   */
  public long getHeight() {
    return schema.getHeight();
  }

/*
Review:
Returns a list of all block hashes, indexed by the block height.
For example, the genesis block will be at index ?, the block at height {@code h} — at index ?.
 */
  /**
   * Returns an list index containing a block hash for every block height
   * (represented by list index id).
   */
  public ListIndex<HashCode> getAllBlockHashes() {
    return schema.getAllBlockHashes();
  }

/*
Review: This javadoc is incorrect.
 */
  /**
   * Returns an proof list index containing block hashes for the given height.
   */
/*
Review: `@param` (what is a valid range of values), `@throws` — what if I don't get it right?
 */
  public ProofListIndexProxy<HashCode> getBlockTransactions(long height) {
    return schema.getBlockTransactions(height);
  }

}
