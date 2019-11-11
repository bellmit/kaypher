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

package com.treutec.kaypher.statement;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.treutec.kaypher.parser.KaypherParser.PreparedStatement;
import com.treutec.kaypher.parser.tree.Statement;
import com.treutec.kaypher.util.KaypherConfig;
import java.util.Map;
import java.util.Objects;

/**
 * A prepared statement paired with the configurations needed to fully
 * execute it.
 */
@Immutable
public final class ConfiguredStatement<T extends Statement> {

  private final PreparedStatement<T> statement;
  private final Map<String, Object> overrides;
  private final KaypherConfig config;

  public static <S extends Statement> ConfiguredStatement<S> of(
      final PreparedStatement<S> statement,
      final Map<String, ?> overrides,
      final KaypherConfig config
  ) {
    return new ConfiguredStatement<>(statement, overrides, config);
  }

  private ConfiguredStatement(
      final PreparedStatement<T> statement,
      final Map<String, ?> overrides,
      final KaypherConfig config
  ) {
    this.statement = requireNonNull(statement, "statement");
    this.overrides = ImmutableMap.copyOf(requireNonNull(overrides, "overrides"));
    this.config = requireNonNull(config, "config");
  }

  @SuppressWarnings("unchecked")
  public <S extends Statement> ConfiguredStatement<S> cast() {
    return (ConfiguredStatement<S>) this;
  }

  public T getStatement() {
    return statement.getStatement();
  }

  public String getStatementText() {
    return statement.getStatementText();
  }

  public Map<String, Object> getOverrides() {
    return overrides;
  }

  public KaypherConfig getConfig() {
    return config;
  }

  public ConfiguredStatement<T> withConfig(final KaypherConfig config) {
    return new ConfiguredStatement<>(this.statement, this.overrides, config);
  }

  public ConfiguredStatement<T> withProperties(final Map<String, Object> properties) {
    return new ConfiguredStatement<>(this.statement, properties, this.config);
  }

  public ConfiguredStatement<T> withStatement(
      final String statementText,
      final T statement) {
    return new ConfiguredStatement<>(
        PreparedStatement.of(statementText, statement), this.overrides, this.config);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ConfiguredStatement<?> that = (ConfiguredStatement<?>) o;
    return Objects.equals(statement, that.statement)
        && Objects.equals(overrides, that.overrides)
        && Objects.equals(config, that.config);
  }

  @Override
  public int hashCode() {
    return Objects.hash(statement, overrides, config);
  }

  @Override
  public String toString() {
    return "ConfiguredStatement{"
        + "statement=" + statement
        + ", overrides=" + overrides
        + ", config=" + config
        + '}';
  }
}
