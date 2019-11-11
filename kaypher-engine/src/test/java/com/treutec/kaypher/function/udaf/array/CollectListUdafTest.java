/*
 * Copyright 2019 Treu Techologies
 *
 * See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.treutec.kaypher.function.udaf.array;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.treutec.kaypher.function.udaf.TableUdaf;
import java.util.List;
import org.junit.Test;

public class CollectListUdafTest {

  @Test
  public void shouldCollectInts() {
    final TableUdaf<Integer, List<Integer>, List<Integer>> udaf = CollectListUdaf.createCollectListInt();
    final Integer[] values = new Integer[] {3, 4, 5, 3};
    List<Integer> runningList = udaf.initialize();
    for (final Integer i : values) {
      runningList = udaf.aggregate(i, runningList);
    }
    assertThat(runningList, contains(3, 4, 5, 3));
  }

  @Test
  public void shouldMergeIntLists() {
    final TableUdaf<Integer, List<Integer>, List<Integer>> udaf = CollectListUdaf.createCollectListInt();

    List<Integer> lhs = udaf.initialize();
    final Integer[] lhsValues = new Integer[] {1, 2, null, 3};
    for (final Integer i : lhsValues) {
      lhs = udaf.aggregate(i, lhs);
    }
    assertThat(lhs, contains(1, 2, null, 3));

    List<Integer> rhs = udaf.initialize();
    final Integer[] rhsValues = new Integer[] {2, null, 3, 4, 5, 6};
    for (final Integer i : rhsValues) {
      rhs = udaf.aggregate(i, rhs);
    }
    assertThat(rhs, contains(2, null, 3, 4, 5, 6));

    final List<Integer> merged = udaf.merge(lhs, rhs);
    assertThat(merged, contains(1, 2, null, 3, 2, null, 3, 4, 5, 6));
  }

  @Test
  public void shouldRespectSizeLimit() {
    final TableUdaf<Integer, List<Integer>, List<Integer>> udaf = CollectListUdaf.createCollectListInt();
    List<Integer> runningList = udaf.initialize();
    for (int i = 1; i < 2500; i++) {
      runningList = udaf.aggregate(i, runningList);
    }
    assertThat(runningList, hasSize(1000));
    assertThat(runningList, hasItem(1));
    assertThat(runningList, hasItem(1000));
    assertThat(runningList, not(hasItem(1001)));
  }

}
