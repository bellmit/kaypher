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

package com.treutec.kaypher.serde.connect;

import java.util.Map;
import java.util.Objects;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.storage.Converter;

public class KaypherConnectDeserializer implements Deserializer<Object> {

  private final Converter converter;
  private final DataTranslator translator;

  public KaypherConnectDeserializer(
      final Converter converter,
      final DataTranslator translator
  ) {
    this.converter = Objects.requireNonNull(converter, "converter");
    this.translator = Objects.requireNonNull(translator, "translator");
  }

  @Override
  public void configure(final Map<String, ?> map, final boolean b) {
  }

  @Override
  public Object deserialize(final String topic, final byte[] bytes) {
    try {
      final SchemaAndValue schemaAndValue = converter.toConnectData(topic, bytes);
      return translator.toKaypherRow(schemaAndValue.schema(), schemaAndValue.value());
    } catch (final Exception e) {
      throw new SerializationException(
          "Error deserializing message from topic: " + topic, e);
    }
  }

  @Override
  public void close() {
  }
}
