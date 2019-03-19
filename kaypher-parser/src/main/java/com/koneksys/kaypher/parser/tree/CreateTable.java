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

import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Immutable
public class CreateTable extends AbstractStreamCreateStatement implements ExecutableDdlStatement {

  public CreateTable(
      final QualifiedName name,
      final List<TableElement> elements,
      final boolean notExists,
      final Map<String, Expression> properties
  ) {
    this(Optional.empty(), name, elements, notExists, properties);
  }

  public CreateTable(
      final Optional<NodeLocation> location,
      final QualifiedName name,
      final List<TableElement> elements,
      final boolean notExists,
      final Map<String, Expression> properties
  ) {
    super(location, name, elements, notExists, properties);
  }

  @Override
  public AbstractStreamCreateStatement copyWith(
      final List<TableElement> elements,
      final Map<String, Expression> properties
  ) {
    return new CreateTable(
        getLocation(),
        getName(),
        elements,
        isNotExists(),
        properties);
  }

  @Override
  public <R, C> R accept(final AstVisitor<R, C> visitor, final C context) {
    return visitor.visitCreateTable(this, context);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if ((obj == null) || (getClass() != obj.getClass())) {
      return false;
    }
    return super.equals(obj);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("name", getName())
        .add("elements", getElements())
        .add("notExists", isNotExists())
        .add("properties", getProperties())
        .toString();
  }
}
