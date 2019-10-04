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

import static com.exonum.binding.testkit.TestKit.MAX_SERVICE_INSTANCE_ID;
import static com.exonum.binding.testkit.TestService.constructAfterCommitTransaction;
import static com.exonum.binding.testkit.TestTransaction.BODY_CHARSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.exonum.binding.common.blockchain.TransactionResult;
import com.exonum.binding.common.crypto.CryptoFunction;
import com.exonum.binding.common.crypto.CryptoFunctions;
import com.exonum.binding.common.crypto.KeyPair;
import com.exonum.binding.common.crypto.PublicKey;
import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.message.TransactionMessage;
import com.exonum.binding.core.blockchain.Block;
import com.exonum.binding.core.blockchain.Blockchain;
import com.exonum.binding.core.proxy.Cleaner;
import com.exonum.binding.core.runtime.ServiceArtifactId;
import com.exonum.binding.core.service.AbstractServiceModule;
import com.exonum.binding.core.service.Node;
import com.exonum.binding.core.service.Service;
import com.exonum.binding.core.service.TransactionConverter;
import com.exonum.binding.core.storage.database.Snapshot;
import com.exonum.binding.core.storage.database.View;
import com.exonum.binding.core.storage.indices.MapIndex;
import com.exonum.binding.core.storage.indices.ProofMapIndexProxy;
import com.exonum.binding.core.transaction.RawTransaction;
import com.exonum.binding.time.TimeSchema;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import io.vertx.ext.web.Router;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TestKitTest extends TestKitWithTestArtifact {
  private static final String ARTIFACT_FILENAME_2 = "test-service-2.jar";
  private static final ServiceArtifactId ARTIFACT_ID_2 =
      ServiceArtifactId.of("com.exonum.binding", "test-service-2", "1.0.0");
  private static final String SERVICE_NAME_2 = "Test service 2";
  private static final int SERVICE_ID_2 = 48;

  private String TIME_SERVICE_NAME = "Time service";
  private int TIME_SERVICE_ID = 10;

  private static final CryptoFunction CRYPTO_FUNCTION = CryptoFunctions.ed25519();
  private static final KeyPair KEY_PAIR = CRYPTO_FUNCTION.generateKeyPair();
  private static final ZonedDateTime TIME =
      ZonedDateTime.of(2000, 1, 1, 1, 1, 1, 1, ZoneOffset.UTC);

  @RegisterExtension
  TestKitExtension testKitExtension = new TestKitExtension(
      TestKit.builder()
          .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
          .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID));

  // TODO: create artifact for second service

  @Test
  void createTestKitForSingleService() {
    TestService service;
    try (TestKit testKit = TestKit.forService(ARTIFACT_ID, ARTIFACT_FILENAME,
        SERVICE_NAME, SERVICE_ID)) {
      service = testKit.getService(SERVICE_NAME, TestService.class);
      checkTestServiceInitialization(testKit, service);
    }
  }

  @Test
  void createTestKitForSingleServiceWithCustomConfiguration() {
    TestService service;
    try (TestKit testKit = TestKit.forService(ARTIFACT_ID, ARTIFACT_FILENAME,
        SERVICE_NAME, SERVICE_ID, Any.getDefaultInstance())) {
      service = testKit.getService(SERVICE_NAME, TestService.class);
      checkTestServiceInitialization(testKit, service);
    }
  }

  @Test
  void createTestKitWithBuilderForSingleService() {
    try (TestKit testKit = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID)
        .build()) {
      TestService service = testKit.getService(SERVICE_NAME, TestService.class);
      checkTestServiceInitialization(testKit, service);
    }
  }

  @Test
  void createTestKitWithoutDeployedArtifactThrows() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    TestKit.Builder testKitBuilder = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME);
    IllegalArgumentException thrownException = assertThrows(exceptionType, testKitBuilder::build);
    assertThat(thrownException.getMessage())
        .isEqualTo("Following service artifacts were deployed, but not used for"
            + " service instantiation: ", ARTIFACT_ID.toString());
  }

  @Test
  void createTestKitWithDeployedArtifactWithoutCreatedServicesThrows() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    TestKit.Builder testKitBuilder = TestKit.builder()
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID);
    IllegalArgumentException thrownException = assertThrows(exceptionType, testKitBuilder::build);
    assertThat(thrownException.getMessage())
        .isEqualTo("Service %s should be deployed first in order to be created",
        ARTIFACT_ID.toString());
  }

  @Test
  void createTestKitWithDifferentDeployedArtifactAndCreatedServiceThrows() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    TestKit.Builder testKitBuilder = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID);
    IllegalArgumentException thrownException = assertThrows(exceptionType, testKitBuilder::build);
    assertThat(thrownException.getMessage())
        .isEqualTo("Service %s should be deployed first in order to be created",
        ARTIFACT_ID.toString());
  }

  @Test
  void createTestKitWithBuilderForSingleServiceWithCustomConfiguration() {
    try (TestKit testKit = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID, Any.getDefaultInstance())
        .build()) {
      TestService service = testKit.getService(SERVICE_NAME, TestService.class);
      checkTestServiceInitialization(testKit, service);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, MAX_SERVICE_INSTANCE_ID + 1})
  void createTestKitWithInvalidServiceId(int invalidServiceId) {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    TestKit.Builder testKitBuilder = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME);
    assertThrows(exceptionType,
        () -> testKitBuilder.withService(ARTIFACT_ID, SERVICE_NAME, invalidServiceId));
  }

  @Test
  void createTestKitWithBuilderForMultipleDifferentServices() {
    try (TestKit testKit = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID)
        .withDeployedArtifact(ARTIFACT_ID_2, ARTIFACT_FILENAME_2)
        .withService(ARTIFACT_ID_2, SERVICE_NAME_2, SERVICE_ID_2)
        .build()) {
      TestService service = testKit.getService(SERVICE_NAME, TestService.class);
      checkTestServiceInitialization(testKit, service);
      TestService2 service2 = testKit.getService(SERVICE_NAME_2, TestService2.class);
      checkTestService2Initialization(testKit, service2);
    }
  }

  @Test
  void createTestKitWithTimeService() {
    TimeProvider timeProvider = FakeTimeProvider.create(TIME);
    try (TestKit testKit = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID)
        .withTimeService(TIME_SERVICE_NAME, TIME_SERVICE_ID, timeProvider)
        .build()) {
      TestService service = testKit.getService(SERVICE_NAME, TestService.class);
      checkTestServiceInitialization(testKit, service);
    }
  }

  @Test
  void createTestKitWithSeveralTimeServices() {
    String timeServiceName2 = "Time service 2";
    int timeServiceId2 = 18;
    TimeProvider timeProvider = FakeTimeProvider.create(TIME);
    TimeProvider timeProvider2 = FakeTimeProvider.create(TIME.plusDays(1));
    try (TestKit testKit = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID)
        .withTimeService(TIME_SERVICE_NAME, TIME_SERVICE_ID, timeProvider)
        .withTimeService(timeServiceName2, timeServiceId2, timeProvider2)
        .build()) {
      TestService service = testKit.getService(SERVICE_NAME, TestService.class);
      checkTestServiceInitialization(testKit, service);
    }
  }

  private void checkTestServiceInitialization(TestKit testKit, TestService service) {
    // Check that TestService API is mounted
    Node serviceNode = service.getNode();
    EmulatedNode emulatedTestKitNode = testKit.getEmulatedNode();
    assertThat(serviceNode.getPublicKey())
        .isEqualTo(emulatedTestKitNode.getServiceKeyPair().getPublicKey());

    // Check that initialization changed database state
    Snapshot view = testKit.getSnapshot();
    TestSchema testSchema = service.createDataSchema(view);
    ProofMapIndexProxy<HashCode, String> testProofMap = testSchema.testMap();
    Map<HashCode, String> testMap = toMap(testProofMap);
    Map<HashCode, String> expected = ImmutableMap.of(
        TestService.INITIAL_ENTRY_KEY, TestService.INITIAL_ENTRY_VALUE);
    assertThat(testMap).isEqualTo(expected);

    // Check that genesis block was committed
    Blockchain blockchain = Blockchain.newInstance(view);
    assertThat(blockchain.getBlockHashes().size()).isEqualTo(1L);
  }

  private void checkTestService2Initialization(TestKit testKit, TestService2 service) {
    // Check that TestService2 API is mounted
    Node serviceNode = service.getNode();
    EmulatedNode emulatedTestKitNode = testKit.getEmulatedNode();
    assertThat(serviceNode.getPublicKey())
        .isEqualTo(emulatedTestKitNode.getServiceKeyPair().getPublicKey());

    // Check that genesis block was committed
    Snapshot view = testKit.getSnapshot();
    Blockchain blockchain = Blockchain.newInstance(view);
    assertThat(blockchain.getBlockHashes().size()).isEqualTo(1L);
  }

  @Test
  void createTestKitWithSeveralValidators() {
    short validatorCount = 2;
    try (TestKit testKit = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID)
        .withValidators(validatorCount)
        .build()) {
      Snapshot view = testKit.getSnapshot();
      Blockchain blockchain = Blockchain.newInstance(view);
      assertThat(blockchain.getActualConfiguration().validatorKeys().size())
          .isEqualTo(validatorCount);
    }
  }

  @Test
  void createTestKitWithAuditorAndAdditionalValidators() {
    short validatorCount = 2;
    try (TestKit testKit = TestKit.builder()
        .withNodeType(EmulatedNodeType.AUDITOR)
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID)
        .withValidators(validatorCount)
        .build()) {
      Snapshot view = testKit.getSnapshot();
      Blockchain blockchain = Blockchain.newInstance(view);
      assertThat(blockchain.getActualConfiguration().validatorKeys().size())
          .isEqualTo(validatorCount);
    }
  }

  @Test
  void setInvalidValidatorCount() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    short invalidValidatorCount = 0;
    TestKit.Builder testKitBuilder = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID);
    assertThrows(exceptionType, () -> testKitBuilder.withValidators(invalidValidatorCount));
  }

  @Test
  void requestWrongServiceClass() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    try (TestKit testKit = TestKit.builder()
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID)
        .build()) {
      assertThrows(exceptionType,
          () -> testKit.getService(SERVICE_NAME, TestService2.class));
    }
  }

  @Test
  void requestWrongServiceId(TestKit testKit) {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    assertThrows(exceptionType, () -> testKit.getService("Invalid service name",
        // Review: Why the class changed?
        TestService.class));
  }

  @Test
  void createTestKitMoreThanMaxServiceNumber() {
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    TestKit.Builder testKitBuilder =
        TestKit.builder().withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME);
    for (int i = 0; i < TestKit.MAX_SERVICE_NUMBER + 1; i++) {
      String serviceName = SERVICE_NAME + " " + i;
      int serviceId = SERVICE_ID + i;
      testKitBuilder = testKitBuilder.withService(ARTIFACT_ID, serviceName, serviceId);
    }
    assertThrows(exceptionType, testKitBuilder::build);
  }

  @Test
  void createTestKitWithoutServices() {
    try (TestKit testKit = TestKit.builder()
        .build()) {
      // Shouldn't throw
    }
  }

  @Test
  void createEmptyBlock(TestKit testKit) {
    Block block = testKit.createBlock();
    assertThat(block.getNumTransactions()).isEqualTo(0);

    Snapshot view = testKit.getSnapshot();
    Blockchain blockchain = Blockchain.newInstance(view);
    assertThat(blockchain.getHeight()).isEqualTo(1);
    assertThat(block).isEqualTo(blockchain.getBlock(1));
  }

  // TODO: fix this test (allow retrieving service id from runtime)
//  @Test
//  void afterCommitSubmitsTransaction(TestKit testKit) {
//    // Create a block so that afterCommit transaction is submitted
//    Block block = testKit.createBlock();
//    List<TransactionMessage> inPoolTransactions = testKit
//        .findTransactionsInPool(tx -> tx.getServiceId() == SERVICE_ID);
//    assertThat(inPoolTransactions).hasSize(1);
//    TransactionMessage inPoolTransaction = inPoolTransactions.get(0);
//    RawTransaction afterCommitTransaction = constructAfterCommitTransaction(block.getHeight());
//
//    assertThat(inPoolTransaction.getServiceId())
//        .isEqualTo(afterCommitTransaction.getServiceId());
//    assertThat(inPoolTransaction.getTransactionId())
//        .isEqualTo(afterCommitTransaction.getTransactionId());
//    assertThat(inPoolTransaction.getPayload()).isEqualTo(afterCommitTransaction.getPayload());
//
//    Block nextBlock = testKit.createBlock();
//    assertThat(nextBlock.getNumTransactions()).isEqualTo(1);
//    assertThat(nextBlock.getHeight()).isEqualTo(2);
//  }

  @Test
  void createBlockWithTransactionIgnoresInPoolTransactions(TestKit testKit) {
    // Create a block so that afterCommit transaction is submitted
    testKit.createBlock();

    Block block = testKit.createBlockWithTransactions();
    assertThat(block.getNumTransactions()).isEqualTo(0);

    // Two blocks were created, so two afterCommit transactions should be submitted into pool
    List<TransactionMessage> inPoolTransactions = testKit
        .findTransactionsInPool(tx -> tx.getServiceId() == SERVICE_ID);
    assertThat(inPoolTransactions).hasSize(2);
  }

  @Test
  void createBlockWithSingleTransaction(TestKit testKit) {
    TransactionMessage message = constructTestTransactionMessage("Test message");
    Block block = testKit.createBlockWithTransactions(message);
    assertThat(block.getNumTransactions()).isEqualTo(1);

    Snapshot view = testKit.getSnapshot();
    Blockchain blockchain = Blockchain.newInstance(view);
    assertThat(blockchain.getHeight()).isEqualTo(1);
    assertThat(block).isEqualTo(blockchain.getBlock(1));
    Map<HashCode, TransactionResult> transactionResults = toMap(blockchain.getTxResults());
    assertThat(transactionResults).hasSize(1);
    TransactionResult transactionResult = transactionResults.get(message.hash());
    assertThat(transactionResult).isEqualTo(TransactionResult.successful());
  }

  @Test
  void createBlockWithTransactions(TestKit testKit) {
    TransactionMessage message = constructTestTransactionMessage("Test message");
    TransactionMessage message2 = constructTestTransactionMessage("Test message 2");

    Block block = testKit.createBlockWithTransactions(ImmutableList.of(message, message2));
    assertThat(block.getNumTransactions()).isEqualTo(2);

    testKit.withSnapshot((view) -> checkTransactionsCommittedSuccessfully(
        view, block, message, message2));
  }

  @Test
  void nodeSubmittedTransactionsArePlacedInPool(TestKit testKit) {
    TestService service = testKit.getService(SERVICE_NAME, TestService.class);

    TransactionMessage message = constructTestTransactionMessage("Test message", testKit);
    RawTransaction rawTransaction = RawTransaction.fromMessage(message);
    service.getNode().submitTransaction(rawTransaction);

    List<TransactionMessage> transactionsInPool =
        testKit.findTransactionsInPool(tx -> tx.getServiceId() == SERVICE_ID);
    assertThat(transactionsInPool).isEqualTo(ImmutableList.of(message));
  }

  @Test
  void getTransactionPool(TestKit testKit) {
    TestService service = testKit.getService(SERVICE_NAME, TestService.class);

    TransactionMessage message = constructTestTransactionMessage("Test message", testKit);
    RawTransaction rawTransaction = RawTransaction.fromMessage(message);
    TransactionMessage message2 = constructTestTransactionMessage("Test message 2", testKit);
    RawTransaction rawTransaction2 = RawTransaction.fromMessage(message2);

    service.getNode().submitTransaction(rawTransaction);
    service.getNode().submitTransaction(rawTransaction2);

    List<TransactionMessage> transactionsInPool = testKit.getTransactionPool();
    assertThat(transactionsInPool).containsExactlyInAnyOrder(message, message2);
  }

  @Test
  void findTransactionsInPool(TestKit testKit) {
    TestService service = testKit.getService(SERVICE_NAME, TestService.class);

    TransactionMessage message = constructTestTransactionMessage("Test message", testKit);
    RawTransaction rawTransaction = RawTransaction.fromMessage(message);
    TransactionMessage message2 = constructTestTransactionMessage("Test message 2", testKit);
    RawTransaction rawTransaction2 = RawTransaction.fromMessage(message2);
    service.getNode().submitTransaction(rawTransaction);
    service.getNode().submitTransaction(rawTransaction2);

    List<TransactionMessage> transactionsInPool =
        testKit.findTransactionsInPool(
            tx -> tx.getPayload().equals(message.getPayload()));
    assertThat(transactionsInPool).containsExactly(message);
  }

  @Test
  void createBlockWithTransactionsVarargs(TestKit testKit) {
    TransactionMessage message = constructTestTransactionMessage("Test message");
    TransactionMessage message2 = constructTestTransactionMessage("Test message 2");

    Block block = testKit.createBlockWithTransactions(message, message2);
    assertThat(block.getNumTransactions()).isEqualTo(2);

    testKit.withSnapshot((view) -> checkTransactionsCommittedSuccessfully(
        view, block, message, message2));
  }

  private TransactionMessage constructTestTransactionMessage(String payload) {
    return constructTestTransactionMessage(payload, KEY_PAIR);
  }

  private TransactionMessage constructTestTransactionMessage(String payload, TestKit testKit) {
    EmulatedNode emulatedNode = testKit.getEmulatedNode();
    KeyPair emulatedNodeKeyPair = emulatedNode.getServiceKeyPair();
    return constructTestTransactionMessage(payload, emulatedNodeKeyPair);
  }

  private TransactionMessage constructTestTransactionMessage(String payload, KeyPair keyPair) {
    return TransactionMessage.builder()
        .serviceId(SERVICE_ID)
        .transactionId(TestTransaction.ID)
        .payload(payload.getBytes(BODY_CHARSET))
        .sign(keyPair, CRYPTO_FUNCTION);
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
  void createBlockWithTransactionWithWrongServiceId(TestKit testKit) {
    short wrongServiceId = SERVICE_ID + 1;
    TransactionMessage message = TransactionMessage.builder()
        .serviceId(wrongServiceId)
        .transactionId(TestTransaction.ID)
        .payload("Test message".getBytes(BODY_CHARSET))
        .sign(KEY_PAIR, CRYPTO_FUNCTION);
    IllegalArgumentException thrownException = assertThrows(IllegalArgumentException.class,
        () -> testKit.createBlockWithTransactions(message));
    assertThat(thrownException.getMessage())
        .contains("No service with id", Integer.toString(wrongServiceId));
  }

  @Test
  void createBlockWithTransactionWithWrongTransactionId(TestKit testKit) {
    short wrongTransactionId = (short) (TestTransaction.ID + 1);
    TransactionMessage message = TransactionMessage.builder()
        .serviceId(SERVICE_ID)
        .transactionId(wrongTransactionId)
        .payload("Test message".getBytes(BODY_CHARSET))
        .sign(KEY_PAIR, CRYPTO_FUNCTION);
    IllegalArgumentException thrownException = assertThrows(IllegalArgumentException.class,
        () -> testKit.createBlockWithTransactions(message));
    assertThat(thrownException.getMessage())
        .contains("failed to convert transaction", SERVICE_NAME,
            Integer.toString(SERVICE_ID), message.toString());
  }

  @Test
  void getValidatorEmulatedNode(TestKit testKit) {
    EmulatedNode node = testKit.getEmulatedNode();
    assertThat(node.getNodeType()).isEqualTo(EmulatedNodeType.VALIDATOR);
    assertThat(node.getValidatorId()).isNotEmpty();
    assertThat(node.getServiceKeyPair()).isNotNull();
  }

  @Test
  void getAuditorEmulatedNode() {
    try (TestKit testKit = TestKit.builder()
        .withNodeType(EmulatedNodeType.AUDITOR)
        .withDeployedArtifact(ARTIFACT_ID, ARTIFACT_FILENAME)
        .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID)
        .build()) {
      EmulatedNode node = testKit.getEmulatedNode();
      assertThat(node.getNodeType()).isEqualTo(EmulatedNodeType.AUDITOR);
      assertThat(node.getValidatorId()).isEmpty();
      assertThat(node.getServiceKeyPair()).isNotNull();
    }
  }

  @Test
  void timeServiceWorksInTestKit() {
    FakeTimeProvider timeProvider = FakeTimeProvider.create(TIME);
    try (TestKit testKit = TestKit.builder()
        .withTimeService(TIME_SERVICE_NAME, TIME_SERVICE_ID, timeProvider)
        .build()) {
      // Commit two blocks for time oracle to prepare consolidated time. Two blocks are needed as
      // after the first block time transactions are generated and after the second one they are
      // processed
      testKit.createBlock();
      testKit.createBlock();
      testKit.withSnapshot((view) -> {
        TimeSchema timeSchema = TimeSchema.newInstance(view);
        Optional<ZonedDateTime> consolidatedTime = timeSchema.getTime().toOptional();
        assertThat(consolidatedTime).contains(TIME);

        // Check that validatorsTimes contains one exactly entry with TestKit emulated node's
        // public key and time provider's time
        checkValidatorsTimes(timeSchema, testKit, TIME);
      });

      // Update time in time provider
      ZonedDateTime newTime = TIME.plusDays(1);
      timeProvider.setTime(newTime);
      // Commit two blocks for time oracle to update consolidated time. Two blocks are needed as
      // after the first block time transactions are generated and after the second one they are
      // processed
      testKit.createBlock();
      testKit.createBlock();
      testKit.withSnapshot((view) -> {
        TimeSchema timeSchema = TimeSchema.newInstance(view);
        Optional<ZonedDateTime> consolidatedTime = timeSchema.getTime().toOptional();
        assertThat(consolidatedTime).contains(newTime);
      });
    }
  }

  @Test
  void createTestKitWithTimeServiceAndTooManyValidators() {
    TimeProvider timeProvider = FakeTimeProvider.create(TIME);
    Class<IllegalArgumentException> exceptionType = IllegalArgumentException.class;
    short invalidValidatorCount = TestKit.MAX_VALIDATOR_COUNT_WITH_ENABLED_TIME_SERVICE + 1;
    TestKit.Builder testKitBuilder = TestKit.builder()
        /*
         REview:
         re .withService(ARTIFACT_ID, SERVICE_NAME, SERVICE_ID,
            Any.getDefaultInstance()):
    There must be an overload for no (= empty) configuration.
         */
        .withTimeService(TIME_SERVICE_NAME, TIME_SERVICE_ID, timeProvider)
        .withValidators(invalidValidatorCount);
    IllegalArgumentException thrownException = assertThrows(exceptionType, testKitBuilder::build);
    String expectedMessage = String.format("Number of validators (%s) should be less than or equal"
        + " to %s when TimeService is enabled.",
        invalidValidatorCount, TestKit.MAX_VALIDATOR_COUNT_WITH_ENABLED_TIME_SERVICE);
    assertThat(thrownException).hasMessageContaining(expectedMessage);
  }

  @Test
  void getSnapshot(TestKit testKit) {
    Class<IllegalStateException> exceptionType = IllegalStateException.class;
    Snapshot view1 = testKit.getSnapshot();
    Snapshot view2 = testKit.getSnapshot();
    Cleaner snapshotCleaner = testKit.snapshotCleaner;
    assertThat(snapshotCleaner.getNumRegisteredActions()).isEqualTo(2);

    testKit.close();

    // Verify that snapshot proxies were closed
    assertThrows(exceptionType, view1::getViewNativeHandle);
    assertThrows(exceptionType, view2::getViewNativeHandle);
  }

  private void checkValidatorsTimes(
      TimeSchema timeSchema, TestKit testKit, ZonedDateTime expectedTime) {
    Map<PublicKey, ZonedDateTime> validatorsTimes = toMap(timeSchema.getValidatorsTimes());
    EmulatedNode emulatedNode = testKit.getEmulatedNode();
    PublicKey nodePublicKey = emulatedNode.getServiceKeyPair().getPublicKey();
    Map<PublicKey, ZonedDateTime> expected = ImmutableMap.of(nodePublicKey, expectedTime);
    assertThat(validatorsTimes).isEqualTo(expected);
  }

  private <K, V> Map<K, V> toMap(MapIndex<K, V> mapIndex) {
    return Maps.toMap(mapIndex.keys(), mapIndex::get);
  }

  public static final class TestServiceModule2 extends AbstractServiceModule {

    private static final TransactionConverter THROWING_TX_CONVERTER = (tx, payload) -> {
      throw new IllegalStateException("No transactions in this service: " + tx);
    };

    @Override
    protected void configure() {
      bind(Service.class).to(TestService2.class).in(Singleton.class);
      bind(TransactionConverter.class).toInstance(THROWING_TX_CONVERTER);
    }
  }

  static final class TestService2 implements Service {

    private Node node;

    Node getNode() {
      return node;
    }

    // Review: null?
    @Override
    public List<HashCode> getStateHashes(Snapshot snapshot) {
      // TODO: refactor TestService2
      return null;
    }

    @Override
    public void createPublicApiHandlers(Node node, Router router) {
      this.node = node;
    }
  }
}
