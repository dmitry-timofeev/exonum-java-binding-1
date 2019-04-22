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

import static com.exonum.binding.testkit.TestService.constructAfterCommitTransaction;
import static com.exonum.binding.testkit.TestTransaction.BODY_CHARSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.exonum.binding.blockchain.Block;
import com.exonum.binding.blockchain.Blockchain;
import com.exonum.binding.common.blockchain.TransactionResult;
import com.exonum.binding.common.crypto.CryptoFunction;
import com.exonum.binding.common.crypto.CryptoFunctions;
import com.exonum.binding.common.crypto.KeyPair;
import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.message.TransactionMessage;
import com.exonum.binding.service.AbstractServiceModule;
import com.exonum.binding.service.Node;
import com.exonum.binding.service.Service;
import com.exonum.binding.service.ServiceModule;
import com.exonum.binding.service.TransactionConverter;
import com.exonum.binding.storage.database.View;
import com.exonum.binding.storage.indices.MapIndex;
import com.exonum.binding.storage.indices.ProofMapIndexProxy;
import com.exonum.binding.transaction.RawTransaction;
import com.exonum.binding.transaction.Transaction;
import com.exonum.binding.util.LibraryLoader;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Singleton;
import io.vertx.ext.web.Router;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestKitTest {

  private static final CryptoFunction CRYPTO_FUNCTION = CryptoFunctions.ed25519();
  private static final KeyPair KEY_PAIR = CRYPTO_FUNCTION.generateKeyPair();

  static {
    LibraryLoader.load();
  }

  /*( Review:
  Where do we test that:
    - A genesis block is created *upon test network instantiation*.
    - A service gets its APIs mounted?
      -  and is provided with a Node that has the same identifiers as EmulatedNode.
    - Error reporting on transactions that (a) do not belong to any service; (b) fail to deserialize?
    - Arguments passed to afterCommit (BlockCommitEvent)?

    For some of these a ServiceModule providing a mock might be useful.
   */
  @Test
  void createTestKitForSingleService() {
    TestService service;
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      service = testKit.getService(TestService.SERVICE_ID, TestService.class);
      checkTestServiceInitialization(testKit, service);
        // Review: Can we (here and below) verify for each service that it is properly initialized:
        //  - its initialize is called
        //  - its API is mounted
        //  - anything else?
    }
  }

  @Test
  void createTestKitWithBuilderForSingleService() {
    TestService service;
    try (TestKit testKit = TestKit.builder(EmulatedNodeType.VALIDATOR)
        .withService(TestServiceModule.class)
        .build()) {
      service = testKit.getService(TestService.SERVICE_ID, TestService.class);
      checkTestServiceInitialization(testKit, service);
    }
  }

  @Test
  void createTestKitWithBuilderForMultipleSameServices() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    List<Class<? extends ServiceModule>> serviceModules = ImmutableList.of(TestServiceModule.class,
        TestServiceModule.class);
    TestKit.Builder testKitBuilder = TestKit.builder(EmulatedNodeType.VALIDATOR)
        .withServices(serviceModules);
    assertThrows(exceptionType, testKitBuilder::build);
  }

  @Test
  void createTestKitWithBuilderForMultipleDifferentServices() {
    try (TestKit testKit = TestKit.builder(EmulatedNodeType.VALIDATOR)
        .withService(TestServiceModule.class)
        .withService(TestServiceModule2.class)
        .build()) {
      TestService service = testKit.getService(TestService.SERVICE_ID, TestService.class);
      checkTestServiceInitialization(testKit, service);
      TestService2 service2 = testKit.getService(TestService2.SERVICE_ID, TestService2.class);
      checkTestService2Initialization(testKit, service2);
    }
  }

  @Test
  void createTestKitWithBuilderForMultipleDifferentServicesVarargs() {
    try (TestKit testKit = TestKit.builder(EmulatedNodeType.VALIDATOR)
        .withServices(TestServiceModule.class, TestServiceModule2.class)
        .build()) {
      TestService service = testKit.getService(TestService.SERVICE_ID, TestService.class);
      checkTestServiceInitialization(testKit, service);
      TestService2 service2 = testKit.getService(TestService2.SERVICE_ID, TestService2.class);
      checkTestService2Initialization(testKit, service2);
    }
  }

  private void checkTestServiceInitialization(TestKit testKit, TestService service) {
    // Check that TestKit contains an instance of TestService
    assertThat(service.getId()).isEqualTo(TestService.SERVICE_ID);
    assertThat(service.getName()).isEqualTo(TestService.SERVICE_NAME);

    // Check that TestService API is mounted
    Node serviceNode = service.getNode();
    EmulatedNode emulatedTestKitNode = testKit.getEmulatedNode();
    assertThat(serviceNode.getPublicKey())
        .isEqualTo(emulatedTestKitNode.getServiceKeyPair().getPublicKey());
    testKit.withSnapshot((view) -> {
      // Check that initialization changed database state
      TestSchema testSchema = service.createDataSchema(view);
      // Review: testProofMap and testMap?
      ProofMapIndexProxy<HashCode, String> proofMapIndexProxy = testSchema.testMap();
      Map<HashCode, String> map = toMap(proofMapIndexProxy);
      Map<HashCode, String> expected = ImmutableMap.of(
          TestService.INITIAL_ENTRY_KEY, TestService.INITIAL_ENTRY_VALUE);
      assertThat(map).isEqualTo(expected);

      // Check that genesis block was committed
      Blockchain blockchain = Blockchain.newInstance(view);
      assertThat(blockchain.getBlockHashes().size()).isEqualTo(1L);
      return null;
    });
  }

  private void checkTestService2Initialization(TestKit testKit, TestService2 service) {
    // Check that TestKit contains an instance of TestService2
    assertThat(service.getId()).isEqualTo(TestService2.SERVICE_ID);
    assertThat(service.getName()).isEqualTo(TestService2.SERVICE_NAME);

    // Check that TestService2 API is mounted
    Node serviceNode = service.getNode();
    EmulatedNode emulatedTestKitNode = testKit.getEmulatedNode();
    assertThat(serviceNode.getPublicKey())
        .isEqualTo(emulatedTestKitNode.getServiceKeyPair().getPublicKey());
    testKit.withSnapshot((view) -> {
      // Check that genesis block was committed
      Blockchain blockchain = Blockchain.newInstance(view);
      assertThat(blockchain.getBlockHashes().size()).isEqualTo(1L);
      return null;
    });
  }

  @Test
  void createTestKitWithValidatorAndAdditionalValidators() {
    short additionalValidatorsCount = 2;
    try (TestKit testKit = TestKit.builder(EmulatedNodeType.VALIDATOR)
        .withService(TestServiceModule.class)
        .withValidators(additionalValidatorsCount)
        .build()) {
      testKit.withSnapshot((view) -> {
        Blockchain blockchain = Blockchain.newInstance(view);
        // Number of validator keys is equal to number of validator nodes - in this case one
        // emulated validator node plus additional nodes
        assertThat(blockchain.getActualConfiguration().validatorKeys().size())
            .isEqualTo(additionalValidatorsCount + 1);
        return null;
      });
    }
  }

  @Test
  void createTestKitWithAuditorAndAdditionalValidators() {
    short additionalValidatorsCount = 2;
    try (TestKit testKit = TestKit.builder(EmulatedNodeType.AUDITOR)
        .withService(TestServiceModule.class)
        .withValidators(additionalValidatorsCount)
        .build()) {
      testKit.withSnapshot((view) -> {
        Blockchain blockchain = Blockchain.newInstance(view);
        // Number of validator keys is equal to number of validators - in this case only additional
        // nodes, as main emulated node is an auditor
        assertThat(blockchain.getActualConfiguration().validatorKeys().size())
            .isEqualTo(additionalValidatorsCount);
        return null;
      });
    }
  }

  @Test
  void requestWrongServiceClass() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    try (TestKit testKit = TestKit.builder(EmulatedNodeType.VALIDATOR)
        .withService(TestServiceModule.class)
        .build()) {
      assertThrows(exceptionType,
          () -> testKit.getService(TestService.SERVICE_ID, TestService2.class));
    }
  }

  @Test
  void requestWrongServiceId() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      assertThrows(exceptionType, () -> testKit.getService((short) -1, TestService2.class));
    }
  }

  @Test
  void createTestKitMoreThanMaxServiceNumber() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    List<Class<? extends ServiceModule>> serviceModules = new ArrayList<>();
    for (int i = 0; i < TestKit.MAX_SERVICE_NUMBER + 1; i++) {
      serviceModules.add(TestServiceModule.class);
    }
    TestKit.Builder testKitBuilder = TestKit.builder(EmulatedNodeType.VALIDATOR)
        .withServices(serviceModules);
    assertThrows(exceptionType, testKitBuilder::build);
  }

  @Test
  void createTestKitWithoutServices() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    TestKit.Builder testKitBuilder = TestKit.builder(EmulatedNodeType.VALIDATOR);
    assertThrows(exceptionType, testKitBuilder::build);
  }

// Review: Why removed?
//  @Test
//  void initializationChangesState() {
//    TestKit testKit = TestKit.forService(TestServiceModule.class);
//    Map<HashCode, String> map = testKit.withSnapshot((view) -> {
//      TestService testService = testKit.getService(TestService.SERVICE_ID, TestService.class);
//      TestSchema testSchema = testService.createDataSchema(view);
//      ProofMapIndexProxy<HashCode, String> proofMapIndexProxy = testSchema.testMap();
//      return toMap(proofMapIndexProxy);
//    });
//    /* Review:
//    Map<HashCode, String> expected = ImmutableMap.of(.., ...);
//    assertThat(map).isEqualTo(expected);
//    */
//    assertThat(map).hasSize(1);
//    String initialValue = map.get(TestService.INITIAL_ENTRY_KEY);
//    assertThat(initialValue).isEqualTo(TestService.INITIAL_ENTRY_VALUE);
//  }

  @Test
  void createEmptyBlock() {
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      Block block = testKit.createBlock();
      assertThat(block.getNumTransactions()).isEqualTo(0);

      testKit.withSnapshot((view) -> {
        Blockchain blockchain = Blockchain.newInstance(view);
        assertThat(blockchain.getHeight()).isEqualTo(1);
        assertThat(block).isEqualTo(blockchain.getBlock(1));
        return null;
      });
    }
  }

  @Test
  void afterCommitSubmitsTransaction() {
    Block nextBlock;
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      // Create a block so that afterCommit transaction is submitted
  // Review: It is unclear why this is invoked.
    Block block = testKit.createBlock();
    List<TransactionMessage> inPoolTransactions = testKit
        .findTransactionsInPool(tx -> tx.getServiceId() == TestService.SERVICE_ID);
    assertThat(inPoolTransactions).hasSize(1);
    TransactionMessage inPoolTransaction = inPoolTransactions.get(0);
    RawTransaction afterCommitTransaction = constructAfterCommitTransaction(block.getHeight());

      assertThat(inPoolTransaction.getServiceId())
          .isEqualTo(afterCommitTransaction.getServiceId());
      assertThat(inPoolTransaction.getTransactionId())
          .isEqualTo(afterCommitTransaction.getTransactionId());
      assertThat(inPoolTransaction.getPayload()).isEqualTo(afterCommitTransaction.getPayload());

      nextBlock = testKit.createBlock();
    }
    assertThat(nextBlock.getNumTransactions()).isEqualTo(1);
    assertThat(nextBlock.getHeight()).isEqualTo(2);
  }

  @Test
  void createBlockWithTransactionIgnoresInPoolTransactions() {
    /*
    Review: Why don't we declare these variables inside try-catch and initialize immediately?
     */
    List<TransactionMessage> inPoolTransactions;
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      // Create a block so that afterCommit transaction is submitted
      testKit.createBlock();

      Block block = testKit.createBlockWithTransactions();
      assertThat(block.getNumTransactions()).isEqualTo(0);

      // Two blocks were created, so two afterCommit transactions should be submitted into pool
      inPoolTransactions = testKit
          .findTransactionsInPool(tx -> tx.getServiceId() == TestService.SERVICE_ID);
    }
    assertThat(inPoolTransactions).hasSize(2);
  }

  @Test
  void createBlockWithSingleTransaction() {
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      TransactionMessage message = constructTestTransactionMessage("Test message");
      Block block = testKit.createBlockWithTransactions(message);
      assertThat(block.getNumTransactions()).isEqualTo(1);

      testKit.withSnapshot((view) -> {
        Blockchain blockchain = Blockchain.newInstance(view);
        assertThat(blockchain.getHeight()).isEqualTo(1);
        assertThat(block).isEqualTo(blockchain.getBlock(1));
        Map<HashCode, TransactionResult> transactionResults = toMap(blockchain.getTxResults());
        assertThat(transactionResults).hasSize(1);
        TransactionResult transactionResult = transactionResults.get(message.hash());
        assertThat(transactionResult).isEqualTo(TransactionResult.successful());
        return null;
      });
    }
  }

  @Test
  void createBlockWithTransactions() {
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      TransactionMessage message = constructTestTransactionMessage("Test message");
      TransactionMessage message2 = constructTestTransactionMessage("Test message 2");

      Block block = testKit.createBlockWithTransactions(ImmutableList.of(message, message2));
      assertThat(block.getNumTransactions()).isEqualTo(2);

      testKit.withSnapshot((view) -> {
        checkTransactionsCommittedSuccessfully(view, block, message, message2);
        return null;
      });
    }
  }

  @Test
  void createBlockWithTransactionsVarargs() {
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      TransactionMessage message = constructTestTransactionMessage("Test message");
      TransactionMessage message2 = constructTestTransactionMessage("Test message 2");

      Block block = testKit.createBlockWithTransactions(message, message2);
      assertThat(block.getNumTransactions()).isEqualTo(2);

      testKit.withSnapshot((view) -> {
        checkTransactionsCommittedSuccessfully(view, block, message, message2);
        return null;
      });
    }
  }

  private TransactionMessage constructTestTransactionMessage(String payload) {
    return TransactionMessage.builder()
        .serviceId(TestService.SERVICE_ID)
        .transactionId(TestTransaction.ID)
        .payload(payload.getBytes(BODY_CHARSET))
        .sign(KEY_PAIR, CRYPTO_FUNCTION);
  }

  private void checkTransactionsCommittedSuccessfully(
      View view, Block block, TransactionMessage message, TransactionMessage message2) {
    Blockchain blockchain = Blockchain.newInstance(view);
    assertThat(blockchain.getHeight()).isEqualTo(1);
    assertThat(block).isEqualTo(blockchain.getBlock(1));
    Map<HashCode, TransactionResult> transactionResults = toMap(blockchain.getTxResults());
    assertThat(transactionResults).hasSize(2);

    TransactionResult transactionResult = transactionResults.get(message.hash());
    assertThat(transactionResult).isEqualTo(TransactionResult.successful());
    TransactionResult transactionResult2 = transactionResults.get(message2.hash());
    assertThat(transactionResult2).isEqualTo(TransactionResult.successful());
  }

  @Test
  void createBlockWithTransactionWithWrongServiceId() {
    Class<RuntimeException> exceptionType = RuntimeException.class;
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      short wrongServiceId = (short) (TestService.SERVICE_ID + 1);
      TransactionMessage message = TransactionMessage.builder()
          .serviceId(wrongServiceId)
          .transactionId(TestTransaction.ID)
          .payload("Test message".getBytes(BODY_CHARSET))
          .sign(KEY_PAIR, CRYPTO_FUNCTION);
      /*
      Review:
(1) The thrown exception type is on the wrong abstraction level, RuntimeException communicates nothing. Since it is a testkit,
 I would expect either IllegalArgumentException or https://ota4j-team.github.io/opentest4j/docs/current/api/org/opentest4j/AssertionFailedError.html
(2) As we established in the requirements, the error messages must be user-friendly and tell exactly what
the problem is (or which problems are possible). In this case I think there are two possible problems:
   - A test developer passed an incorrectly serialized transaction (they intended to pass a proper one).
     The implementation of the service is fine.
   - A test dev passed a correctly serialized transaction, but the implementation of the service
     (particularly, TransactionConverter) does not handle that transaction correctly.

In both cases, the actual thrown exception *and* the details about the transaction message are quite useful
to understand what is broken.

Speaking of the implementation, it might be more convenient to check that each tx message is correct and create
a proper exception _in Java_, before passing them to the native code.
       */
      assertThrows(exceptionType, () -> testKit.createBlockWithTransactions(message));
    }
  }

  @Test
  void createBlockWithTransactionWithWrongTransactionId() {
    Class<RuntimeException> exceptionType = RuntimeException.class;
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      short wrongTransactionId = (short) (TestTransaction.ID + 1);
      TransactionMessage message = TransactionMessage.builder()
          .serviceId(TestService.SERVICE_ID)
          .transactionId(wrongTransactionId)
          .payload("Test message".getBytes(BODY_CHARSET))
          .sign(KEY_PAIR, CRYPTO_FUNCTION);
      assertThrows(exceptionType, () -> testKit.createBlockWithTransactions(message));
    }
  }

  @Test
  void getValidatorEmulatedNode() {
    EmulatedNode node;
    try (TestKit testKit = TestKit.forService(TestServiceModule.class)) {
      node = testKit.getEmulatedNode();
    }
    assertThat(node.getNodeType()).isEqualTo(EmulatedNodeType.VALIDATOR);
    assertThat(node.getValidatorId()).isNotEmpty();
    assertThat(node.getServiceKeyPair()).isNotNull();
  }

  @Test
  void getAuditorEmulatedNode() {
    EmulatedNode node;
    try (TestKit testKit = TestKit.builder(EmulatedNodeType.AUDITOR)
        .withService(TestServiceModule.class)
        .build()) {
      node = testKit.getEmulatedNode();
    }
    assertThat(node.getNodeType()).isEqualTo(EmulatedNodeType.AUDITOR);
    assertThat(node.getValidatorId()).isEmpty();
    assertThat(node.getServiceKeyPair()).isNotNull();
  }

  private <K, V> Map<K, V> toMap(MapIndex<K, V> mapIndex) {
    return Maps.toMap(mapIndex.keys(), mapIndex::get);
  }

  public static final class TestServiceModule2 extends AbstractServiceModule {

    private static final TransactionConverter THROWING_TX_CONVERTER = (tx) -> {
      throw new IllegalStateException("No transactions in this service: " + tx);
    };

    @Override
    protected void configure() {
      bind(Service.class).to(TestService2.class).in(Singleton.class);
      bind(TransactionConverter.class).toInstance(THROWING_TX_CONVERTER);
    }
  }

  static final class TestService2 implements Service {

    static short SERVICE_ID = 48;
    static String SERVICE_NAME = "Test service 2";

    private Node node;

    @Override
    public short getId() {
      return SERVICE_ID;
    }

    @Override
    public String getName() {
      return SERVICE_NAME;
    }

    Node getNode() {
      return node;
    }

    @Override
    public Transaction convertToTransaction(RawTransaction rawTransaction) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void createPublicApiHandlers(Node node, Router router) {
      this.node = node;
    }
  }
}
