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
package com.treutec.kaypher.parser.properties.with;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.errorprone.annotations.Immutable;
import com.treutec.kaypher.execution.expression.tree.Literal;
import com.treutec.kaypher.properties.with.ConfigMetaData;
import com.treutec.kaypher.util.KaypherException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.common.config.AbstractConfig;

/**
 * Helper for handling 'with clause' properties.
 */
@Immutable
final class PropertiesConfig extends AbstractConfig {

  private final ConfigMetaData configDetails;
  private final ImmutableMap<String, Literal> originalLiterals;

  PropertiesConfig(final ConfigMetaData configDetails, final Map<String, Literal> originals) {
    super(
        configDetails.getConfigDef(),
        toValues(
            configDetails.getShortConfigs(),
            Objects.requireNonNull(originals, "originals")
        ),
        false
    );

    throwOnUnknownProperty(configDetails.getConfigNames(), originals);

    this.configDetails = Objects.requireNonNull(configDetails, "configDetails");

    this.originalLiterals = ImmutableMap.copyOf(originals.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().toUpperCase(), Map.Entry::getValue)));
  }

  @Override
  public String toString() {
    return configDetails.getOrderedConfigNames().stream()
        .filter(originalLiterals::containsKey)
        .map(name -> name + "=" + originalLiterals.get(name))
        .collect(Collectors.joining(", "));
  }

  public Map<String, Literal> copyOfOriginalLiterals() {
    return new HashMap<>(originalLiterals);
  }

  void validateDateTimeFormat(final String configName) {
    final Object value = originals().get(configName);
    if (value == null) {
      return;
    }

    final String pattern = value.toString();

    try {
      DateTimeFormatter.ofPattern(pattern);
    } catch (final Exception e) {
      throw new KaypherException("Invalid datatime format for"
          + " config:" + configName
          + ", reason:" + e.getMessage(), e);
    }
  }

  private static Map<String, Object> toValues(
      final Set<String> shortConfigProperties,
      final Map<String, Literal> literals
  ) {
    final Map<String, Object> values = literals.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().toUpperCase(), e -> e.getValue().getValue()));

    shortConfigProperties.forEach(configName -> {
      final Object rf = values.get(configName);
      if (rf instanceof Number) {
        values.put(configName, ((Number) rf).shortValue());
      }
    });

    return values;
  }

  private static void throwOnUnknownProperty(
      final Set<String> configNames,
      final Map<String, ?> originals
  ) {
    final Set<String> providedNames = originals.keySet().stream()
        .map(String::toUpperCase)
        .collect(Collectors.toSet());

    final SetView<String> onlyInProvided = Sets.difference(providedNames, configNames);
    if (!onlyInProvided.isEmpty()) {
      throw new KaypherException("Invalid config variable(s) in the WITH clause: "
          + String.join(",", onlyInProvided));
    }
  }

}
