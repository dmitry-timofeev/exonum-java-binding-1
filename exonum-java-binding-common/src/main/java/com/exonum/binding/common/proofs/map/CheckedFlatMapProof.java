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

package com.exonum.binding.common.proofs.map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptySet;

import com.exonum.binding.common.hash.HashCode;
import com.google.protobuf.ByteString;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A checked flat map proof, which does not include any intermediate nodes.
 */
public class CheckedFlatMapProof implements CheckedMapProof {

  private final Set<MapEntry> entries;

  private final Set<ByteString> missingKeys;

  private final HashCode rootHash;

  private final MapProofStatus status;

  private CheckedFlatMapProof(
      MapProofStatus status,
      HashCode rootHash,
      Set<MapEntry> entries,
      Set<ByteString> missingKeys) {
    this.status = checkNotNull(status);
    this.rootHash = checkNotNull(rootHash);
    this.entries = checkNotNull(entries);
    this.missingKeys = checkNotNull(missingKeys);
  }

  /**
   * Creates a valid map proof.
   *
   * @param rootHash the Merkle root hash calculated by the validator
   * @param entries the list of entries that are proved to be in the map
   * @param missingKeys the list of keys that are proved <em>not</em> to be in the map
   * @return a new checked proof
   */
  public static CheckedFlatMapProof correct(
      HashCode rootHash,
      Set<MapEntry> entries,
      Set<ByteString> missingKeys) {
    return new CheckedFlatMapProof(MapProofStatus.CORRECT, rootHash, entries, missingKeys);
  }

  /**
   * Creates an invalid map proof.
   *
   * @param status the status explaining why the proof is not valid;
   *     must not be {@link MapProofStatus#CORRECT}
   * @return a new checked proof
   */
  public static CheckedFlatMapProof invalid(MapProofStatus status) {
    checkArgument(status != MapProofStatus.CORRECT);
    return new CheckedFlatMapProof(
        status, HashCode.fromInt(1), emptySet(), emptySet());
  }

  @Override
  public Set<MapEntry> getEntries() {
    checkValid();
    return entries;
  }

  @Override
  public Set<ByteString> getMissingKeys() {
    checkValid();
    return missingKeys;
  }

  @Override
  public boolean containsKey(ByteString key) {
    checkValid();
    checkThatKeyIsRequested(key);
    /*
Review: This code suggest our structure is not good enough yet.
We shall probably have
```
Map<ByteString, ByteString> entries;

+ containsKey(key : ByteString) -> boolean
+ getEntries() -> Map<ByteString, ByteString> // or Set<Map.Entry<ByteString, ByteString>>?
// I think the former is better, with a proper documentation warning
```
      */
    return entries.stream().anyMatch(entry -> entry.getKey().equals(key));
  }

  @Override
  public HashCode getRootHash() {
    checkValid();
    return rootHash;
  }

  @Override
  public ByteString get(ByteString key) {
    checkValid();
    checkThatKeyIsRequested(key);
    // Review: same as above, must be entries.get(key);
    return entries
        .stream()
        .filter(entry -> entry.getKey().equals(key))
        .map(MapEntry::getValue)
        .findFirst()
        .orElse(null);
  }

  @Override
  public MapProofStatus getStatus() {
    return status;
  }

  @Override
  public boolean compareWithRootHash(HashCode expectedRootHash) {
    checkValid();
    return rootHash.equals(expectedRootHash);
  }

  private void checkValid() {
    checkState(status == MapProofStatus.CORRECT, "Proof is not valid: %s", status);
  }

  private void checkThatKeyIsRequested(ByteString key) {
    /*
Review:
entries.containsKey(key) || missingKeys.contains(key)
     */
    Stream.concat(
        entries.stream().map(MapEntry::getKey),
        missingKeys.stream())
        .filter(entryKey -> entryKey.equals(key))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Key that wasn't among requested keys was checked"));
  }
}
