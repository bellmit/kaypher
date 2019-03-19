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
import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;

public class DropTopic extends Statement implements ExecutableDdlStatement {

  private final QualifiedName topicName;
  private final boolean exists;

  public DropTopic(
      final QualifiedName tableName,
      final boolean exists
  ) {
    this(Optional.empty(), tableName, exists);
  }

  public DropTopic(
      final Optional<NodeLocation> location,
      final QualifiedName topicName,
      final boolean exists
  ) {
    super(location);
    this.topicName = requireNonNull(topicName, "topicName");
    this.exists = exists;
  }

  public QualifiedName getTopicName() {
    return topicName;
  }

  public boolean isExists() {
    return exists;
  }

  @Override
  public <R, C> R accept(final AstVisitor<R, C> visitor, final C context) {
    return visitor.visitDropTopic(this, context);
  }

  @Override
  public int hashCode() {
    return hash(topicName, exists);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if ((obj == null) || (getClass() != obj.getClass())) {
      return false;
    }
    final DropTopic o = (DropTopic) obj;
    return Objects.equals(topicName, o.topicName)
           && (exists == o.exists);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("topicName", topicName)
        .add("exists", exists)
        .toString();
  }
}