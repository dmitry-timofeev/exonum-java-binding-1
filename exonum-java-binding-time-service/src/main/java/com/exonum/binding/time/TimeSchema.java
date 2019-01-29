/*
 * Copyright 2019 The Exonum Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exonum.binding.time;

import com.exonum.binding.common.crypto.PublicKey;
import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.service.Schema;
import com.exonum.binding.storage.indices.EntryIndexProxy;
import com.exonum.binding.storage.indices.ProofMapIndexProxy;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Exonum time service database schema.
 */
// Review: Why does it extend the Schema?
public interface TimeSchema extends Schema {

  /**
   * Returns stored time.
   */
  EntryIndexProxy<ZonedDateTime> getTime();

  /**
   * Returns the table that stores time for every validator.
   */
  ProofMapIndexProxy<PublicKey, ZonedDateTime> getValidatorsTimes();

  @Override
  List<HashCode> getStateHashes();
}
