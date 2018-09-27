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
 */

package com.exonum.binding.common.proofs.list;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.serialization.StandardSerializers;
import org.junit.jupiter.api.Test;

class ListProofStructureValidatorTest {

  private static final String V1 = "v1";
  private static final String V2 = "v2";
  private static final String V3 = "v3";
  private static final String V4 = "v4";

  private static final HashCode H1 = HashCode.fromString("a1");
  private static final HashCode H2 = HashCode.fromString("a2");
  private static final HashCode H3 = HashCode.fromString("a3");

  private ListProofStructureValidator validator;

  @Test
  void visit_SingletonListProof() {
    ListProof root = leafOf(V1);

    validator = createListProofStructureValidator();
    root.accept(validator);

    assertThat(validator.check(), is(ListProofStatus.VALID));
  }

  @Test
  void visit_FullProof2elements() {
    ListProofElement left = leafOf(V1);
    ListProofElement right = leafOf(V2);
    ListProofBranch root = new ListProofBranch(left, right);

    validator = createListProofStructureValidator();
    validator.visit(root);

    assertThat(validator.check(), is(ListProofStatus.VALID));
  }

  @Test
  void visit_FullProof4elements() {
    ListProofBranch root = new ListProofBranch(
        new ListProofBranch(
            leafOf(V1),
            leafOf(V2)
        ),
        new ListProofBranch(
            leafOf(V3),
            leafOf(V4)
        )
    );

    validator = createListProofStructureValidator();
    validator.visit(root);

    assertThat(validator.check(), is(ListProofStatus.VALID));
  }

  @Test
  void visit_IllegalProofOfSingletonTree() {
    ListProofElement left = leafOf(V1);

    // A proof for a list of size 1 must not contain branch nodes.
    ListProofBranch root = new ListProofBranch(left, null);

    validator = createListProofStructureValidator();
    validator.visit(root);

    assertThat(validator.check(), is(ListProofStatus.VALID));
  }

  @Test
  void visit_ProofLeftValue() {
    ListProof left = leafOf(V1);
    ListProof right = new ListProofHashNode(H2);
    ListProofBranch root = new ListProofBranch(left, right);

    validator = createListProofStructureValidator();
    validator.visit(root);

    assertThat(validator.check(), is(ListProofStatus.VALID));
  }

  @Test
  void visit_ProofRightValue() {
    ListProof left = new ListProofHashNode(H1);
    ListProof right = leafOf(V2);
    ListProofBranch root = new ListProofBranch(left, right);

    validator = createListProofStructureValidator();
    validator.visit(root);

    assertThat(validator.check(), is(ListProofStatus.VALID));
  }

  @Test
  void visit_InvalidTreeHasNoElements() {
    ListProof left = new ListProofHashNode(H1);
    ListProofBranch root = new ListProofBranch(left, null);

    validator = createListProofStructureValidator();
    validator.visit(root);

    assertThat(validator.check(), is(ListProofStatus.INVALID_TREE_NO_ELEMENTS));
  }

  @Test
  void visit_UnbalancedInTheRightSubTree() {
    ListProofBranch root = new ListProofBranch(
        new ListProofBranch(leafOf(V1),
            new ListProofHashNode(H2)),
        leafOf(V3) // <-- A value at the wrong depth.
    );

    validator = createListProofStructureValidator();
    validator.visit(root);

    assertThat(validator.check(), is(ListProofStatus.INVALID_NODE_DEPTH));
  }

  @Test
  void visit_UnbalancedInTheLeftSubTree() {
    ListProofBranch root = new ListProofBranch(
        leafOf(V1), // <-- A value at the wrong depth.
        new ListProofBranch(leafOf(V2),
            new ListProofHashNode(H3))
    );

    validator = createListProofStructureValidator();
    validator.visit(root);

    assertThat(validator.check(), is(ListProofStatus.INVALID_NODE_DEPTH));
  }

  @Test
  void visitUnbalancedElementNodeTooDeep() {
    int depth = ListProofStructureValidator.MAX_NODE_DEPTH + 1;
    ListProof root = generateLeftLeaningProofTree(depth, leafOf(V1));

    validator = createListProofStructureValidator();
    root.accept(validator);

    assertThat(validator.check(), is(ListProofStatus.INVALID_ELEMENT_NODE_DEPTH));
  }

  @Test
  void visitUnbalancedHashNodeTooDeep() {
    int depth = ListProofStructureValidator.MAX_NODE_DEPTH + 1;
    ListProof root = generateLeftLeaningProofTree(depth, new ListProofHashNode(H2));

    validator = createListProofStructureValidator();
    root.accept(validator);

    assertThat(validator.check(), is(ListProofStatus.INVALID_HASH_NODE_DEPTH));
  }

  private ListProofStructureValidator createListProofStructureValidator() {
    return new ListProofStructureValidator();
  }

  private static ListProofElement leafOf(String element) {
    byte[] dbElement = bytesOf(element);
    return new ListProofElement(dbElement);
  }

  private static byte[] bytesOf(String element) {
    return StandardSerializers.string().toBytes(element);
  }

  private ListProof generateLeftLeaningProofTree(int depth, ListProof leafNode) {
    ListProof root = null;
    ListProof left = leafNode;
    int d = depth;
    while (d != 0) {
      ListProof right = new ListProofHashNode(H1);
      root = new ListProofBranch(left, right);
      left = root;
      d--;
    }
    return root;
  }
}
