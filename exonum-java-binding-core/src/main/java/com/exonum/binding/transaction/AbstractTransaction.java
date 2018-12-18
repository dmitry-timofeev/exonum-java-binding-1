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

import static com.google.common.base.Preconditions.checkNotNull;

import com.exonum.binding.common.hash.HashCode;

/**
 * An abstract Exonum transaction. It includes a reference to a raw Exonum transaction
 * ({@link AbstractTransaction#rawTransaction}, representing this transaction.
 */
/*
Review: Is this class needed at all?
 */
public abstract class AbstractTransaction implements Transaction {

  /**
   * A binary Exonum message, representing this transaction.
   */
  protected final transient RawTransaction rawTransaction;

  protected AbstractTransaction(RawTransaction rawTransaction) {
    this.rawTransaction = checkNotNull(rawTransaction);
  }

/*
Review: as we discussed, that is not a valid implementation, it is not the hash of the message,
just the tx payload which is of little value.
 */
  @Override
  public HashCode hash() {
    return rawTransaction.hash();
  }

}
