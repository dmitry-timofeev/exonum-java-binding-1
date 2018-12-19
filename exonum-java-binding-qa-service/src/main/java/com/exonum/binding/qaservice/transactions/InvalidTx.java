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

package com.exonum.binding.qaservice.transactions;

import static com.exonum.binding.qaservice.transactions.TransactionPreconditions.checkPayloadSize;
import static com.exonum.binding.qaservice.transactions.TransactionPreconditions.checkTransaction;

import com.exonum.binding.qaservice.QaService;
import com.exonum.binding.transaction.RawTransaction;
import com.exonum.binding.transaction.Transaction;
import com.exonum.binding.transaction.TransactionContext;

/**
 * An invalid transaction always returning false.
 */
/*
Review: With no `#isValid` such transaction no longer makes sense.
 */
public final class InvalidTx implements Transaction {

  private static final short ID = QaTransaction.INVALID.id();

  @Override
  public void execute(TransactionContext context) {
    throw new AssertionError("Must never be executed by the framework: " + this);
  }

  public static TransactionMessageConverter<InvalidTx> converter() {
    return TransactionConverter.INSTANCE;
  }

  private enum TransactionConverter implements TransactionMessageConverter<InvalidTx> {
    INSTANCE;

    static final int BODY_SIZE = 0;

    @Override
    public InvalidTx fromRawTransaction(RawTransaction rawTransaction) {
      checkMessage(rawTransaction);
      return new InvalidTx();
    }

    @Override
    public RawTransaction toRawTransaction(InvalidTx transaction) {
      return RawTransaction.newBuilder()
          .serviceId(QaService.ID)
          .transactionId(ID)
          .payload(new byte[]{})
          .build();
    }

    private void checkMessage(RawTransaction rawTransaction) {
      checkTransaction(rawTransaction, ID);
      checkPayloadSize(rawTransaction, BODY_SIZE);
    }
  }
}
