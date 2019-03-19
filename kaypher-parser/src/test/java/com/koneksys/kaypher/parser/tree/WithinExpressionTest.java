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

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class WithinExpressionTest {

  @Test
  public void shouldDisplayCorrectStringWithSingleWithin() {
    final WithinExpression expression = new WithinExpression(20, TimeUnit.SECONDS);
    assertEquals(" WITHIN 20 SECONDS", expression.toString());
  }

  @Test
  public void shouldDisplayCorrectStringWithBeforeAndAfter() {
    final WithinExpression expression = new WithinExpression(30, 40, TimeUnit.MINUTES, TimeUnit.HOURS);
    assertEquals(" WITHIN (30 MINUTES, 40 HOURS)", expression.toString());
  }

}
