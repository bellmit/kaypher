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
package com.treutec.kaypher.ddl.commands;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.treutec.kaypher.execution.ddl.commands.DropTypeCommand;
import com.treutec.kaypher.parser.DropType;
import java.util.Optional;
import org.junit.Test;

public class DropTypeFactoryTest {
  private static final String SOME_TYPE_NAME = "some_type";

  private final DropTypeFactory factory = new DropTypeFactory();

  @Test
  public void shouldCreateDropType() {
    // Given:
    final DropType dropType = new DropType(Optional.empty(), SOME_TYPE_NAME);

    // When:
    final DropTypeCommand cmd = factory.create(dropType);

    // Then:
    assertThat(cmd.getTypeName(), equalTo(SOME_TYPE_NAME));
  }
}
