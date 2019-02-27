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

package com.exonum.client.response;

import java.time.ZonedDateTime;
import java.util.List;
import lombok.Value;

@Value
public class BlocksResponse {
  /**
   * Blockchain blocks in descending order by height.
   * It is allowed to be empty if no blocks found.
   */
  List<Block> blocks;

  /**
   * Blockchain block commit times. It is allowed to be empty.
   * The index of time elements corresponds to the index of blocks.
   */
  List<ZonedDateTime> blockCommitTimes;

  /**
   * The smallest height of the returned blocks.
   */
  long blocksRangeStart;

  /**
   * The largest height of the returned blocks.
   * if blocks have gaps then: {@code blocksRangeEnd - blocksRangeStart != blocks.size}
   */
  long blocksRangeEnd;
}
