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

public final class KaypherPreconditions {

  private KaypherPreconditions() { }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws KaypherException if {@code expression} is false
   */
  public static void checkArgument(final boolean expression, final String message) {
    if (!expression) {
      throw new KaypherException(message);
    }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws KaypherServerException if {@code expression} is false
   */
  public static void checkServerCondition(final boolean expression, final String message) {
    if (!expression) {
      throw new KaypherServerException(message);
    }
  }

}
