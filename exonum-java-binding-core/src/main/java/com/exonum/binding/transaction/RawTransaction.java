package com.exonum.binding.transaction;

import com.google.auto.value.AutoValue;

/**
 * An Exonum raw transaction. It is mainly used for interaction with the Exonum core
 * as well as for transferring transactions between nodes within the network.
 */
@AutoValue
public abstract class RawTransaction {

  /* Review: Returns the identifier of the service this transaction belongs to. @see Service#getId */
  /**
   * Returns a service identifier which the transaction belongs to.
   */
  public abstract short getServiceId();

  /* Review: Returns the type of this transaction within a service. */
  /**
   * Returns the type of this transaction within a service.
   */
  public abstract short getTransactionId();

  /* Review: What is payload? */
  /**
   * Returns the transaction payload which contains actual transaction data.
   */
  public abstract byte[] getPayload();

  /**
   * Returns the new builder for the transaction.
   */
  public static RawTransaction.Builder newBuilder() {
    return new AutoValue_RawTransaction.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    /* Review: identifier of the service this transaction belongs to. */
    /**
     * Sets service identifier to the transaction.
     */
    public abstract Builder serviceId(short serviceId);

    /* Review: identifier of the transaction within a service. */
    /**
     * Sets transaction identifier to the transaction.
     */
    public abstract Builder transactionId(short transactionId);

    /* Review: the payload *of* … */
    /**
     * Sets payload to the transaction.
     */
    public abstract Builder payload(byte[] payload);

    public abstract RawTransaction build();
  }

}
