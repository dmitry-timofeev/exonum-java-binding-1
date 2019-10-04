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

package com.exonum.binding.testkit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.message.TransactionMessage;
import com.exonum.binding.common.serialization.Serializer;
import com.exonum.binding.core.blockchain.Block;
import com.exonum.binding.core.blockchain.Blockchain;
import com.exonum.binding.core.blockchain.serialization.BlockSerializer;
import com.exonum.binding.core.proxy.AbstractCloseableNativeProxy;
import com.exonum.binding.core.proxy.Cleaner;
import com.exonum.binding.core.proxy.CloseFailuresException;
import com.exonum.binding.core.runtime.ServiceArtifactId;
import com.exonum.binding.core.runtime.ServiceRuntime;
import com.exonum.binding.core.service.BlockCommittedEvent;
import com.exonum.binding.core.service.Node;
import com.exonum.binding.core.service.Service;
import com.exonum.binding.core.storage.database.Fork;
import com.exonum.binding.core.storage.database.Snapshot;
import com.exonum.binding.core.storage.indices.KeySetIndexProxy;
import com.exonum.binding.core.storage.indices.MapIndex;
import com.exonum.binding.core.transaction.RawTransaction;
import com.exonum.binding.core.util.LibraryLoader;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.protobuf.Any;
import com.google.protobuf.MessageLite;
import io.vertx.ext.web.Router;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TestKit for testing blockchain services. It offers simple network configuration emulation
 * (with no real network setup). Although it is possible to add several validator nodes to this
 * network, only one node will create the service instances, execute their operations (e.g.,
 * {@linkplain Service#afterCommit(BlockCommittedEvent)} method logic), and provide access to its
 * state.
 *
 * <p>Only the emulated node has a pool of unconfirmed transactions where a service can submit new
 * transaction messages through {@link Node#submitTransaction(RawTransaction)}; or the test
 * code through {@link #createBlockWithTransactions(TransactionMessage...)}. All transactions
 * from the pool are committed when a new block is created with {@link #createBlock()}.
 *
 * <p>When TestKit is created, Exonum blockchain instance is initialized — service instances are
 * {@linkplain Service#configure(Fork) initialized} and genesis block is committed.
 * Then the {@linkplain Service#createPublicApiHandlers(Node, Router) public API handlers} are
 * created.
 *
 * @see <a href="https://exonum.com/doc/version/0.12/get-started/test-service/">TestKit documentation</a>
 * @see <a href="https://exonum.com/doc/version/0.12/advanced/consensus/specification/#pool-of-unconfirmed-transactions">Pool of Unconfirmed Transactions</a>
 */
public final class TestKit extends AbstractCloseableNativeProxy {

  static {
    LibraryLoader.load();
  }

  /**
   * The maximum number of validators supported by TestKit when a time oracle is enabled. The time
   * oracle does not work in a TestKit with a higher number of validators because the time oracle
   * requires the majority of those validators to submit transactions with time updates, but only a
   * single emulated node submits them.
   */
  public static final short MAX_VALIDATOR_COUNT_WITH_ENABLED_TIME_SERVICE = 3;
  @VisibleForTesting
  static final short MAX_SERVICE_NUMBER = 256;
  @VisibleForTesting
  /*
  Review: Does the core have such restriction?
   */
  static final int MAX_SERVICE_INSTANCE_ID = 1023;
  private static final Serializer<Block> BLOCK_SERIALIZER = BlockSerializer.INSTANCE;
  /*
  Review: Set, it is only used to check if id in the set.
   */
  private final List<Integer> timeServiceIds;

  @VisibleForTesting
  final Cleaner snapshotCleaner = new Cleaner("TestKit#getSnapshot");

  private TestKit(long nativeHandle, List<TimeServiceSpec> timeServiceSpecs) {
    super(nativeHandle, true);
    timeServiceIds = timeServiceSpecs.stream()
        .map(t -> t.getServiceId)
        .collect(toList());
  }

  private static TestKit newInstance(TestKitServiceInstances[] serviceInstances,
                                     EmulatedNodeType nodeType, short validatorCount,
                                     List<TimeServiceSpec> timeServiceSpecs) {
    boolean isAuditorNode = nodeType == EmulatedNodeType.AUDITOR;
    long nativeHandle = nativeCreateTestKit(serviceInstances, isAuditorNode, validatorCount,
        timeServiceSpecs.toArray(new TimeServiceSpec[0]));
    return new TestKit(nativeHandle, timeServiceSpecs);
  }

  /**
   * Deploys and creates a single service with a single validator node in this TestKit network.
   */
  public static TestKit forService(ServiceArtifactId artifactId, String artifactFilename,
                                   String serviceName, int serviceId, MessageLite configuration) {
    /*
     Review: I think using a builder here would reduce code duplication and simplify
things:

```java
    return new Builder()
        .withNodeType(EmulatedNodeType.VALIDATOR)
        .withDeployedArtifact(artifactId, artifactFilename)
        .withService(artifactId, serviceName, serviceId, configuration)
        .build();
```

createTestKitSingleServiceInstance would no longer be needed
     */
    checkServiceId(serviceId);
    TestKitServiceInstances[] testKitServiceInstances = createTestKitSingleServiceInstance(
        artifactId, artifactFilename, serviceName, serviceId, configuration);
    return newInstance(
        testKitServiceInstances, EmulatedNodeType.VALIDATOR, (short) 1, emptyList());
  }

  /**
   * Deploys and creates a single service with default configuration and with a single validator
   * node in this TestKit network.
   */
  public static TestKit forService(ServiceArtifactId artifactId, String artifactFilename,
                                   String serviceName, int serviceId) {
    // Review: Any is not used for configuration anymore, but any protobuf message (MessageLite).
    Any defaultConfiguration = Any.getDefaultInstance();
    return forService(artifactId, artifactFilename, serviceName, serviceId, defaultConfiguration);
  }

  private static TestKitServiceInstances[] createTestKitSingleServiceInstance(
      ServiceArtifactId artifactId, String artifactFilename, String serviceName,
      int serviceId, MessageLite configuration) {
    ServiceSpec serviceSpec = new ServiceSpec(serviceName, serviceId, configuration.toByteArray());
    TestKitServiceInstances testKitServiceInstances = new TestKitServiceInstances(
        artifactId.toString(), artifactFilename, new ServiceSpec[] {serviceSpec});
    return new TestKitServiceInstances[] {testKitServiceInstances};
  }

  /**
   * Returns an instance of a service with the given service name and service class. Only
   * user-defined services can be requested, i.e., it is not possible to get an instance of a
   * built-in service such as the time oracle.
   *
   * @return the service instance or null if there is no service with such id
   * @throws IllegalArgumentException if the service with given id was not found or could not be
   *     cast to given class
   */
  public <T extends Service> T getService(String serviceName, Class<T> serviceClass) {
    Service service = getServiceRuntime().getServiceInstanceByName(serviceName);
    checkArgument(service.getClass().equals(serviceClass),
        "Service (name=%s, class=%s) cannot be cast to %s",
        serviceName, service.getClass().getCanonicalName(), serviceClass.getCanonicalName());
    return serviceClass.cast(service);
  }

  /**
   * Creates a block with the given transaction(s). Transactions are applied in the lexicographical
   * order of their hashes. In-pool transactions will be ignored.
   *
   * @return created block
   * @throws IllegalArgumentException if transactions are malformed or don't belong to this
   *     service
   */
  public Block createBlockWithTransactions(TransactionMessage... transactions) {
    return createBlockWithTransactions(asList(transactions));
  }

  /**
   * Creates a block with the given transactions. Transactions are applied in the lexicographical
   * order of their hashes. In-pool transactions will be ignored.
   *
   * @return created block
   * @throws IllegalArgumentException if transactions are malformed or don't belong to this
   *     service
   */
  public Block createBlockWithTransactions(Iterable<TransactionMessage> transactions) {
    List<TransactionMessage> messageList = ImmutableList.copyOf(transactions);
    checkTransactions(messageList);
    byte[][] transactionMessagesArr = messageList.stream()
        .map(TransactionMessage::toBytes)
        .toArray(byte[][]::new);
    byte[] block = nativeCreateBlockWithTransactions(nativeHandle.get(), transactionMessagesArr);
    return BLOCK_SERIALIZER.fromBytes(block);
  }

  /**
   * Creates a block with all in-pool transactions. Transactions are applied in the lexicographical
   * order of their hashes.
   *
   * @return created block
   */
  public Block createBlock() {
    List<TransactionMessage> inPoolTransactions = getTransactionPool();
    checkTransactions(inPoolTransactions);
    byte[] block = nativeCreateBlock(nativeHandle.get());
    return BLOCK_SERIALIZER.fromBytes(block);
  }

  private void checkTransactions(List<TransactionMessage> transactionMessages) {
    for (TransactionMessage transactionMessage: transactionMessages) {
      checkTransaction(transactionMessage);
    }
  }

  private void checkTransaction(TransactionMessage transactionMessage) {
    int serviceId = transactionMessage.getServiceId();
    // As transactions of time services might be submitted in TestKit that has those service
    // activated, those transactions should be considered valid, as time services are not
    // contained in Java runtime
    if (timeServiceIds.contains(serviceId)) {
      return;
    }
    try {
      // Review: (still) Not appropriate: we don't need to pass the transaction
      getServiceRuntime().convertTransaction(serviceId,
          transactionMessage.getTransactionId(), transactionMessage.getPayload().toByteArray());
      // Review: Just Exception (we don't need Errors).
    } catch (Throwable conversionError) {
      String message = String.format("Service with id=%s failed to convert transaction (%s)."
          + " Make sure that the submitted transaction is correctly serialized, and the service's"
          + " TransactionConverter implementation is correct and handles this transaction as"
          + " expected.", serviceId, transactionMessage);
      throw new IllegalArgumentException(message, conversionError);
    }
  }

  /**
   * Returns a list of in-pool transactions. Please note that the order of transactions in pool
   * does not necessarily match the order in which the clients submitted the messages.
   */
  public List<TransactionMessage> getTransactionPool() {
    return findTransactionsInPool(transactionMessage -> true);
  }

  /**
   * Returns a list of in-pool transactions that match the given predicate.
   */
  public List<TransactionMessage> findTransactionsInPool(Predicate<TransactionMessage> predicate) {
    return applySnapshot((view) -> {
      Blockchain blockchain = Blockchain.newInstance(view);
      MapIndex<HashCode, TransactionMessage> txMessages = blockchain.getTxMessages();
      KeySetIndexProxy<HashCode> poolTxsHashes = blockchain.getTransactionPool();
      return stream(poolTxsHashes)
          .map(txMessages::get)
          .filter(predicate)
          .collect(toList());
    });
  }

  private static <T> Stream<T> stream(KeySetIndexProxy<T> setIndex) {
    return Streams.stream(setIndex);
  }

  private ServiceRuntime getServiceRuntime() {
    // Review: Is this still a question?
    // TODO: create ServiceRuntime instance
    return null;
  }

  /**
   * Performs the given function with a snapshot of the current database state (i.e., the one that
   * corresponds to the latest committed block). In-pool (not yet processed) transactions are also
   * accessible with it in {@linkplain Blockchain#getTxMessages() blockchain}.
   *
   * <p>This method destroys the snapshot once the passed closure completes, compared to
   * {@link #getSnapshot()}, which disposes created snapshots only when TestKit is closed.
   *
   * @param snapshotFunction a function to execute
   * @see #applySnapshot(Function)
   */
  public void withSnapshot(Consumer<Snapshot> snapshotFunction) {
    applySnapshot(s -> {
      snapshotFunction.accept(s);
      return null;
    });
  }

  /**
   * Performs the given function with a snapshot of the current database state (i.e., the one that
   * corresponds to the latest committed block) and returns a result of its execution. In-pool
   * (not yet processed) transactions are also accessible with it in
   * {@linkplain Blockchain#getTxMessages() blockchain}.
   *
   * <p>This method destroys the snapshot once the passed closure completes, compared to
   * {@link #getSnapshot()}, which disposes created snapshots only when TestKit is closed.
   *
   * <p>Consider using {@link #withSnapshot(Consumer)} when returning the result of given function
   * is not needed.
   *
   * @param snapshotFunction a function to execute
   * @param <ResultT> a type the function returns
   * @return the result of applying the given function to the database state
   */
  public <ResultT> ResultT applySnapshot(Function<Snapshot, ResultT> snapshotFunction) {
    try (Cleaner cleaner = new Cleaner("TestKit#applySnapshot")) {
      Snapshot snapshot = createSnapshot(cleaner);
      return snapshotFunction.apply(snapshot);
    } catch (CloseFailuresException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns a snapshot of the current database state (i.e., the one that
   * corresponds to the latest committed block). In-pool (not yet processed) transactions are also
   * accessible with it in {@linkplain Blockchain#getTxMessages() blockchain}.
   *
   * <p>All created snapshots are deleted when this TestKit is {@linkplain #close() closed}.
   * It is forbidden to access the snapshots once the TestKit is closed.
   *
   * <p>If you need to create a large number (e.g. more than a hundred) of snapshots, it is
   * recommended to use {@link #withSnapshot(Consumer)} or {@link #applySnapshot(Function)}, which
   * destroy the snapshots once the passed closure completes.
   */
  public Snapshot getSnapshot() {
    return createSnapshot(snapshotCleaner);
  }

  private Snapshot createSnapshot(Cleaner cleaner) {
    long snapshotHandle = nativeCreateSnapshot(nativeHandle.get());
    return Snapshot.newInstance(snapshotHandle, cleaner);
  }

  /**
   * Returns the context of the node that the TestKit emulates (i.e., on which it instantiates and
   * executes services).
   */
  public EmulatedNode getEmulatedNode() {
    return nativeGetEmulatedNode(nativeHandle.get());
  }

  @Override
  protected void disposeInternal() {
    try {
      snapshotCleaner.close();
    } catch (CloseFailuresException e) {
      throw new IllegalStateException(e);
    } finally {
      nativeFreeTestKit(nativeHandle.get());
    }
  }

  private static native long nativeCreateTestKit(TestKitServiceInstances[] services,
                                                 boolean auditor, short withValidatorCount,
                                                 TimeServiceSpec[] timeProviderSpecs);

  private native long nativeCreateSnapshot(long nativeHandle);

  private native byte[] nativeCreateBlock(long nativeHandle);

  private native byte[] nativeCreateBlockWithTransactions(long nativeHandle, byte[][] transactions);

  private native EmulatedNode nativeGetEmulatedNode(long nativeHandle);

  private native void nativeFreeTestKit(long nativeHandle);

  private static void checkServiceId(int serviceId) {
    /*
    Review: core restrictions
     */
    checkArgument(0 <= serviceId && serviceId <= MAX_SERVICE_INSTANCE_ID,
        "Service id must be in range [0; %s], but was %s",
        MAX_SERVICE_INSTANCE_ID, serviceId);
  }

  /**
   * Creates a new builder for the TestKit. Note that this builder creates a single validator
   * network by default.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for the TestKit.
   */
  public static final class Builder {

    private EmulatedNodeType nodeType = EmulatedNodeType.VALIDATOR;
    private short validatorCount = 1;
    private Multimap<ServiceArtifactId, ServiceSpec> services = ArrayListMultimap.create();
    private HashMap<ServiceArtifactId, String> serviceArtifactFilenames = new HashMap<>();
    private List<TimeServiceSpec> timeServiceSpecs = new ArrayList<>();

    private Builder() {}

    /**
     * Returns a copy of this TestKit builder.
     */
    Builder copy() {
      Builder builder = new Builder()
          .withNodeType(nodeType)
          .withValidators(validatorCount);
      // Review: Is a shallow copy of a builder OK?
      builder.timeServiceSpecs = timeServiceSpecs;
      builder.services = services;
      builder.serviceArtifactFilenames = serviceArtifactFilenames;
      return builder;
    }

    /**
     * Sets the type of the main TestKit node - either validator or auditor. Note that
     * {@link Service#afterCommit(BlockCommittedEvent)} logic will only be called on the main
     * TestKit node of this type
     */
    public Builder withNodeType(EmulatedNodeType nodeType) {
      this.nodeType = nodeType;
      return this;
    }

    /**
     * Sets number of validator nodes in the TestKit network, should be positive. Note that
     * regardless of the configured number of validators, only a single service will be
     * instantiated. Equal to one by default.
     *
     * <p>Note that validator count should be
     * {@value #MAX_VALIDATOR_COUNT_WITH_ENABLED_TIME_SERVICE} or less if time service is enabled.
     *
     * @throws IllegalArgumentException if validatorCount is less than one
     */
    public Builder withValidators(short validatorCount) {
      checkArgument(validatorCount > 0, "TestKit network should have at least one validator node");
      this.validatorCount = validatorCount;
      return this;
    }

    /**
     * Adds a service artifact which would be deployed by the TestKit. Several service artifacts
     * can be added.
     *
     * <p>Once the service artifact is deployed, the service instances can be added with
     * {@link #withService(ServiceArtifactId, String, int, MessageLite)}.
     */
    public Builder withDeployedArtifact(ServiceArtifactId serviceArtifactId, String artifactFilename) {
      serviceArtifactFilenames.put(serviceArtifactId, artifactFilename);
      return this;
    }

    /**
     * Adds a service specification with which the TestKit would create the corresponding service
     * instance. Several service specifications can be added. All services are started and
     * configured before the genesis block.
     * instance. Several service specifications can be added.
     * Review: Also, what would happen if configuration of such service
     * fails? Will I get an exception or just silent start failure, as with 'dynamic' services?)
     *
     * <p>Note that the corresponding service artifact with equal serviceArtifactId should be
     * deployed with {@link #withDeployedArtifact(ServiceArtifactId, String)}.
     */
    public Builder withService(ServiceArtifactId serviceArtifactId, String serviceName,
                               int serviceId, MessageLite configuration) {
      /*
      Review: @skletsun Does testkit/core check properly (with reasonable error messages)
that there are no duplicate ids assigned? The builder accepts ids for regular and time services,
and something has to check they do not intersect.
       */
      checkServiceId(serviceId);
      checkServiceArtifactIsDeployed(serviceArtifactId);
      ServiceSpec serviceSpec = new ServiceSpec(serviceName, serviceId,
          configuration.toByteArray());
      services.put(serviceArtifactId, serviceSpec);
      return this;
    }

    private void checkServiceArtifactIsDeployed(ServiceArtifactId serviceArtifactId) {
      checkArgument(serviceArtifactFilenames.containsKey(serviceArtifactId),
          "Service %s should be deployed first in order to be created", serviceArtifactId);
    }

    /**
     * Adds a service specification with which the TestKit would create the corresponding service
     * Review: with no configuration. (there is no 'default' in the core, it is default of the testkit).
     * instance with default configuration. Several service specifications can be added. All
     * services are started and configured before the genesis block.
     *
     * <p>Note that the corresponding service artifact with equal serviceArtifactId should be
     * deployed with {@link #withDeployedArtifact(ServiceArtifactId, String)}.
     */
    public Builder withService(ServiceArtifactId serviceArtifactId, String serviceName,
                               int serviceId) {
      /*
      Review: I think a default is just empty array, but any could also work.
       */
      Any defaultConfiguration = Any.getDefaultInstance();
      return withService(serviceArtifactId, serviceName, serviceId, defaultConfiguration);
    }

    /**
     * Adds a time service specification with which the TestKit would create the corresponding
     * time service instance. Several time service specifications can be added.
     *
     * <p>Note that validator count should be
     * {@value #MAX_VALIDATOR_COUNT_WITH_ENABLED_TIME_SERVICE} or less if time service is enabled.
     */
    public Builder withTimeService(String serviceName, int serviceId, TimeProvider timeProvider) {
      TimeProviderAdapter timeProviderAdapter = new TimeProviderAdapter(timeProvider);
      timeServiceSpecs.add(new TimeServiceSpec(serviceName, serviceId, timeProviderAdapter));
      return this;
    }

    /**
     * Creates the TestKit instance.
     *
     * @throws IllegalArgumentException if validator count is invalid
     * @throws IllegalArgumentException if service number is invalid
     */
    public TestKit build() {
      checkCorrectServiceNumber(services.size());
      checkCorrectValidatorNumber();
      TestKitServiceInstances[] testKitServiceInstances = mergeServiceSpecs();
      return newInstance(testKitServiceInstances, nodeType, validatorCount, timeServiceSpecs);
    }

    /**
     * Turn collection of service instances into a list of
     * {@linkplain TestKitServiceInstances} objects for native to work with.
     */
    private TestKitServiceInstances[] mergeServiceSpecs() {
      checkDeployedArtifactsAreUsed();
      return serviceArtifactFilenames.entrySet().stream()
          .map(this::aggregateServiceSpecs)
          .toArray(TestKitServiceInstances[]::new);
    }

    private void checkDeployedArtifactsAreUsed() {
      /*
      Review: I suggest either both names as xArtifactIds or none (xIds).
       */
      Set<ServiceArtifactId> serviceIds = services.keySet();
      Set<ServiceArtifactId> deployedArtifactIds = serviceArtifactFilenames.keySet();
      Sets.SetView<ServiceArtifactId> unusedArtifacts =
          Sets.difference(deployedArtifactIds, serviceIds);
      checkArgument(unusedArtifacts.isEmpty(),
          "Following service artifacts were deployed, but not used for service instantiation: %s",
          /*
Review: Both SetView and ServiceArtifactIds must have proper toString with no redundant information,
don't they?
           */
          unusedArtifacts.stream()
              .map(ServiceArtifactId::toString)
              .collect(Collectors.joining(", ")));
    }

    /**
     * Aggregates service instances specifications of a given service artifact id as a
     * {@linkplain TestKitServiceInstances} object.
     */
    private TestKitServiceInstances aggregateServiceSpecs(
        Map.Entry<ServiceArtifactId, String> serviceArtifact) {
      ServiceArtifactId serviceArtifactId = serviceArtifact.getKey();
      ServiceSpec[] serviceSpecs = services.get(serviceArtifactId).toArray(new ServiceSpec[0]);
      return new TestKitServiceInstances(
          serviceArtifactId.toString(), serviceArtifact.getValue(), serviceSpecs);
    }

    private void checkCorrectValidatorNumber() {
      if (!timeServiceSpecs.isEmpty()) {
        checkArgument(validatorCount <= MAX_VALIDATOR_COUNT_WITH_ENABLED_TIME_SERVICE,
            "Number of validators (%s) should be less than or equal to %s when TimeService is"
                + " instantiated.",
            validatorCount, MAX_VALIDATOR_COUNT_WITH_ENABLED_TIME_SERVICE);
      }
    }

    private void checkCorrectServiceNumber(int serviceCount) {
      checkArgument(0 <= serviceCount && serviceCount <= MAX_SERVICE_NUMBER,
          "Number of services must be in range [0; %s], but was %s",
          MAX_SERVICE_NUMBER, serviceCount);
    }
  }
}
