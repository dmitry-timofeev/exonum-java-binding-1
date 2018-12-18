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

package com.exonum.binding.transaction;

import com.exonum.binding.common.crypto.PublicKey;
import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.storage.database.Fork;

/**
 * Transaction context class. Contains required information for the transaction execution.
 */
public interface TransactionContext {
  /**
   * Returns database view allowing R/W operations.
   */
  Fork getFork();

  /*
  Review: Of the whole transaction message? Of the transaction message (linkplain BinaryTxMessage) that carried the payload
  from which the transaction was created (linkplain TxConverter)?

Also, please add that
  Each transaction message is uniquely identified by its hash. The messages are persisted
  in the blockchain (link Blockchain#getMessages) and can be fetched by this hash.
   */
  /**
   * Returns SHA-256 hash of the transaction message.
   */
  HashCode getTransactionMessageHash();

  /*
  Review: Please add that the corresponding transaction message is guaranteed to have a correct
  Ed25519 signature with this public key.
   */
  /**
   * Returns public key of the transaction author.
   */
  PublicKey getAuthorPk();

  /**
   * Returns the builder of the transaction context.
   */
  static Builder builder() {
    return new Builder();
  }

  /**
   * Transaction context builder.
   */
  final class Builder {
    private Fork fork;
    private HashCode hash;
    private PublicKey authorPk;

    /**
     * Sets database fork for the context.
     */
    public Builder fork(Fork fork) {
      this.fork = fork;
      return this;
    }

    /**
     * Sets message hash for the context.
     */
    public Builder hash(HashCode hash) {
      this.hash = hash;
      return this;
    }

    /**
     * Sets transaction author public key for the context.
     */
    public Builder authorPk(PublicKey authorPk) {
      this.authorPk = authorPk;
      return this;
    }

    /* Review:
Why not just TransactionContext? Also, we considered defaults for hash & authorPk.
Also, Javadocs are needed. */
    /**
     * Creates the transaction context instance.
     */
    public InternalTransactionContext build() {
      return new InternalTransactionContext(fork, hash, authorPk);
    }

    private Builder() {
    }
  }

}
