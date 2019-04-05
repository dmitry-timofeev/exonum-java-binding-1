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

import java.time.ZonedDateTime;

// TODO: update Javadocs in P2 [ECR-3051]
public class FakeTimeProvider implements TimeProvider {

  public FakeTimeProvider create(ZonedDateTime time) {
    throw new UnsupportedOperationException();
  }

  public void setTime(ZonedDateTime time) {
    throw new UnsupportedOperationException();
  }

  public void addTime(ZonedDateTime time) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ZonedDateTime getTime() {
    throw new UnsupportedOperationException();
  }
}
