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

import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import java.util.Optional;

@Immutable
public class BooleanLiteral extends Literal {

  private final boolean value;

  public BooleanLiteral(final String value) {
    this(Optional.empty(), value);
  }

  public BooleanLiteral(
      final Optional<NodeLocation> location,
      final String value
  ) {
    super(location);
    this.value = requireNonNull(value, "value")
        .toLowerCase(ENGLISH)
        .equals("true");

    Preconditions.checkArgument(value.toLowerCase(ENGLISH).equals("true")
        || value.toLowerCase(ENGLISH).equals("false"));
  }

  @Override
  public Boolean getValue() {
    return value;
  }

  @Override
  public <R, C> R accept(final AstVisitor<R, C> visitor, final C context) {
    return visitor.visitBooleanLiteral(this, context);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final BooleanLiteral other = (BooleanLiteral) obj;
    return Objects.equals(this.value, other.value);
  }
}
