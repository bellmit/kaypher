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

package com.treutec.kaypher.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.Test;

public class IdentifierUtilTest {

  @Test
  public void shouldNeedBackQuotes() {
    // Given:
    final String[] identifiers = new String[]{
        "SELECT",   // reserved word
        "@ID",      // invalid character
        "FOO.BAR",  // with a dot
        "foo"       // lower case
    };

    // Then:
    for (final String identifier : identifiers) {
      assertThat("Expected quotes for " + identifier, IdentifierUtil.needsQuotes(identifier));
    }
  }

  @Test
  public void shouldNotNeedBackQuotes() {
    // Given:
    final String[] identifiers = new String[]{
        "FOO",      // nothing special
        "TABLES",   // in vocabulary but non-reserved
        "`SELECT`"  // already has back quotes
    };

    // Then:
    for (final String identifier : identifiers) {
      assertThat("Expected no quotes for " + identifier, !IdentifierUtil.needsQuotes(identifier));
    }
  }

  @Test
  public void shouldBeValid() {
    // Given:
    final String[] identifiers = new String[]{
        "FOO",   // nothing special
        "foo",   // lower-case
    };

    // Then:
    for (final String identifier : identifiers) {
      assertThat(
          "Expected " + identifier + " to be valid.",
          IdentifierUtil.isValid(identifier)
      );
    }
  }

  @Test
  public void shouldNotBeValid() {
    // Given:
    final String[] identifiers = new String[]{
        "@FOO",    // invalid character
        "FOO.BAR", // Dot
        "SELECT"   // reserved word
    };

    // Then:
    for (final String identifier : identifiers) {
      assertThat(
          "Expected " + identifier + " to be invalid",
          !IdentifierUtil.isValid(identifier)
      );
    }
  }

}