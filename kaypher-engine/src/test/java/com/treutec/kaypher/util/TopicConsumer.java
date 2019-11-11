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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import com.google.common.collect.ImmutableMap;
import com.treutec.kaypher.GenericRow;
import com.treutec.kaypher.logging.processing.ProcessingLogContext;
import com.treutec.kaypher.schema.kaypher.PhysicalSchema;
import com.treutec.kaypher.serde.Format;
import com.treutec.kaypher.serde.FormatInfo;
import com.treutec.kaypher.serde.GenericRowSerDe;
import com.treutec.kaypher.test.util.EmbeddedSingleNodeKafkaCluster;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.hamcrest.Matcher;

public class TopicConsumer {

  private static final long RESULTS_POLL_MAX_TIME_MS = 30000;
  private static final Duration RESULTS_EXTRA_POLL_TIME = Duration.ofMillis(250);

  private final EmbeddedSingleNodeKafkaCluster cluster;
  private final ProcessingLogContext processingLogContext = ProcessingLogContext.create();

  public TopicConsumer(final EmbeddedSingleNodeKafkaCluster cluster) {
    this.cluster = cluster;
  }

  public <K, V> Map<K, V> readResults(
      final String topic,
      final Matcher<Integer> expectedNumMessages,
      final Deserializer<V> valueDeserializer,
      final Deserializer<K> keyDeserializer
  ) {
    final Map<K, V> result = new HashMap<>();

    final Properties consumerConfig = new Properties();
    consumerConfig.putAll(cluster.getClientProperties());
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
    consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    try (KafkaConsumer<K, V> consumer =
             new KafkaConsumer<>(consumerConfig, keyDeserializer, valueDeserializer)
    ) {
      consumer.subscribe(Collections.singleton(topic));
      final long pollStart = System.currentTimeMillis();
      final long pollEnd = pollStart + RESULTS_POLL_MAX_TIME_MS;
      while (System.currentTimeMillis() < pollEnd && !expectedNumMessages.matches(result.size())) {
        for (final ConsumerRecord<K, V> record :
            consumer.poll(Duration.ofMillis(Math.max(1, pollEnd - System.currentTimeMillis())))) {
          if (record.value() != null) {
            result.put(record.key(), record.value());
          }
        }
      }

      for (final ConsumerRecord<K, V> record : consumer.poll(RESULTS_EXTRA_POLL_TIME)) {
        if (record.value() != null) {
          result.put(record.key(), record.value());
        }
      }
    }
    return result;
  }

  public <K> Map<K, GenericRow> readResults(
      final String topic,
      final PhysicalSchema schema,
      final int expectedNumMessages,
      final Deserializer<K> keyDeserializer
  ) {
    final Deserializer<GenericRow> deserializer = GenericRowSerDe.from(
        FormatInfo.of(Format.JSON, Optional.empty(), Optional.empty()),
        schema.valueSchema(),
        new KaypherConfig(ImmutableMap.of()),
        () -> null,
        "consumer",
        processingLogContext
    ).deserializer();

    return readResults(
        topic,
        greaterThanOrEqualTo(expectedNumMessages),
        deserializer,
        keyDeserializer
    );
  }

  public void verifyRecordsReceived(final String topic,
                                    final Matcher<Integer> expectedNumMessages) {
    verifyRecordsReceived(
        topic,
        expectedNumMessages,
        new ByteArrayDeserializer(),
        new ByteArrayDeserializer());
  }

  public <K, V> Map<K, V> verifyRecordsReceived(final String topic,
                                                final Matcher<Integer> expectedNumMessages,
                                                final Deserializer<V> valueDeserializer,
                                                final Deserializer<K> keyDeserializer) {
    final Map<K, V> records =
        readResults(topic, expectedNumMessages, valueDeserializer, keyDeserializer);

    assertThat(records.keySet(), hasSize(expectedNumMessages));

    return records;
  }
}
