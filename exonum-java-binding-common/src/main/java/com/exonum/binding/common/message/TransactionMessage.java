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
 *
 */

package com.exonum.binding.common.message;

import static com.google.common.base.Preconditions.checkArgument;

import com.exonum.binding.common.crypto.CryptoFunction;
import com.exonum.binding.common.crypto.CryptoFunctions;
import com.exonum.binding.common.crypto.KeyPair;
import com.exonum.binding.common.crypto.PublicKey;
import com.exonum.binding.common.hash.HashCode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An Exonum transaction message.
 */
public interface TransactionMessage {
  int AUTHOR_PUBLIC_KEY_OFFSET = 0;
  int CLS_OFFSET = 32;
  int TAG_OFFSET = 33;
  int SERVICE_ID_OFFSET = 34;
  int TRANSACTION_ID_OFFSET = 36;
  int PAYLOAD_OFFSET = 38;

  int AUTHOR_PUBLIC_KEY_SIZE = 32;
  int SIGNATURE_SIZE = 64;
  int MIN_MESSAGE_SIZE = PAYLOAD_OFFSET + SIGNATURE_SIZE;

  /*
Review: Here and elsewhere, I'd suggest to not capitalize the individual words that comprise
the interface name, because they refer not to the interface, but to the thing it represents.
I.e., "Returns a public key of the author of the transaction message".

Also, if I remember correctly, `’s` to specify possession is used with people or organizations.
   */
  /**
   * Returns a public key of the author of the transaction message.
   */
  PublicKey getAuthor();

  /*
Review:
```
   * Returns the identifier of the service this message belongs to,
   * or zero if this message is an internal Exonum message.
```
   */
  /**
   * Returns the identifier of the service this message belongs to.
   */
  short getServiceId();

  /*
Review: What is transaction id? How is that useful to anyone? Please give these javadocs some love.
   */
  /**
   * Returns transaction identifier which is unique within the service.
   */
  short getTransactionId();

  /*
Review: What is payload?
   */
  /**
   * Returns the transaction message body.
   */
  byte[] getPayload();

/*
Review: A hash of what exactly?
 */
  /**
   * Returns the transaction message hash.
   */
  HashCode hash();

/*
Review: A signature of what? Is the format specified?
 */
  /**
   * Returns the <a href="https://ed25519.cr.yp.to/">Ed25519</a> signature
   * over this binary message.
   *
   * <p>The signature is <strong>not</strong> guaranteed to be valid and must be verified against
   * the signer’s public key.
   *
   * @see CryptoFunctions#ed25519()
   */
  byte[] getSignature();

  /**
   * Returns the transaction message in binary format.
   */
  byte[] toBytes();

  /*
  Review: Creates a new builder of the transaction message?
   */
  /**
   * Returns the transaction message size in bytes.
   */
  int size();

  /**
   * Create a new builder for the transaction message.
   */
  static Builder builder() {
    return new Builder();
  }

  /*
Review: I wonder if we shall validate the format somehow on instantiation (in BTM) :thinking:
   */
  /**
   * Creates the transaction message from the given bytes array.
   */
  static TransactionMessage fromBytes(byte[] bytes) {
    return new BinaryTransactionMessage(bytes);
  }

  /**
   * Creates the transaction message from the given bytes buffer.
   */
  static TransactionMessage fromBuffer(ByteBuffer buffer) {
    return new BinaryTransactionMessage(buffer.array());
  }

  /**
   * Builder for the binary transaction message.
   */
  class Builder {
    private Short serviceId;
    private Short transactionId;
    private ByteBuffer payload;

    /**
     * Sets service identifier to the transaction message.
     */
    public Builder serviceId(short serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    /**
     * Sets transaction identifier to the transaction message.
     */
    public Builder transactionId(short transactionId) {
      this.transactionId = transactionId;
      return this;
    }

    /**
     * Sets payload to the transaction message.
     */
    public Builder payload(byte[] payload) {
      return payload(ByteBuffer.wrap(payload));
    }

    /**
     * Sets payload to the transaction message.
     */
    public Builder payload(ByteBuffer payload) {
      this.payload = payload.duplicate().order(ByteOrder.LITTLE_ENDIAN);
      return this;
    }

/*
Review: Doc: Where do keys end up? Will it eat my secret key?
 */
    /**
     * Signs the message, creating a new signed binary transaction message.
     *
     * @param keys key pair with private and public keys. Public key is used as an author key of the
     *        message and private key is used for signing the message.
     * @param crypto a cryptographic function to use
     * @return a new signed binary transaction message
     * @throws NullPointerException if serviceId or transactionId or payload weren't set
     * @throws IllegalArgumentException if public key has wrong size
     */
    public TransactionMessage sign(KeyPair keys, CryptoFunction crypto) {
      /*
Review: Something like `checkRequiredFieldSet(field: Object, fieldName: String)` so that clients
do not get plain NPEs, but, say, IllegalStateException with message that explains things?
       */
      checkRequiredFieldsSet();
      PublicKey authorPublicKey = keys.getPublicKey();
      checkArgument(authorPublicKey.size() == AUTHOR_PUBLIC_KEY_SIZE);

      ByteBuffer buffer = ByteBuffer
          .allocate(MIN_MESSAGE_SIZE + payload.limit())
          .order(ByteOrder.LITTLE_ENDIAN);
      buffer.put(authorPublicKey.toBytes());
      buffer.put(MessageType.TRANSACTION.bytes());
      buffer.putShort(serviceId);
      buffer.putShort(transactionId);
      buffer.put(payload);

      buffer.position(0);
      byte[] unsignedMessage = new byte[PAYLOAD_OFFSET + payload.limit()];
      buffer.get(unsignedMessage);
      byte[] signature = crypto.signMessage(unsignedMessage, keys.getPrivateKey());
      buffer.put(signature);

      return new BinaryTransactionMessage(buffer.array());
    }

    private void checkRequiredFieldsSet() {
      String undefinedFields = "";
      undefinedFields = serviceId == null ? undefinedFields + " serviceId" : undefinedFields;
      undefinedFields =
          transactionId == null ? undefinedFields + " transactionId" : undefinedFields;
      undefinedFields = payload == null ? undefinedFields + " payload" : undefinedFields;
      if (!undefinedFields.isEmpty()) {
        throw new IllegalStateException(
            "Following field(s) are required but weren't set: " + undefinedFields);
      }
    }

    private Builder() {
    }
  }

}
