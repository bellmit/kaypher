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

package com.koneksys.kaypher.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import com.google.common.collect.ImmutableMap;
import com.koneksys.kaypher.errors.LogMetricAndContinueExceptionHandler;
import com.koneksys.kaypher.errors.ProductionExceptionHandlerUtil.LogAndContinueProductionExceptionHandler;
import com.koneksys.kaypher.errors.ProductionExceptionHandlerUtil.LogAndFailProductionExceptionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class KsqlConfigTest {
  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldSetInitialValuesCorrectly() {
    final Map<String, Object> initialProps = new HashMap<>();
    initialProps.put(KsqlConfig.SINK_NUMBER_OF_PARTITIONS_PROPERTY, 10);
    initialProps.put(KsqlConfig.SINK_NUMBER_OF_REPLICAS_PROPERTY, (short) 3);
    initialProps.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 800);
    initialProps.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 5);

    final KsqlConfig kaypherConfig = new KsqlConfig(initialProps);

    assertThat(kaypherConfig.getInt(KsqlConfig.SINK_NUMBER_OF_PARTITIONS_PROPERTY), equalTo(10));
    assertThat(kaypherConfig.getShort(KsqlConfig.SINK_NUMBER_OF_REPLICAS_PROPERTY), equalTo((short) 3));

  }

  @Test
  public void shouldSetLogAndContinueExceptionHandlerByDefault() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.emptyMap());
    final Object result = kaypherConfig.getKsqlStreamConfigProps().get(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG);
    assertThat(result, equalTo(LogMetricAndContinueExceptionHandler.class));
  }

  @Test
  public void shouldSetLogAndContinueExceptionHandlerWhenFailOnDeserializationErrorFalse() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.singletonMap(KsqlConfig.FAIL_ON_DESERIALIZATION_ERROR_CONFIG, false));
    final Object result = kaypherConfig.getKsqlStreamConfigProps().get(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG);
    assertThat(result, equalTo(LogMetricAndContinueExceptionHandler.class));
  }

  @Test
  public void shouldNotSetDeserializationExceptionHandlerWhenFailOnDeserializationErrorTrue() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.singletonMap(KsqlConfig.FAIL_ON_DESERIALIZATION_ERROR_CONFIG, true));
    final Object result = kaypherConfig.getKsqlStreamConfigProps().get(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG);
    assertThat(result, nullValue());
  }

  @Test
  public void shouldSetLogAndContinueExceptionHandlerWhenFailOnProductionErrorFalse() {
    final KsqlConfig kaypherConfig =
        new KsqlConfig(Collections.singletonMap(KsqlConfig.FAIL_ON_PRODUCTION_ERROR_CONFIG, false));
    final Object result = kaypherConfig.getKsqlStreamConfigProps()
        .get(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG);
    assertThat(result, equalTo(LogAndContinueProductionExceptionHandler.class));
  }

  @Test
  public void shouldNotSetDeserializationExceptionHandlerWhenFailOnProductionErrorTrue() {
    final KsqlConfig kaypherConfig =
        new KsqlConfig(Collections.singletonMap(KsqlConfig.FAIL_ON_PRODUCTION_ERROR_CONFIG, true));
    final Object result = kaypherConfig.getKsqlStreamConfigProps()
        .get(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG);
    assertThat(result, equalTo(LogAndFailProductionExceptionHandler.class));
  }

  @Test
  public void shouldFailOnProductionErrorByDefault() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.emptyMap());
    final Object result = kaypherConfig.getKsqlStreamConfigProps()
        .get(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG);
    assertThat(result, equalTo(LogAndFailProductionExceptionHandler.class));
  }

  @Test
  public void shouldSetStreamsConfigConsumerUnprefixedProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.singletonMap(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));
    final Object result = kaypherConfig.getKsqlStreamConfigProps().get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
    assertThat(result, equalTo("earliest"));
  }

  @Test
  public void shouldSetStreamsConfigConsumerPrefixedProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(
        Collections.singletonMap(
            StreamsConfig.CONSUMER_PREFIX + ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "100"));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
        .get(StreamsConfig.CONSUMER_PREFIX + ConsumerConfig.FETCH_MIN_BYTES_CONFIG),
        equalTo(100));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
            .get(ConsumerConfig.FETCH_MIN_BYTES_CONFIG),
        is(nullValue()));
  }

  @Test
  public void shouldSetStreamsConfigConsumerKsqlPrefixedProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(
        Collections.singletonMap(
            KsqlConfig.KSQL_STREAMS_PREFIX + StreamsConfig.CONSUMER_PREFIX + ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "100"));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
            .get(StreamsConfig.CONSUMER_PREFIX + ConsumerConfig.FETCH_MIN_BYTES_CONFIG),
        equalTo(100));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
            .get(ConsumerConfig.FETCH_MIN_BYTES_CONFIG),
        is(nullValue()));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
            .get(KsqlConfig.KSQL_STREAMS_PREFIX + StreamsConfig.CONSUMER_PREFIX + ConsumerConfig.FETCH_MIN_BYTES_CONFIG),
        is(nullValue()));
  }

  @Test
  public void shouldSetStreamsConfigProducerUnprefixedProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(
        Collections.singletonMap(ProducerConfig.BUFFER_MEMORY_CONFIG, "1024"));
    final Object result = kaypherConfig.getKsqlStreamConfigProps().get(ProducerConfig.BUFFER_MEMORY_CONFIG);
    assertThat(result, equalTo(1024L));
  }

  @Test
  public void shouldSetStreamsConfigProducerPrefixedProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(
        Collections.singletonMap(
            StreamsConfig.PRODUCER_PREFIX + ProducerConfig.BUFFER_MEMORY_CONFIG, "1024"));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
        .get(StreamsConfig.PRODUCER_PREFIX + ProducerConfig.BUFFER_MEMORY_CONFIG),
        equalTo(1024L));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
            .get(ProducerConfig.BUFFER_MEMORY_CONFIG),
        is(nullValue()));
  }

  @Test
  public void shouldSetStreamsConfigKsqlProducerPrefixedProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(
        Collections.singletonMap(
            KsqlConfig.KSQL_STREAMS_PREFIX + StreamsConfig.PRODUCER_PREFIX + ProducerConfig.BUFFER_MEMORY_CONFIG, "1024"));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
            .get(StreamsConfig.PRODUCER_PREFIX + ProducerConfig.BUFFER_MEMORY_CONFIG),
        equalTo(1024L));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
            .get(ProducerConfig.BUFFER_MEMORY_CONFIG),
        is(nullValue()));

    assertThat(kaypherConfig.getKsqlStreamConfigProps()
            .get(KsqlConfig.KSQL_STREAMS_PREFIX + StreamsConfig.PRODUCER_PREFIX + ProducerConfig.BUFFER_MEMORY_CONFIG),
        is(nullValue()));
  }

  @Test
  public void shouldSetStreamsConfigAdminClientProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(
        Collections.singletonMap(AdminClientConfig.RETRIES_CONFIG, 3));
    final Object result = kaypherConfig.getKsqlStreamConfigProps().get(
        AdminClientConfig.RETRIES_CONFIG);
    assertThat(result, equalTo(3));
  }

  @Test
  public void shouldSetStreamsConfigProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(
        Collections.singletonMap(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "128"));
    final Object result = kaypherConfig.getKsqlStreamConfigProps().get(
        StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG);
    assertThat(result, equalTo(128L));
  }

  @Test
  public void shouldSetPrefixedStreamsConfigProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.singletonMap(
        KsqlConfig.KSQL_STREAMS_PREFIX + StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "128"));

    assertThat(kaypherConfig.getKsqlStreamConfigProps().
        get(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG), equalTo(128L));

    assertThat(kaypherConfig.getKsqlStreamConfigProps().
        get(KsqlConfig.KSQL_STREAMS_PREFIX + StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG),
        is(nullValue()));
  }

  @Test
  public void shouldSetMonitoringInterceptorConfigProperties() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.singletonMap(
        "koneksys.monitoring.interceptor.topic", "foo"));
    final Object result
        = kaypherConfig.getKsqlStreamConfigProps().get("koneksys.monitoring.interceptor.topic");
    assertThat(result, equalTo("foo"));
  }

  @Test
  public void shouldFilterPropertiesForWhichTypeUnknown() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.singletonMap("you.shall.not.pass", "wizard"));
    assertThat(
        kaypherConfig.getAllConfigPropsWithSecretsObfuscated().keySet(),
        not(hasItem("you.shall.not.pass")));
  }

  @Test
  public void shouldCloneWithKsqlPropertyOverwrite() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.singletonMap(
        KsqlConfig.KSQL_SERVICE_ID_CONFIG, "test"));
    final KsqlConfig kaypherConfigClone = kaypherConfig.cloneWithPropertyOverwrite(
        Collections.singletonMap(
            KsqlConfig.KSQL_SERVICE_ID_CONFIG, "test-2"));
    final String result = kaypherConfigClone.getString(KsqlConfig.KSQL_SERVICE_ID_CONFIG);
    assertThat(result, equalTo("test-2"));
  }

  @Test
  public void shouldCloneWithStreamPropertyOverwrite() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.singletonMap(
        ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "100"));
    final KsqlConfig kaypherConfigClone = kaypherConfig.cloneWithPropertyOverwrite(
        Collections.singletonMap(
            ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "200"));
    final Object result = kaypherConfigClone.getKsqlStreamConfigProps().get(ConsumerConfig.FETCH_MIN_BYTES_CONFIG);
    assertThat(result, equalTo(200));
  }

  @Test
  public void shouldCloneWithMultipleOverwrites() {
    final KsqlConfig kaypherConfig = new KsqlConfig(ImmutableMap.of(
        ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "123",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"
    ));
    final KsqlConfig clone = kaypherConfig.cloneWithPropertyOverwrite(ImmutableMap.of(
        StreamsConfig.NUM_STREAM_THREADS_CONFIG, "2",
        ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "456"
    ));
    final KsqlConfig cloneClone = clone.cloneWithPropertyOverwrite(ImmutableMap.of(
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
        StreamsConfig.METADATA_MAX_AGE_CONFIG, "13"
    ));
    final Map<String, ?> props = cloneClone.getKsqlStreamConfigProps();
    assertThat(props.get(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG), equalTo(456));
    assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), equalTo("earliest"));
    assertThat(props.get(StreamsConfig.NUM_STREAM_THREADS_CONFIG), equalTo(2));
    assertThat(props.get(StreamsConfig.METADATA_MAX_AGE_CONFIG), equalTo(13L));
  }

  @Test
  public void shouldCloneWithPrefixedStreamPropertyOverwrite() {
    final KsqlConfig kaypherConfig = new KsqlConfig(Collections.singletonMap(
        KsqlConfig.KSQL_STREAMS_PREFIX + ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "100"));
    final KsqlConfig kaypherConfigClone = kaypherConfig.cloneWithPropertyOverwrite(
        Collections.singletonMap(
            KsqlConfig.KSQL_STREAMS_PREFIX + ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "200"));
    final Object result = kaypherConfigClone.getKsqlStreamConfigProps().get(ConsumerConfig.FETCH_MIN_BYTES_CONFIG);
    assertThat(result, equalTo(200));
  }

  @Test
  public void shouldPreserveOriginalCompatibilitySensitiveConfigs() {
    final Map<String, String> originalProperties = ImmutableMap.of(
        KsqlConfig.KSQL_PERSISTENT_QUERY_NAME_PREFIX_CONFIG, "not_the_default");
    final KsqlConfig currentConfig = new KsqlConfig(Collections.emptyMap());
    final KsqlConfig compatibleConfig = currentConfig.overrideBreakingConfigsWithOriginalValues(originalProperties);
    assertThat(
        compatibleConfig.getString(KsqlConfig.KSQL_PERSISTENT_QUERY_NAME_PREFIX_CONFIG),
        equalTo("not_the_default"));
  }

  @Test
  public void shouldUseCurrentValueForCompatibilityInsensitiveConfigs() {
    final Map<String, String> originalProperties = Collections.singletonMap(KsqlConfig.KSQL_ENABLE_UDFS, "false");
    final KsqlConfig currentConfig = new KsqlConfig(Collections.singletonMap(KsqlConfig.KSQL_ENABLE_UDFS, true));
    final KsqlConfig compatibleConfig = currentConfig.overrideBreakingConfigsWithOriginalValues(originalProperties);
    assertThat(compatibleConfig.getBoolean(KsqlConfig.KSQL_ENABLE_UDFS), is(true));
  }

  @Test
  public void shouldReturnUdfConfig() {
    // Given:
    final String functionName = "bob";

    final String udfConfigName =
        KsqlConfig.KSQL_FUNCTIONS_PROPERTY_PREFIX + functionName + ".some-setting";

    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        udfConfigName, "should-be-visible"
    ));

    // When:
    final Map<String, ?> udfProps = config.getKsqlFunctionsConfigProps(functionName);

    // Then:
    assertThat(udfProps.get(udfConfigName), is("should-be-visible"));
  }

  @Test
  public void shouldReturnUdfConfigOnlyIfLowercase() {
    // Given:
    final String functionName = "BOB";

    final String correctConfigName =
        KsqlConfig.KSQL_FUNCTIONS_PROPERTY_PREFIX + functionName.toLowerCase() + ".some-setting";

    final String invalidConfigName =
        KsqlConfig.KSQL_FUNCTIONS_PROPERTY_PREFIX + functionName + ".some-other-setting";

    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        invalidConfigName, "should-not-be-visible",
        correctConfigName, "should-be-visible"
    ));

    // When:
    final Map<String, ?> udfProps = config.getKsqlFunctionsConfigProps(functionName);

    // Then:
    assertThat(udfProps.keySet(), contains(correctConfigName));
  }

  @Test
  public void shouldReturnUdfConfigAfterMerge() {
    final String functionName = "BOB";

    final String correctConfigName =
        KsqlConfig.KSQL_FUNCTIONS_PROPERTY_PREFIX + functionName.toLowerCase() + ".some-setting";

    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        correctConfigName, "should-be-visible"
    ));
    final KsqlConfig merged = config.overrideBreakingConfigsWithOriginalValues(Collections.emptyMap());

    // When:
    final Map<String, ?> udfProps = merged.getKsqlFunctionsConfigProps(functionName);

    // Then:
    assertThat(udfProps.keySet(), hasItem(correctConfigName));
  }

  @Test
  public void shouldReturnGlobalUdfConfig() {
    // Given:
    final String globalConfigName =
        KsqlConfig.KSQ_FUNCTIONS_GLOBAL_PROPERTY_PREFIX + ".some-setting";

    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        globalConfigName, "global"
    ));

    // When:
    final Map<String, ?> udfProps = config.getKsqlFunctionsConfigProps("what-eva");

    // Then:
    assertThat(udfProps.get(globalConfigName), is("global"));
  }

  @Test
  public void shouldNotReturnNoneUdfConfig() {
    // Given:
    final String functionName = "bob";
    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        KsqlConfig.KSQL_SERVICE_ID_CONFIG, "not a udf property",
        KsqlConfig.KSQL_FUNCTIONS_PROPERTY_PREFIX + "different_udf.some-setting", "different udf property"
    ));

    // When:
    final Map<String, ?> udfProps = config.getKsqlFunctionsConfigProps(functionName);

    // Then:
    assertThat(udfProps.keySet(), is(empty()));
  }

  @Test
  public void shouldListKnownKsqlConfig() {
    // Given:
    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        KsqlConfig.KSQL_SERVICE_ID_CONFIG, "not sensitive",
        SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, "sensitive!"
    ));

    // When:
    final Map<String, String> result = config.getAllConfigPropsWithSecretsObfuscated();

    // Then:
    assertThat(result.get(KsqlConfig.KSQL_SERVICE_ID_CONFIG), is("not sensitive"));
  }

  @Test
  public void shouldListKnownKsqlFunctionConfig() {
    // Given:
    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        KsqlConfig.KSQL_FUNCTIONS_SUBSTRING_LEGACY_ARGS_CONFIG, "true"
    ));

    // When:
    final Map<String, String> result = config.getAllConfigPropsWithSecretsObfuscated();

    // Then:
    assertThat(result.get(KsqlConfig.KSQL_FUNCTIONS_SUBSTRING_LEGACY_ARGS_CONFIG), is("true"));
  }

  @Test
  public void shouldListUnknownKsqlFunctionConfigObfuscated() {
    // Given:
    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        KsqlConfig.KSQL_FUNCTIONS_PROPERTY_PREFIX + "some_udf.some.prop", "maybe sensitive"
    ));

    // When:
    final Map<String, String> result = config.getAllConfigPropsWithSecretsObfuscated();

    // Then:
    assertThat(result.get(KsqlConfig.KSQL_FUNCTIONS_PROPERTY_PREFIX + "some_udf.some.prop"),
        is("[hidden]"));
  }

  @Test
  public void shouldListKnownStreamsConfigObfuscated() {
    // Given:
    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        StreamsConfig.APPLICATION_ID_CONFIG, "not sensitive",
        KsqlConfig.KSQL_STREAMS_PREFIX + SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "sensitive!",
        KsqlConfig.KSQL_STREAMS_PREFIX + StreamsConfig.CONSUMER_PREFIX +
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "sensitive!"
    ));

    // When:
    final Map<String, String> result = config.getAllConfigPropsWithSecretsObfuscated();

    // Then:
    assertThat(result.get(KsqlConfig.KSQL_STREAMS_PREFIX + StreamsConfig.APPLICATION_ID_CONFIG),
        is("not sensitive"));
    assertThat(result.get(
        KsqlConfig.KSQL_STREAMS_PREFIX + SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG),
        is("[hidden]"));
    assertThat(result.get(KsqlConfig.KSQL_STREAMS_PREFIX + StreamsConfig.CONSUMER_PREFIX
            + SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG),
        is("[hidden]"));
  }

  @Test
  public void shouldNotListUnresolvedServerConfig() {
    // Given:
    final KsqlConfig config = new KsqlConfig(ImmutableMap.of(
        "some.random.property", "might be sensitive"
    ));

    // When:
    final Map<String, String> result = config.getAllConfigPropsWithSecretsObfuscated();

    // Then:
    assertThat(result.get("some.random.property"), is(nullValue()));
  }

  @Test
  public void shouldDefaultOptimizationsToOn() {
    // When:
    final KsqlConfig config = new KsqlConfig(Collections.emptyMap());

    // Then:
    assertThat(
        config.getKsqlStreamConfigProps().get(StreamsConfig.TOPOLOGY_OPTIMIZATION),
        equalTo(StreamsConfig.OPTIMIZE));
  }

  @Test
  public void shouldDefaultOptimizationsToOffForOldConfigs() {
    // When:
    final KsqlConfig config = new KsqlConfig(Collections.emptyMap())
        .overrideBreakingConfigsWithOriginalValues(Collections.emptyMap());

    // Then:
    assertThat(
        config.getKsqlStreamConfigProps().get(StreamsConfig.TOPOLOGY_OPTIMIZATION),
        equalTo(StreamsConfig.NO_OPTIMIZATION));
  }

  @Test
  public void shouldPreserveOriginalOptimizationConfig() {
    // Given:
    final KsqlConfig config = new KsqlConfig(
        Collections.singletonMap(
            StreamsConfig.TOPOLOGY_OPTIMIZATION, StreamsConfig.OPTIMIZE));
    final KsqlConfig saved = new KsqlConfig(
        Collections.singletonMap(
            StreamsConfig.TOPOLOGY_OPTIMIZATION, StreamsConfig.NO_OPTIMIZATION));

    // When:
    final KsqlConfig merged = config.overrideBreakingConfigsWithOriginalValues(
        saved.getAllConfigPropsWithSecretsObfuscated());

    // Then:
    assertThat(
        merged.getKsqlStreamConfigProps().get(StreamsConfig.TOPOLOGY_OPTIMIZATION),
        equalTo(StreamsConfig.NO_OPTIMIZATION));
  }

  @Test
  public void shouldRaiseIfInternalTopicNamingOffAndStreamsOptimizationsOn() {
    expectedException.expect(RuntimeException.class);
    expectedException.expectMessage(
        "Internal topic naming must be enabled if streams optimizations enabled");
    new KsqlConfig(
        ImmutableMap.of(
            KsqlConfig.KSQL_USE_NAMED_INTERNAL_TOPICS,
            KsqlConfig.KSQL_USE_NAMED_INTERNAL_TOPICS_OFF,
            StreamsConfig.TOPOLOGY_OPTIMIZATION,
            StreamsConfig.OPTIMIZE)
    );
  }

  @Test
  public void shouldRaiseOnInvalidInternalTopicNamingValue() {
    expectedException.expect(ConfigException.class);
    new KsqlConfig(
        Collections.singletonMap(
            KsqlConfig.KSQL_USE_NAMED_INTERNAL_TOPICS,
            "foobar"
        )
    );
  }
}