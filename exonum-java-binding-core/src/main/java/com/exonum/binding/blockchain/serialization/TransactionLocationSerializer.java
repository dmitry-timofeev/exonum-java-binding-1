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

package com.exonum.binding.blockchain.serialization;

import com.exonum.binding.blockchain.TransactionLocation;
import com.exonum.binding.common.serialization.Serializer;
import com.google.protobuf.InvalidProtocolBufferException;

/*
Review: Same here — shan't it be a package-private class in ejb-core, near its usage in CoreSchema?
 */
public enum TransactionLocationSerializer implements Serializer<TransactionLocation> {
  INSTANCE;

  @Override
  public byte[] toBytes(TransactionLocation value) {
    CoreProtos.TxLocation txLocation =
        CoreProtos.TxLocation.newBuilder()
            .setBlockHeight(value.getHeight())
            .setPositionInBlock(value.getIndexInBlock())
            .build();
    return txLocation.toByteArray();
  }

  @Override
  public TransactionLocation fromBytes(byte[] binaryTransactionLocation) {
    /*
    Review:
Would it be easier to use `com.exonum.binding.common.serialization.StandardSerializers.protobuf`,
which will hide the exception handling (same in other serializers):
```java
var txLocationProto = locationProtobufSerializer.fromBytes(bytes);
return TransactionLocation.valueOf(txLocationProto.getHeight(), txLocationProto.getIndexInBlock());
```
     */
    try {
      CoreProtos.TxLocation copiedtxLocationProtos =
          CoreProtos.TxLocation.parseFrom(binaryTransactionLocation);
      return TransactionLocation.valueOf(copiedtxLocationProtos.getBlockHeight(),
          copiedtxLocationProtos.getPositionInBlock());
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException("Unable to instantiate "
          + "CoreProtos.TxLocation instance from provided binary data", e);
    }
  }

}