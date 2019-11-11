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
package com.treutec.kaypher.parser;

import com.treutec.kaypher.execution.expression.tree.Literal;
import com.treutec.kaypher.util.KaypherException;

/**
 * Utility class for working with {@link Literal} properties.
 */
public final class LiteralUtil {

  private LiteralUtil() {
  }

  public static boolean toBoolean(final Literal literal, final String propertyName) {
    final String value = literal.getValue().toString();
    final boolean isTrue = value.equalsIgnoreCase("true");
    final boolean isFalse = value.equalsIgnoreCase("false");
    if (!isTrue && !isFalse) {
      throw new KaypherException("Property '" + propertyName + "' is not a boolean value");
    }
    return isTrue;
  }
}
