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

package com.exonum.binding.common.proofs;

import static com.exonum.binding.common.proofs.DbKeyCompressedFunnel.getWholeBytesKeyLength;
import static com.exonum.binding.common.proofs.map.DbKeyTestUtils.keyFromString;
import static org.mockito.Mockito.anyByte;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.exonum.binding.common.hash.PrimitiveSink;
import com.exonum.binding.common.proofs.map.DbKey;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DbKeyCompressedFunnelTest {

  @ParameterizedTest
  @MethodSource("testSource")
  void funnelTest(DbKey dbKey, byte[] encodedSignificantBitsNum) {
    PrimitiveSink primitiveSink = mock(PrimitiveSink.class);
    DbKeyCompressedFunnel.dbKeyCompressedFunnel().funnel(dbKey, primitiveSink);
    /*
    Review: Why getWholeBytesKeyLength is still used in the test to get the *expected* value
     or not tested separately?
     */
    int wholeBytesKeyLength = getWholeBytesKeyLength(dbKey.getNumSignificantBits());
    byte[] key = dbKey.getKeySlice();

    /*
    Review: Please use InOrder. Usual verifications do not verify the order of operations.
    times is not needed then.
     */
    verify(primitiveSink, times(encodedSignificantBitsNum.length)).putByte(anyByte());
    for (byte encodedByte : encodedSignificantBitsNum) {
      verify(primitiveSink).putByte(encodedByte);
    }
    verify(primitiveSink).putBytes(key, 0, wholeBytesKeyLength);
  }

  private static Stream<Arguments> testSource() {
    return Stream.of(
        Arguments.of(DbKey.newBranchKey(keyFromString(""), 0), new byte[]{0}),
        Arguments.of(DbKey.newBranchKey(keyFromString("1"), 1), new byte[]{1}),
        Arguments.of(DbKey.newBranchKey(keyFromString("111"), 7), new byte[]{7}),
        Arguments.of(DbKey.newBranchKey(keyFromString("0001"), 8), new byte[]{8}),
        Arguments.of(DbKey.newBranchKey(keyFromString("1111"), 127), new byte[]{127}),
        /*
        Review: As it is a test of LEB128, which is a binary encoding, why doesn't it make it easier
         to verify the correctness of test data by using binary:
          * `128:  0b1_0000000 -> 1_0000000, 0_0000001`;
          * `256: 0b10_0000000 -> 1_0000000, 0_0000010`

         */
        Arguments.of(DbKey.newBranchKey(keyFromString("1001 1001"), 128), new byte[]{
            (byte) 0b1_0000000, 0b0_0000001}),
        Arguments.of(DbKey.newLeafKey(keyFromString("1111 1111")), new byte[]{(byte) 0b1_0000000, 0b0_0000010}));
  }
}
