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

/**
 * Time provider for service testing. Used as a time source by TestKit time service.
 */
public interface TimeProvider {

  /**
   * Returns the current time of this time provider in UTC time zone.
   */
  ZonedDateTime getTime();

  /*
  Review:
  I do not understand how it is supposed to work. It is apparently required when testkit is
`static` **and** it has a time provider set. In this case the time provider needs to be configured
in a static context (= before `@BeforeAll`!). Some issues with the current design:
1. Does not make sense with FakeTimeProvider unless it is constant (= never changes the current time),
because the client cannot access the actual time provider used by the testkit. We can probably
make testkit return it, but I think it will only complicate things (pass one provider to the
builder, but get another one from the teskit). On the other hand, if we do not copy (in a `static` context),
and the client puts it in a `static` field and then reconfigures in tests —
they will end up with broken initial state in case of single-threaded execution of tests or concurrency
issues (and anything broken) in case of multi-threaded execution.
2. Cannot use mocks even without the extension because they cannot be cloned.

Unless I'm missing something, copying seems restrictive and not that easy to use or fix.
But it is required only for `static` extensions. What could we do?
1. 'Fix' by adding `Testkit#getTimeProvider` and some obscure documentation.
2. Ignore the concurrency issue (i.e., `static` extension with a shared template builder
and a shared time provider will use the same instance in all tests). If a client breaks
it by modifying the provider in any test, it is their problem.
3. Forbid `static` extensions — we seem to have found a good reason :-)

   */
}
