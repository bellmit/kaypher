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

package com.treutec.kaypher.properties;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.treutec.kaypher.config.ImmutableProperties;
import com.treutec.kaypher.config.PropertyValidator;
import com.treutec.kaypher.util.KaypherConfig;
import com.treutec.kaypher.util.KaypherConstants;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.StreamsConfig;

/**
 * This class adds additional validation of properties on top of that provided by the
 * {@code ConfigDef} instances.
 */
public class LocalPropertyValidator implements PropertyValidator {

  // Only these config properties can be configured using SET/UNSET commands.
  public static final Set<String> CONFIG_PROPERTY_WHITELIST = ImmutableSet.<String>builder()
      .add(KaypherConfig.SINK_WINDOW_CHANGE_LOG_ADDITIONAL_RETENTION_MS_PROPERTY)
      .add(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG)
      .add(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)
      .add(KaypherConstants.LEGACY_RUN_SCRIPT_STATEMENTS_CONTENT)
      .add(ConsumerConfig.GROUP_ID_CONFIG)
      .build();

  private final Set<String> immutableProps;

  private static final Map<String, Consumer<Object>> HANDLERS =
      ImmutableMap.<String, Consumer<Object>>builder()
      .put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
          LocalPropertyValidator::validateConsumerOffsetResetConfig)
      .build();

  LocalPropertyValidator() {
    this(ImmutableProperties.getImmutableProperties());
  }

  LocalPropertyValidator(final Collection<String> immutableProps) {
    this.immutableProps = ImmutableSet.copyOf(
        Objects.requireNonNull(immutableProps, "immutableProps"));
  }


  @Override
  public void validate(final String name, final Object value) {
    if (immutableProps.contains(name)) {
      throw new IllegalArgumentException(String.format("Cannot override property '%s'", name));
    }

    final Consumer<Object> validator = HANDLERS.get(name);
    if (validator != null) {
      validator.accept(value);
    }
  }

  private static void validateConsumerOffsetResetConfig(final Object value) {
    if (value instanceof String && "none".equalsIgnoreCase((String)value)) {
      throw new IllegalArgumentException("'none' is not valid for this property within KAYPHER");
    }
  }
}
