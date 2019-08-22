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
 * Review: Not necessarily a (single) element, but of the requested range of elements.
 * Represents the proof of absence of an element.
 */
public final class ListProofOfAbsence implements ListProofNode {

  /*
  Review: Which hash?
   */
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
   * Review: Not of 'this proof of absence'. Please clarify which hash in the signature and the docs
   * (is it the Merkle root hash?).
   *
   * Returns the hash of this proof of absence.
   */
  public HashCode getHash() {
    return hash;
  }
}
