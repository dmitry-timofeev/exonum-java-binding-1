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

import com.exonum.binding.core.service.AbstractServiceModule;
import com.exonum.binding.core.service.Service;
import com.exonum.binding.core.service.TransactionConverter;
import com.google.inject.Singleton;

public final class TestServiceModule2 extends AbstractServiceModule {

  @Override
  protected void configure() {
    bind(Service.class).to(TestService2.class).in(Singleton.class);
    bind(TransactionConverter.class).toInstance(TestTransaction::from);
  }
}
