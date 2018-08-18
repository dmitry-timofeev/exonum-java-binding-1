package com.exonum.binding.storage.proofs.map.flat;

import static com.exonum.binding.hash.Funnels.hashCodeFunnel;
import static com.exonum.binding.storage.proofs.DbKeyFunnel.dbKeyFunnel;

import com.exonum.binding.hash.HashCode;
import com.exonum.binding.hash.HashFunction;
import com.exonum.binding.hash.Hashing;
import com.exonum.binding.storage.proofs.map.DbKey;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An unchecked flat map proof, which does not include any intermediate nodes.
 */
public class UncheckedFlatMapProof implements UncheckedMapProof {

  private static final HashFunction HASH_FUNCTION = Hashing.defaultHashFunction();

  private final List<MapProofEntry> proofList;

  // Review: I did the same mistake — an array does not override equals and hashCode,
  // therefore, cannot be reliably used in a hash set. If we need to
  // have a set of byte[], then we probably have to add a ByteString class.
  // I’d add a ByteString interface with the minimum required methods and static factory methods +
  // ArrayByteString implementing that interface — this way it will be easier to replace it.
  // We may use the protobuf version: https://github.com/google/protobuf/blob/master/java/core/src/main/java/com/google/protobuf/ByteString.java
  // or https://github.com/kbolino/ByteString/blob/master/src/main/java/com/kbolino/libraries/bytestring/ByteString.java
  private final Set<byte[]> requestedKeys;

  UncheckedFlatMapProof(List<MapProofEntry> proofList, Set<byte[]> requestedKeys) {
    this.proofList = proofList;
    this.requestedKeys = requestedKeys;
  }

  // Review: The unchecked map proof does not have to carry the requested keys — it is the
  // caller of the map validator who supplies them.
  @SuppressWarnings("unused") // Native API
  static UncheckedFlatMapProof fromUnsorted(MapProofEntry[] proofList, byte[][] requestedKeys) {
    List<MapProofEntry> mapProofEntries = Arrays.asList(proofList);
    mapProofEntries.sort(Comparator.comparing(MapProofEntry::getDbKey));
    Set<byte[]> expectedKeys = new HashSet<>(Arrays.asList(requestedKeys));
    return new UncheckedFlatMapProof(mapProofEntries, expectedKeys);
  }

  @Override
  public List<MapProofEntry> getProofList() {
    return proofList;
  }

  @Override
  public CheckedMapProof check() {
    ProofStatus orderCheckResult = orderCheck();
    Set<CheckedMapProofEntry> leafSet;
    if (orderCheckResult != ProofStatus.CORRECT) {
      return CheckedFlatMapProof.invalid(orderCheckResult);
    }
    if (proofList.isEmpty()) {
      leafSet = Collections.emptySet();
      return CheckedFlatMapProof.correct(getEmptyProofListHash(), leafSet, requestedKeys);
    } else if (proofList.size() == 1) {
      MapProofEntry singleEntry = proofList.get(0);
      if (singleEntry instanceof MapProofEntryLeaf) {
        leafSet = extractEntries(proofList);
        return CheckedFlatMapProof.correct(
            getSingletonProofListHash((MapProofEntryLeaf) singleEntry), leafSet, requestedKeys);
      } else {
        return CheckedFlatMapProof.invalid(ProofStatus.NON_TERMINAL_NODE);
      }
    } else {
      Deque<MapProofEntry> contour = new ArrayDeque<>();
      MapProofEntry first = proofList.get(0);
      MapProofEntry second = proofList.get(1);
      DbKey lastPrefix = first.getDbKey().commonPrefix(second.getDbKey());
      contour.push(first);
      contour.push(second);
      for (int i = 2; i < proofList.size(); i++) {
        MapProofEntry currentEntry = proofList.get(i);
        DbKey newPrefix = contour.peek().getDbKey().commonPrefix(currentEntry.getDbKey());
        while (contour.size() > 1
            && newPrefix.getNumSignificantBits() < lastPrefix.getNumSignificantBits()) {
          lastPrefix = fold(contour, lastPrefix).orElse(lastPrefix);
        }
        contour.push(currentEntry);
        lastPrefix = newPrefix;
      }
      while (contour.size() > 1) {
        lastPrefix = fold(contour, lastPrefix).orElse(lastPrefix);
      }
      leafSet = extractEntries(proofList);
      return CheckedFlatMapProof.correct(contour.peek().getHash(), leafSet, requestedKeys);
    }
  }

  /**
   * Check that all entries are in the valid order.
   * The following algorithm is used:
   * Try to find a first bit index at which this key is greater than the other key (i.e., a bit of
   * this key is 1 and the corresponding bit of the other key is 0), and vice versa. The smaller of
   * these indexes indicates the greater key.
   * If there is no such bit, then lengths of these keys are compared and the key with greater
   * length is considered a greater key.
   * Every following key should be greater than the previous.
   * @return {@code ProofStatus.CORRECT} if every following key is greater than the previous
   *         {@code ProofStatus.INVALID_ORDER} if any following key key is lesser than the previous
   *         {@code ProofStatus.DUPLICATE_PATH} if there are two equal keys
   */
  private ProofStatus orderCheck() {
    for (int i = 1; i < proofList.size(); i++) {
      DbKey key = proofList.get(i - 1).getDbKey();
      DbKey nextKey = proofList.get(i).getDbKey();
      int comparisonResult = nextKey.compareTo(key);
      if (comparisonResult < 0) {
        return ProofStatus.INVALID_ORDER;
      } else if (comparisonResult == 0) {
        return ProofStatus.DUPLICATE_PATH;
      }
    }
    return ProofStatus.CORRECT;
  }

  /**
   * Folds two last entries in a contour and replaces them with the folded entry.
   * Returns an updated common prefix between two last entries in the contour.
   */
  private Optional<DbKey> fold(Deque<MapProofEntry> contour, DbKey lastPrefix) {
    MapProofEntry lastEntry = contour.pop();
    MapProofEntry penultimateEntry = contour.pop();
    MapProofEntry newEntry =
        new MapProofEntryBranch(lastPrefix, computeBranchHash(penultimateEntry, lastEntry));
    Optional<DbKey> commonPrefix;
    if (!contour.isEmpty()) {
      MapProofEntry previousEntry = contour.peek();
      commonPrefix = Optional.of(previousEntry.getDbKey().commonPrefix(lastPrefix));
    } else {
      commonPrefix = Optional.empty();
    }

    contour.push(newEntry);
    return commonPrefix;
  }

  private Set<CheckedMapProofEntry> extractEntries(List<MapProofEntry> proofList) {
    Set<CheckedMapProofEntry> leafList = new HashSet<>();
    for (MapProofEntry entry: proofList) {
      if (entry instanceof MapProofEntryLeaf) {
        byte[] key = entry.getDbKey().getKeySlice();
        byte[] value = ((MapProofEntryLeaf) entry).getValue();
        CheckedMapProofEntry checkedEntry = new CheckedMapProofEntry(key, value);
        leafList.add(checkedEntry);
      }
    }
    return leafList;
  }

  private static HashCode computeBranchHash(MapProofEntry leftChild, MapProofEntry rightChild) {
    return HASH_FUNCTION
        .newHasher()
        .putObject(leftChild.getHash(), hashCodeFunnel())
        .putObject(rightChild.getHash(), hashCodeFunnel())
        .putObject(leftChild.getDbKey(), dbKeyFunnel())
        .putObject(rightChild.getDbKey(), dbKeyFunnel())
        .hash();
  }

  private static HashCode getEmptyProofListHash() {
    return HashCode.fromBytes(new byte[Hashing.DEFAULT_HASH_SIZE_BYTES]);
  }

  private static HashCode getSingletonProofListHash(MapProofEntryLeaf entry) {
    return HASH_FUNCTION.newHasher()
        .putObject(entry.getDbKey(), dbKeyFunnel())
        .putObject(entry.getHash(), hashCodeFunnel())
        .hash();
  }
}
