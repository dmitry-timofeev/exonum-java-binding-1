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

package com.exonum.binding.core.runtime;

import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.core.service.BlockCommittedEvent;
import com.exonum.binding.core.service.Configuration;
import com.exonum.binding.core.service.Node;
import com.exonum.binding.core.service.Service;
import com.exonum.binding.core.service.TransactionConverter;
import com.exonum.binding.core.storage.database.Fork;
import com.exonum.binding.core.storage.database.Snapshot;
import com.exonum.binding.core.transaction.Transaction;
import com.exonum.binding.core.transaction.TransactionContext;
import com.exonum.binding.core.transaction.TransactionExecutionException;
import com.google.common.io.BaseEncoding;
import com.google.common.net.UrlEscapers;
import com.google.inject.Inject;
import io.vertx.ext.web.Router;
import java.util.List;

/**
 * The service wrapper represents an Exonum service as a whole and allows the service runtime
 * to operate on them conveniently. It separates the <em>extension</em>,
 * user-facing, interface from the <em>runtime</em>, internal, interface.
 */
final class ServiceWrapper {

  private final Service service;
  private final TransactionConverter txConverter;
  private final ServiceInstanceSpec instanceSpec;

  @Inject
  ServiceWrapper(Service service, TransactionConverter txConverter,
      ServiceInstanceSpec instanceSpec) {
    this.service = service;
    this.txConverter = txConverter;
    this.instanceSpec = instanceSpec;
  }

  /*
  Review: (to self) Why are these methods public? Why ServiceWrapper is public?
I wonder if we shall make ServiceWrapper public or keep all the operations with it in the runtime.
As stated in the docs, the goals of this class are to represent a service implementation as a whole
and separate the user-facing extension interface from the framework interface.

Testkit uses it for three things:
1. Provide Service instance to the client (#getService). Although it appears to violate
the separation between extension/framework interface, this one is a necessary evil to be
able to pass the service instance (implementing the extension, user-facing interface)
to the end user.
2. To check if a service instance with the given id and name started (#getId and #getName).
Such checks might be implemented by using the schema of the dispatcher, which, as an added
benefit, keeps track of all services, not only Java, enabling similar checks of time service.
Also, as currently testkit tests get the wrapper by the service name, later assertions on the name
don't make much sense.
3. Check transaction conversion (#getTxConverter).
I don't think the TransactionConverter, as the extension interface, shall be used by testkit,
as that breaks the separation that this class provides. If testkit needs to check if a transaction
message can be converted, it shall use either the runtime or the ServiceWrapper method to do that.

So, does testkit really need to operate on individual services (just as the runtime), so (1)and (3)
can be provided via public ServiceWrapper, or shall we have ServiceRuntime, providing (1) and (3)
to the testkit via package-private ServiceWrapper?
   */
  /**
   * Returns the service instance.
   */
  Service getService() {
    return service;
  }

  /**
   * Returns the transaction converter of this service.
   */
  TransactionConverter getTxConverter() {
    return txConverter;
  }

  /**
   * Returns the name of this service instance.
   */
  String getName() {
    return instanceSpec.getName();
  }

  /**
   * Returns id of this service instance.
   */
  int getId() {
    return instanceSpec.getId();
  }

  void initialize(Fork view, Configuration configuration) {
    service.initialize(view, configuration);
  }

  void executeTransaction(int txId, byte[] arguments, TransactionContext context)
      throws TransactionExecutionException {
    // Decode the transaction data into an executable transaction
    Transaction transaction = txConverter.toTransaction(txId, arguments);
    if (transaction == null) {
      // Use \n in the format string to ensure the message (which is likely recorded
      // to the blockchain) stays the same on any platform
      throw new NullPointerException(String.format("Invalid service implementation: "
          + "TransactionConverter#toTransaction must never return null.\n"
          + "Throw an exception if your service does not recognize this message id (%s) "
          + "or arguments (%s)", txId, BaseEncoding.base16().encode(arguments)));
    }
    // Execute it
    transaction.execute(context);
  }

  List<HashCode> getStateHashes(Snapshot snapshot) {
    return service.getStateHashes(snapshot);
  }

  void beforeCommit(Fork fork) {
    service.beforeCommit(fork);
  }

  void afterCommit(BlockCommittedEvent event) {
    service.afterCommit(event);
  }

  void createPublicApiHandlers(Node node, Router router) {
    service.createPublicApiHandlers(node, router);
  }

  /**
   * Returns the relative path fragment on which to mount the API of this service.
   * The path fragment is already escaped and can be combined with other URL path fragments.
   */
  String getPublicApiRelativePath() {
    // At the moment, we treat the service name as a single path segment (i.e., our path
    // fragment consists of a single segment — all slashes will be escaped).
    // todo: [ECR-3448] make this user-configurable? If so, is it one of predefined keys
    //  in the normal service configuration, or a separate configuration?
    return UrlEscapers.urlPathSegmentEscaper()
        .escape(getName());
  }
}
