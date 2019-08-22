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

package com.exonum.binding.common.proofs.list;

import com.exonum.binding.common.hash.HashCode;

/**
 * Represents the proof of absence of an element.
 */
public final class ListProofOfAbsence implements ListProofNode {

  private final HashCode hash;

  @SuppressWarnings("unused")  // Native API
  ListProofOfAbsence(byte[] hash) {
    this.hash = HashCode.fromBytes(hash);
  }

  @Override
  public void accept(ListProofVisitor visitor) {
    visitor.visit(this);
  }

  /**
   * Returns the hash of this proof of absence.
   */
  public HashCode getHash() {
    return hash;
  }
}
