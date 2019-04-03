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
import static com.google.common.base.Preconditions.checkNotNull;

import com.exonum.binding.blockchain.Block;
import com.exonum.binding.proxy.NativeHandle;
import com.exonum.binding.service.AbstractServiceModule;
import com.exonum.binding.service.BlockCommittedEvent;
import com.exonum.binding.service.Service;
import com.exonum.binding.service.ServiceModule;
import com.exonum.binding.service.adapters.UserServiceAdapter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import javax.annotation.Nullable;

/**
 * TestKit for testing blockchain services. It offers simple network configuration emulation
 * (with no real network setup). Although it is possible to add several validator nodes to this
 * Review:
 * ... only one node will create the service instances and will execute their operations
 * network, only one node (either validator or auditor) would be truly emulated and execute its
 * {@link Service#afterCommit(BlockCommittedEvent)} method logic.
 *
 * @see <a href="https://exonum.com/doc/version/0.10/get-started/test-service/">TestKit documentation</a>
 */
public final class TestKit {

  @VisibleForTesting
  final static short MAX_SERVICE_NUMBER = 256;
  /*
  Review: Shall we really create a static instance of the testkit injector (and have all testkit instances,
  potentially, working in parallel, reuse the same bindings)?
   */
  private final static Injector frameworkInjector = Guice.createInjector(new TestKitFrameworkModule());

  private final NativeHandle nativeHandle;
  private final Map<Short, Service> services = new HashMap<>();

  private TestKit(List<Class<? extends ServiceModule>> serviceModules, EmulatedNodeType nodeType,
                  short validatorCount, @Nullable TimeProvider timeProvider) {
    List<UserServiceAdapter> serviceAdapters = new ArrayList<>();
    for (Class<? extends ServiceModule> moduleClass : serviceModules) {
      UserServiceAdapter serviceAdapter = createUserModule(moduleClass);
      serviceAdapters.add(serviceAdapter);
      services.put(serviceAdapter.getId(), serviceAdapter.getService());
    }
    boolean isAuditorNode = nodeType == EmulatedNodeType.AUDITOR;
    /*
    Review: serviceAdapters.toArray(new UserServiceAdapter[0]);
     */
    UserServiceAdapter[] userServiceAdapters = serviceAdapters.toArray(new UserServiceAdapter[0]);
    // TODO: fix after native implementation
    nativeHandle = null;
//    nativeHandle = new NativeHandle(
//        nativeCreateTestKit(userServiceAdapters, isAuditorNode,
//            validatorCount, timeProvider));
  }

  /**
   * Creates a TestKit network with a single validator node for a single service.
   */
  public static TestKit forService(Class<? extends ServiceModule> serviceModule) {
    return new TestKit(Collections.singletonList(serviceModule), EmulatedNodeType.VALIDATOR,
        (short) 0, null);
  }

  // Review: documentation?
  @SuppressWarnings("unchecked")
  public <T extends Service> T getService(short serviceId, Class<T> serviceClass) {
    Service service = services.get(serviceId);
    // Review: I'd also add the actual class, something like "Service (id=%s, class=%s) cannot be cast to %s"
    checkArgument(service.getClass().equals(serviceClass), "Service with id %s is not of expected class %s",
        serviceId, serviceClass.getCanonicalName());
    // Review: return serviceClass.cast(service); — and no unchecked operations.
    return (T) services.get(serviceId);
  }

  /**
   * Creates a service from the service module.
   *
   * @param moduleClass a class of the user service module
   */
  // Review: (to self) Isn't there something in multiple-services that does that (and better)?
  private static UserServiceAdapter createUserModule(Class<? extends ServiceModule> moduleClass) {
    try {
      Constructor constructor = moduleClass.getDeclaredConstructor();
      Object moduleObject = constructor.newInstance();
      checkArgument(moduleObject instanceof Module, "%s is not a sub-class of %s",
          moduleClass, Module.class.getCanonicalName());
      Injector serviceInjector = frameworkInjector.createChildInjector((Module) moduleObject);
      return serviceInjector.getInstance(UserServiceAdapter.class);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Cannot access the no-arg module constructor", e);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("No no-arg constructor", e);
    } catch (InstantiationException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private native long nativeCreateTestKit(UserServiceAdapter[] services, boolean auditor,
      short withValidatorCount, TimeProvider timeProvider);

  private native long nativeCreateSnapshot(long nativeHandle);

  private native Block nativeCreateBlock(long nativeHandle);

  private native Block nativeCreateBlockWithTransactions(long nativeHandle, byte[][] transactions);

  private native EmulatedNode nativeGetEmulatedNode(long nativeHandle);

  /**
   * Creates a new builder for the TestKit.
   *
   * @param nodeType type of the main TestKit node - either validator or auditor. Note that
   * {@link Service#afterCommit(BlockCommittedEvent)} logic will only be called on the main TestKit node of this
   * type
   */
  public static Builder builder(EmulatedNodeType nodeType) {
    checkNotNull(nodeType);
    return new Builder(nodeType);
  }

  /**
   * Builder for the TestKit.
   */
  public static final class Builder {

    private EmulatedNodeType nodeType;
    private short validatorCount;
    private List<Class<? extends ServiceModule>> services = new ArrayList<>();
    private TimeProvider timeProvider;

    private Builder(EmulatedNodeType nodeType) {
      // Review: shan't we set the default validatorCount to, say, 1 (as >1 is unneeded in most scenarios)?
      this.nodeType = nodeType;
    }

    /**
     * Sets number of additional validator nodes in the TestKit network. Note that
     * Review: ... regardless of the configured number of validators, only a single service will be instantiated.
     * {@link Service#afterCommit(BlockCommittedEvent)} logic will only be called on the main TestKit node.
     */
    public Builder withValidators(short validatorCount) {
      this.validatorCount = validatorCount;
      return this;
    }

    /**
     * Adds a service with which the TestKit would be instantiated. Several services can be added.
     */
    public Builder withService(Class<? extends ServiceModule> serviceModule) {
      services.add(serviceModule);
      return this;
    }

    /**
     * Adds a list of services with which the TestKit would be instantiated. Several services can
     * be added.
     */
    /* Review: Iterable? Also, a vararg seems to be more useful in our case because it is not expected
that more than a handful of services will commonly be added. Surely, we can add methods with both signatures.
     */
    public Builder withServices(List<Class<? extends ServiceModule>> serviceModules) {
      services.addAll(serviceModules);
      return this;
    }

    /* Review:
If called, will create a TestKit with time service enabled. The time service will use the given TimeProvider
as a time source.
     */
    /**
     * If called, will create a TestKit with time service with given TimeProvider.
     */
    public Builder withTimeService(TimeProvider timeProvider) {
      this.timeProvider = timeProvider;
      return this;
    }

    /**
     * Creates the TestKit instance.
     * Review: as it not just creates a test network, but involves a lot of other (potentially failing) things,
     * I'd document precisely what happens (i.e., service instances are created, services are initialized (link to
     * #initialize), public API handlers are created (link to #mountPublicApiHandlers).
     */
    public TestKit build() {
      checkMaxServiceNumber(services);
      return new TestKit(services, nodeType, validatorCount, timeProvider);
    }

    private void checkMaxServiceNumber(List<Class<? extends ServiceModule>> serviceModules) {
      // Review: ... but x added.
      // Review: Is there anything else that must be validated? E.g., non-empty list of services?
      checkArgument(serviceModules.size() < MAX_SERVICE_NUMBER,
          "There shouldn't be more than %s services in the TestKit", MAX_SERVICE_NUMBER);
    }
  }
}
