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

import static com.koneksys.kaypher.parser.tree.ArithmeticBinaryExpression.Type.ADD;
import static com.koneksys.kaypher.parser.tree.ArithmeticBinaryExpression.Type.DIVIDE;
import static org.mockito.Mockito.mock;

import com.google.common.testing.EqualsTester;
import java.util.Optional;
import org.junit.Test;

public class ArithmeticBinaryExpressionTest {

  public static final NodeLocation SOME_LOCATION = new NodeLocation(0, 0);
  public static final NodeLocation OTHER_LOCATION = new NodeLocation(1, 0);
  private static final Expression EXP_0 = mock(Expression.class);
  private static final Expression EXP_1 = mock(Expression.class);

  @Test
  public void shouldImplementHashCodeAndEqualsProperty() {
    new EqualsTester()
        .addEqualityGroup(
            // Note: At the moment location does not take part in equality testing
            new ArithmeticBinaryExpression(ADD, EXP_0, EXP_1),
            new ArithmeticBinaryExpression(ADD, EXP_0, EXP_1),
            new ArithmeticBinaryExpression(Optional.of(SOME_LOCATION), ADD, EXP_0, EXP_1),
            new ArithmeticBinaryExpression(Optional.of(OTHER_LOCATION), ADD, EXP_0, EXP_1)
        )
        .addEqualityGroup(
            new ArithmeticBinaryExpression(DIVIDE, EXP_0, EXP_1)
        )
        .addEqualityGroup(
            new ArithmeticBinaryExpression(ADD, EXP_1, EXP_1)
        )
        .addEqualityGroup(
            new ArithmeticBinaryExpression(ADD, EXP_0, EXP_0)
        )
        .testEquals();
  }
}