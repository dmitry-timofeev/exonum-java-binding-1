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

package com.exonum.binding.storage.indices;

import static com.exonum.binding.storage.indices.MapTestEntry.presentEntry;

import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.proofs.map.flat.CheckedMapProof;
import com.exonum.binding.common.proofs.map.flat.UncheckedMapProof;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

class ProofMapMultiContainsMatcher extends TypeSafeMatcher<ProofMapIndexProxy<HashCode, String>> {

  private final List<MapTestEntry> entries;

  private final CheckedMapProofMultiMatcher mapProofMatcher;

  private ProofMapMultiContainsMatcher(MapTestEntry... entries) {
    this.entries = Arrays.asList(entries);
    mapProofMatcher = CheckedMapProofMultiMatcher.isValid(this.entries);
  }

  @Override
  protected boolean matchesSafely(ProofMapIndexProxy<HashCode, String> map) {
    CheckedMapProof checkedProof = checkProof(map);
    HashCode expectedRootHash = map.getRootHash();

    return mapProofMatcher.matches(checkedProof)
        && checkedProof.compareWithRootHash(expectedRootHash);
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("proof map providing ")
        .appendDescriptionOf(mapProofMatcher);
  }

  @Override
  protected void describeMismatchSafely(ProofMapIndexProxy<HashCode, String> map,
      Description mismatchDescription) {
    mismatchDescription.appendText("was a proof map with Merkle root=")
        .appendValue(map.getRootHash())
        .appendText(" providing a proof that ");
    CheckedMapProof checkedProof = checkProof(map);
    mapProofMatcher.describeMismatch(checkedProof, mismatchDescription);
  }

  private CheckedMapProof checkProof(ProofMapIndexProxy<HashCode, String> map) {
    HashCode[] keys = entries
        .stream()
        .map(MapTestEntry::getKey)
        .toArray(HashCode[]::new);

    UncheckedMapProof proof = map.getProof(keys);
    assert proof != null : "The proof must not be null";

    return proof.check();
  }

  /**
   * Creates a matcher for a proof map that matches iff the map provides a valid proof that all
   * the entries that are expected to be present/absent are contained/not contained in the map.
   * @param entries expected present or absent map entries
   */
  static ProofMapMultiContainsMatcher provesThatCorrect(MapTestEntry... entries) {
    return new ProofMapMultiContainsMatcher(entries);
  }

  /**
   * Creates a matcher for a proof map that matches iff the map provides a valid proof that all
   * the entries that are expected to be presentare contained in the map.
   *
   * @param entriesList expected list of present map entries
   */
  static ProofMapMultiContainsMatcher provesThatCorrect(
      List<MapEntry<HashCode, String>> entriesList) {
    MapTestEntry[] testEntries = entriesList
        .stream()
        .map(e -> presentEntry(e.getKey(), e.getValue()))
        .toArray(MapTestEntry[]::new);
    return new ProofMapMultiContainsMatcher(testEntries);
  }
}
