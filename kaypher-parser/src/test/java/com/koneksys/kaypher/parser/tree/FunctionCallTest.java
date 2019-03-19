/*
 * Copyright 2019 Koneksys
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

package com.koneksys.kaypher.parser.tree;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

public class FunctionCallTest {

  public static final NodeLocation SOME_LOCATION = new NodeLocation(0, 0);
  public static final NodeLocation OTHER_LOCATION = new NodeLocation(1, 0);
  private static final QualifiedName SOME_NAME = QualifiedName.of("bob");
  private static final List<Expression> SOME_ARGS = ImmutableList.of(
      new StringLiteral("jane"));

  @Test
  public void shouldImplementHashCodeAndEqualsProperty() {
    new EqualsTester()
        .addEqualityGroup(
            // Note: At the moment location does not take part in equality testing
            new FunctionCall(SOME_NAME, SOME_ARGS),
            new FunctionCall(SOME_NAME, SOME_ARGS),
            new FunctionCall(Optional.of(SOME_LOCATION), SOME_NAME, SOME_ARGS),
            new FunctionCall(Optional.of(OTHER_LOCATION), SOME_NAME, SOME_ARGS)
        )
        .addEqualityGroup(
            new FunctionCall(QualifiedName.of("diff"), SOME_ARGS)
        )
        .addEqualityGroup(
            new FunctionCall(SOME_NAME, ImmutableList.of())
        )
        .testEquals();
  }
}