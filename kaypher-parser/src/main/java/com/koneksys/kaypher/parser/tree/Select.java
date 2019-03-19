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

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Immutable
public class Select extends Node {

  private final ImmutableList<SelectItem> selectItems;

  public Select(final List<SelectItem> selectItems) {
    this(Optional.empty(), selectItems);
  }

  public Select(
      final Optional<NodeLocation> location,
      final List<SelectItem> selectItems
  ) {
    super(location);
    this.selectItems = ImmutableList.copyOf(requireNonNull(selectItems, "selectItems"));
  }

  public List<SelectItem> getSelectItems() {
    return selectItems;
  }

  @Override
  public <R, C> R accept(final AstVisitor<R, C> visitor, final C context) {
    return visitor.visitSelect(this, context);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("selectItems", selectItems)
        .omitNullValues()
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Select select = (Select) o;
    return Objects.equals(selectItems, select.selectItems);
  }

  @Override
  public int hashCode() {
    return Objects.hash(selectItems);
  }
}
