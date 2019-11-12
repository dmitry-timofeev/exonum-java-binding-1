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

package com.exonum.binding.core.storage.indices;

import static com.exonum.binding.core.storage.indices.StoragePreconditions.PROOF_MAP_KEY_SIZE;
import static com.exonum.binding.core.storage.indices.StoragePreconditions.checkIndexType;
import static com.google.common.base.Preconditions.checkArgument;

import com.exonum.binding.common.collect.MapEntry;
import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.proofs.map.UncheckedMapProof;
import com.exonum.binding.common.serialization.CheckingSerializerDecorator;
import com.exonum.binding.common.serialization.Serializer;
import com.exonum.binding.common.serialization.StandardSerializers;
import com.exonum.binding.core.proxy.Cleaner;
import com.exonum.binding.core.proxy.NativeHandle;
import com.exonum.binding.core.proxy.ProxyDestructor;
import com.exonum.binding.core.storage.database.View;
import com.google.common.collect.Lists;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * A ProofMapIndexProxy is an index that maps keys to values. A map cannot contain duplicate keys;
 * each key corresponds to at most one value. This map is capable of providing cryptographic proofs
 * that a certain key is mapped to a particular value <em>or</em> that there are no mapping for
 * the key in the map.
 *
 * <p>This map is implemented as a Merkle-Patricia tree. It does not permit null keys and values.
 *
 * <p>This map can be instantiated in two ways - either with keys hashing with methods
 * {@link #newInstance(String, View, Serializer, Serializer)} and
 * {@link #newInGroupUnsafe(String, byte[], View, Serializer, Serializer)} or with no key hashing
 * with methods {@link #newInstanceNoKeyHashing(String, View, Serializer, Serializer)} and
 * {@link #newInGroupUnsafeNoKeyHashing(String, byte[], View, Serializer, Serializer)}. In case of
 * no key hashing keys are required to be 32-byte long. Note that the former option is considered
 * a default one. Review: We discussed why it shall be the default option, and the documentation
 * must reflect that.
 *
 * <p>The "destructive" methods of the map, i.e., the one that change the map contents,
 * are specified to throw {@link UnsupportedOperationException} if
 * the map has been created with a read-only database view.
 *
 * <p>All method arguments are non-null by default.
 *
 * <p>This class is not thread-safe and and its instances shall not be shared between threads.
 *
 * <p>When the view goes out of scope, this map is destroyed. Subsequent use of the closed map
 * is prohibited and will result in {@link IllegalStateException}.
 *
 * @param <K> the type of keys in this map
 * @param <V> the type of values in this map
 * @see View
 */
public final class ProofMapIndexProxy<K, V> extends AbstractIndexProxy implements MapIndex<K, V> {

  private final ProofMapKeyCheckingSerializerDecorator<K> keySerializer;
  private final CheckingSerializerDecorator<V> valueSerializer;

  /**
   * Creates a ProofMapIndexProxy.
   *
   * @param name a unique alphanumeric non-empty identifier of this map in the underlying storage:
   *             [a-zA-Z0-9_]
   * @param view a database view. Must be valid.
   *             If a view is read-only, "destructive" operations are not permitted.
   * @param keySerializer a serializer of keys
   * @param valueSerializer a serializer of values
   * @param <K> the type of keys in the map
   * @param <V> the type of values in the map
   * @throws IllegalStateException if the view is not valid
   * @throws IllegalArgumentException if the name is empty
   * @see StandardSerializers
   */
  public static <K, V> ProofMapIndexProxy<K, V> newInstance(
      String name, View view, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
    IndexAddress address = IndexAddress.valueOf(name);
    long viewNativeHandle = view.getViewNativeHandle();
    LongSupplier nativeMapConstructor = () -> nativeCreate(name, viewNativeHandle, true);

    return getOrCreate(address, view, keySerializer, valueSerializer, nativeMapConstructor, true);
  }

  /**
   * Creates a ProofMapIndexProxy with no key hashing. Requires that keys are 32-byte long.
   * Review: Would it be possible to add a short warning with link to the class-level section?
   *
   * @param name a unique alphanumeric non-empty identifier of this map in the underlying storage:
   *             [a-zA-Z0-9_]
   * @param view a database view. Must be valid.
   *             If a view is read-only, "destructive" operations are not permitted.
   * @param keySerializer a serializer of keys, must always produce 32-byte long values
   * @param valueSerializer a serializer of values
   * @param <K> the type of keys in the map
   * @param <V> the type of values in the map
   * @throws IllegalStateException if the view is not valid
   * @throws IllegalArgumentException if the name is empty
   * @see StandardSerializers
   * @see ProofMapIndexProxy Review: Remove, they are in this class already.
   */
  public static <K, V> ProofMapIndexProxy<K, V> newInstanceNoKeyHashing(
      String name, View view, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
    IndexAddress address = IndexAddress.valueOf(name);
    long viewNativeHandle = view.getViewNativeHandle();
    LongSupplier nativeMapConstructor = () -> nativeCreate(name, viewNativeHandle, false);

    return getOrCreate(address, view, keySerializer, valueSerializer, nativeMapConstructor, false);
  }

  /**
   * Creates a new proof map in a <a href="package-summary.html#families">collection group</a>
   * with the given name.
   *
   * <p>See a <a href="package-summary.html#families-limitations">caveat</a> on index identifiers.
   *
   * @param groupName a name of the collection group
   * @param mapId an identifier of this collection in the group, see the caveats
   * @param view a database view
   * @param keySerializer a serializer of keys
   * @param valueSerializer a serializer of values
   * @param <K> the type of keys in the map
   * @param <V> the type of values in the map
   * @return a new map proxy
   * @throws IllegalStateException if the view is not valid
   * @throws IllegalArgumentException if the name or index id is empty
   * @see StandardSerializers
   */
  public static <K, V> ProofMapIndexProxy<K, V> newInGroupUnsafe(
      String groupName, byte[] mapId, View view, Serializer<K> keySerializer,
      Serializer<V> valueSerializer) {
    IndexAddress address = IndexAddress.valueOf(groupName, mapId);
    long viewNativeHandle = view.getViewNativeHandle();
    LongSupplier nativeMapConstructor =
        () -> nativeCreateInGroup(groupName, mapId, viewNativeHandle, true);

    return getOrCreate(address, view, keySerializer, valueSerializer, nativeMapConstructor, true);
  }

  /**
   * Creates a new proof map in a <a href="package-summary.html#families">collection group</a>
   * with the given name. Requires that keys are 32-byte long.
   *
   * <p>See a <a href="package-summary.html#families-limitations">caveat</a> on index identifiers.
   *
   * @param groupName a name of the collection group
   * @param mapId an identifier of this collection in the group, see the caveats
   * @param view a database view
   * @param keySerializer a serializer of keys, must always produce 32-byte long values
   * @param valueSerializer a serializer of values
   * @param <K> the type of keys in the map
   * @param <V> the type of values in the map
   * @return a new map proxy
   * @throws IllegalStateException if the view is not valid
   * @throws IllegalArgumentException if the name or index id is empty
   * @see StandardSerializers
   * @see ProofMapIndexProxy
   */
  public static <K, V> ProofMapIndexProxy<K, V> newInGroupUnsafeNoKeyHashing(
      String groupName, byte[] mapId, View view, Serializer<K> keySerializer,
      Serializer<V> valueSerializer) {
    IndexAddress address = IndexAddress.valueOf(groupName, mapId);
    long viewNativeHandle = view.getViewNativeHandle();
    LongSupplier nativeMapConstructor =
        () -> nativeCreateInGroup(groupName, mapId, viewNativeHandle, false);

    return getOrCreate(address, view, keySerializer, valueSerializer, nativeMapConstructor, false);
  }

  private static <K, V> ProofMapIndexProxy<K, V> getOrCreate(IndexAddress address, View view,
      Serializer<K> keySerializer, Serializer<V> valueSerializer,
      LongSupplier nativeMapConstructor, boolean keyHashing) {
    return view.findOpenIndex(address)
        .map(ProofMapIndexProxy::<K, V>checkCachedInstance)
        .orElseGet(() -> newMapIndexProxy(address, view, keySerializer, valueSerializer,
            nativeMapConstructor, keyHashing));
  }

  @SuppressWarnings("unchecked") // The compiler is correct: the cache is not type-safe: ECR-3387
  private static <K, V> ProofMapIndexProxy<K, V> checkCachedInstance(StorageIndex cachedIndex) {
    checkIndexType(cachedIndex, ProofMapIndexProxy.class);
    return (ProofMapIndexProxy<K, V>) cachedIndex;
  }

  private static <K, V> ProofMapIndexProxy<K, V> newMapIndexProxy(IndexAddress address, View view,
      Serializer<K> keySerializer, Serializer<V> valueSerializer,
      LongSupplier nativeMapConstructor, boolean keyHashing) {
    ProofMapKeyCheckingSerializerDecorator<K> ks = decorateKeySerializer(keySerializer, keyHashing);
    CheckingSerializerDecorator<V> vs = CheckingSerializerDecorator.from(valueSerializer);

    NativeHandle mapNativeHandle = createNativeMap(view, nativeMapConstructor);

    ProofMapIndexProxy<K, V> map = new ProofMapIndexProxy<>(mapNativeHandle, address, view, ks, vs);
    view.registerIndex(map);
    return map;
  }

  private static <K> ProofMapKeyCheckingSerializerDecorator<K> decorateKeySerializer(
      Serializer<K> keySerializer, boolean keyHashing) {
    if (!keyHashing) {
      ProofMapKeySizeCheckingSerializerDecorator<K> sizeCheckingSerializerDecorator =
          ProofMapKeySizeCheckingSerializerDecorator.from(keySerializer);
      return ProofMapKeyCheckingSerializerDecorator.from(sizeCheckingSerializerDecorator);
    } else {
      // Review: Why is this needed at all if it can return just keySerializer, with no extra
      // decorator types?
      return ProofMapKeyCheckingSerializerDecorator.from(keySerializer);
    }
  }

  private static NativeHandle createNativeMap(View view, LongSupplier nativeMapConstructor) {
    NativeHandle mapNativeHandle = new NativeHandle(nativeMapConstructor.getAsLong());

    Cleaner cleaner = view.getCleaner();
    ProxyDestructor.newRegistered(cleaner, mapNativeHandle, ProofMapIndexProxy.class,
        ProofMapIndexProxy::nativeFree);
    return mapNativeHandle;
  }

  private static native long nativeCreate(String name, long viewNativeHandle, boolean keyHashing);

  private static native long nativeCreateInGroup(String groupName, byte[] mapId,
                                                 long viewNativeHandle, boolean keyHashing);

  private ProofMapIndexProxy(NativeHandle nativeHandle, IndexAddress address, View view,
                             ProofMapKeyCheckingSerializerDecorator<K> keySerializer,
                             CheckingSerializerDecorator<V> valueSerializer) {
    super(nativeHandle, address, view);
    this.keySerializer = keySerializer;
    this.valueSerializer = valueSerializer;
  }

  @Override
  public boolean containsKey(K key) {
    byte[] dbKey = keySerializer.toBytes(key);
    return nativeContainsKey(getNativeHandle(), dbKey);
  }

  private native boolean nativeContainsKey(long nativeHandle, byte[] key);

  /**
   * {@inheritDoc}
   *
   * @param key a proof map key
   * @param value a storage value to associate with the key
   * @throws IllegalStateException if this map is not valid
   * @throws IllegalArgumentException if the size of the key is not 32 bytes (in case of a
   * Review: Here and elsewhere 'a no key hashing proof map' does not sound correct.
   * ... a non-key-hashing proof map
   * ... a proof map that does not hash keys
   * if the size of the key is not 32 bytes *and* this map uses non-hashed keys
   *     no key hashing proof map)
   * @throws UnsupportedOperationException if this map is read-only
   */
  @Override
  public void put(K key, V value) {
    notifyModified();
    long nativeHandle = getNativeHandle();
    putInternal(nativeHandle, key, value);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> sourceMap) {
    notifyModified();
    long nativeHandle = getNativeHandle();
    for (Map.Entry<? extends K, ? extends V> entry : sourceMap.entrySet()) {
      putInternal(nativeHandle, entry.getKey(), entry.getValue());
    }
  }

  private void putInternal(long nativeHandle, K key, V value) {
    byte[] dbKey = keySerializer.toBytes(key);
    byte[] dbValue = valueSerializer.toBytes(value);
    nativePut(nativeHandle, dbKey, dbValue);
  }

  private native void nativePut(long nativeHandle, byte[] key, byte[] value);

  @Override
  public V get(K key) {
    byte[] dbKey = keySerializer.toBytes(key);
    byte[] dbValue = nativeGet(getNativeHandle(), dbKey);
    return (dbValue == null) ? null : valueSerializer.fromBytes(dbValue);
  }

  private native byte[] nativeGet(long nativeHandle, byte[] key);

  /**
   * Returns a proof that there are values mapped to the specified keys or that there are no such
   * mappings.
   *
   * @param key a proof map key which might be mapped to some value
   * @param otherKeys other proof map keys which might be mapped to some values
   * @throws IllegalStateException if this map is not valid
   * @throws IllegalArgumentException if the size of any of the keys is not 32 bytes (in case of a
   *     no key hashing proof map)
   */
  public UncheckedMapProof getProof(K key, K... otherKeys) {
    if (otherKeys.length == 0) {
      return getSingleKeyProof(key);
    } else {
      List<K> keys = Lists.asList(key, otherKeys);
      return getMultiKeyProof(keys);
    }
  }

  /**
   * Returns a proof that there are values mapped to the specified keys or that there are no such
   * mappings.
   *
   * @param keys proof map keys which might be mapped to some values
   * @throws IllegalStateException if this map is not valid
   * @throws IllegalArgumentException if the size of any of the keys is not 32 bytes (in case of a
   *     no key hashing proof map) or keys collection is empty
   */
  public UncheckedMapProof getProof(Collection<? extends K> keys) {
    checkArgument(!keys.isEmpty(), "Keys collection should not be empty");
    if (keys.size() == 1) {
      K key = keys.iterator()
          .next();
      return getSingleKeyProof(key);
    } else {
      return getMultiKeyProof(keys);
    }
  }

  private UncheckedMapProof getSingleKeyProof(K key) {
    byte[] dbKey = keySerializer.toBytes(key);
    return nativeGetProof(getNativeHandle(), dbKey);
  }

  private native UncheckedMapProof nativeGetProof(long nativeHandle, byte[] key);

  private UncheckedMapProof getMultiKeyProof(Collection<? extends K> keys) {
    return nativeGetMultiProof(getNativeHandle(), mergeKeysIntoByteArray(keys));
  }

  private byte[] mergeKeysIntoByteArray(Collection<? extends K> keys) {
    int arraySize = keys.size() * PROOF_MAP_KEY_SIZE;
    ByteBuffer flattenedKeys = ByteBuffer.allocate(arraySize);
    keys.stream()
        .map(keySerializer::toBytes)
        .forEach(flattenedKeys::put);
    return flattenedKeys.array();
  }

  private native UncheckedMapProof nativeGetMultiProof(long nativeHandle, byte[] keys);

  /**
   * Returns the index hash which represents the complete state of this map.
   * Any modifications to the stored entries affect the index hash.
   *
   * @throws IllegalStateException if this map is not valid
   */
  public HashCode getIndexHash() {
    return HashCode.fromBytes(nativeGetIndexHash(getNativeHandle()));
  }

  private native byte[] nativeGetIndexHash(long nativeHandle);

  @Override
  public void remove(K key) {
    notifyModified();
    byte[] dbKey = keySerializer.toBytes(key);
    nativeRemove(getNativeHandle(), dbKey);
  }

  private native void nativeRemove(long nativeHandle, byte[] key);

  @Override
  public Iterator<K> keys() {
    return StorageIterators.createIterator(
        nativeCreateKeysIter(getNativeHandle()),
        this::nativeKeysIterNext,
        this::nativeKeysIterFree,
        dbView,
        modCounter,
        keySerializer::fromBytes
    );
  }

  private native long nativeCreateKeysIter(long nativeHandle);

  private native byte[] nativeKeysIterNext(long iterNativeHandle);

  private native void nativeKeysIterFree(long iterNativeHandle);

  @Override
  public Iterator<V> values() {
    return StorageIterators.createIterator(
        nativeCreateValuesIter(getNativeHandle()),
        this::nativeValuesIterNext,
        this::nativeValuesIterFree,
        dbView,
        modCounter,
        valueSerializer::fromBytes
    );
  }

  private native long nativeCreateValuesIter(long nativeHandle);

  private native byte[] nativeValuesIterNext(long iterNativeHandle);

  private native void nativeValuesIterFree(long iterNativeHandle);

  @Override
  public Iterator<MapEntry<K, V>> entries() {
    return StorageIterators.createIterator(
        nativeCreateEntriesIter(getNativeHandle()),
        this::nativeEntriesIterNext,
        this::nativeEntriesIterFree,
        dbView,
        modCounter,
        (entry) -> entry.toMapEntry(entry, keySerializer, valueSerializer)
    );
  }

  private native long nativeCreateEntriesIter(long nativeHandle);

  private native MapEntryInternal nativeEntriesIterNext(long iterNativeHandle);

  private native void nativeEntriesIterFree(long iterNativeHandle);

  @Override
  public void clear() {
    notifyModified();
    nativeClear(getNativeHandle());
  }

  private native void nativeClear(long nativeHandle);

  private static native void nativeFree(long nativeHandle);
}
