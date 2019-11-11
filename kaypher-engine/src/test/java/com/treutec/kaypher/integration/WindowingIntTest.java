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
package com.treutec.kaypher.integration;

import static com.treutec.kaypher.serde.Format.JSON;
import static com.treutec.kaypher.test.util.AssertEventually.assertThatEventually;
import static com.treutec.kaypher.test.util.ConsumerTestUtil.hasUniqueRecords;
import static com.treutec.kaypher.test.util.MapMatchers.mapHasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.confluent.common.utils.IntegrationTest;
import com.treutec.kaypher.GenericRow;
import com.treutec.kaypher.metastore.model.DataSource;
import com.treutec.kaypher.name.SourceName;
import com.treutec.kaypher.schema.kaypher.PhysicalSchema;
import com.treutec.kaypher.services.KafkaTopicClient;
import com.treutec.kaypher.services.KafkaTopicClient.TopicCleanupPolicy;
import com.treutec.kaypher.test.util.KaypherIdentifierTestUtil;
import com.treutec.kaypher.test.util.TopicTestUtil;
import com.treutec.kaypher.util.OrderDataProvider;
import com.treutec.kaypher.util.QueryMetadata;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import kafka.zookeeper.ZooKeeperClientException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.streams.kstream.SessionWindowedDeserializer;
import org.apache.kafka.streams.kstream.TimeWindowedDeserializer;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.SessionWindow;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;

@Category({IntegrationTest.class})
public class WindowingIntTest {

  private static final String ORDERS_STREAM = "ORDERS";

  private static final StringDeserializer STRING_DESERIALIZER = new StringDeserializer();

  private static final TimeWindowedDeserializer<String> TIME_WINDOWED_DESERIALIZER =
      new TimeWindowedDeserializer<>(STRING_DESERIALIZER);

  private static final SessionWindowedDeserializer<String> SESSION_WINDOWED_DESERIALIZER =
      new SessionWindowedDeserializer<>(STRING_DESERIALIZER);

  private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(60);

  private final long batch0SentMs;
  private final long batch1Delay;
  private final long tenSecWindowStartMs;

  private static final IntegrationTestHarness TEST_HARNESS = IntegrationTestHarness.build();

  @ClassRule
  public static final RuleChain CLUSTER_WITH_RETRY = RuleChain
      .outerRule(Retry.of(3, ZooKeeperClientException.class, 3, TimeUnit.SECONDS))
      .around(TEST_HARNESS);

  @Rule
  public final TestKaypherContext kaypherContext = TEST_HARNESS.buildKaypherContext();

  private String sourceTopicName;
  private String resultStream0;
  private String resultStream1;
  private PhysicalSchema resultSchema;
  private Set<String> preExistingTopics;
  private KafkaTopicClient topicClient;

  public WindowingIntTest() {
    final long currentTimeMillis = System.currentTimeMillis();
    // set the batch to be in the middle of a ten second window
    batch0SentMs = currentTimeMillis - (currentTimeMillis % TimeUnit.SECONDS.toMillis(10)) + (5001);
    tenSecWindowStartMs = batch0SentMs - (batch0SentMs % TimeUnit.SECONDS.toMillis(10));
    batch1Delay = 500;
  }

  @Before
  public void before() {
    topicClient = kaypherContext.getServiceContext().getTopicClient();

    sourceTopicName = TopicTestUtil.uniqueTopicName("orders");
    resultStream0 = KaypherIdentifierTestUtil.uniqueIdentifierName("FIRST");
    resultStream1 = KaypherIdentifierTestUtil.uniqueIdentifierName("SECOND");

    TEST_HARNESS.ensureTopics(sourceTopicName, ORDERS_STREAM.toUpperCase());

    final OrderDataProvider dataProvider = new OrderDataProvider();
    TEST_HARNESS.produceRows(sourceTopicName, dataProvider, JSON, () -> batch0SentMs);
    TEST_HARNESS.produceRows(sourceTopicName, dataProvider, JSON, () -> batch0SentMs + batch1Delay);

    createOrdersStream();

    preExistingTopics = topicClient.listTopicNames();
  }

  @Test
  public void shouldAggregateWithNoWindow() {
    // Given:
    givenTable("CREATE TABLE %s AS "
        + "SELECT ITEMID, COUNT(ITEMID), SUM(ORDERUNITS), SUM(KEYVALUEMAP['key2']/2) "
        + "FROM " + ORDERS_STREAM + " WHERE ITEMID = 'ITEM_1' GROUP BY ITEMID;");

    final Map<String, GenericRow> expected = ImmutableMap.of(
        "ITEM_1",
        new GenericRow(ImmutableList.of("ITEM_1", 2, 20.0, 2.0))
    );

    // Then:
    assertOutputOf(resultStream0, expected, is(expected));
    assertTableCanBeUsedAsSource(expected, is(expected));
    assertTopicsCleanedUp(TopicCleanupPolicy.COMPACT);
  }

  @Test
  public void shouldAggregateTumblingWindow() {
    // Given:
    givenTable("CREATE TABLE %s AS "
        + "SELECT ITEMID, COUNT(ITEMID), SUM(ORDERUNITS), SUM(ORDERUNITS * 10)/COUNT(*) "
        + "FROM " + ORDERS_STREAM + " WINDOW TUMBLING (SIZE 10 SECONDS) "
        + "WHERE ITEMID = 'ITEM_1' GROUP BY ITEMID;");

    final Map<Windowed<String>, GenericRow> expected = ImmutableMap.of(
        new Windowed<>("ITEM_1", new TimeWindow(tenSecWindowStartMs, Long.MAX_VALUE)),
        new GenericRow(ImmutableList.of("ITEM_1", 2, 20.0, 100.0))
    );

    // Then:
    assertOutputOf(resultStream0, expected, is(expected));
    assertTableCanBeUsedAsSource(expected, is(expected));
    assertTopicsCleanedUp(TopicCleanupPolicy.DELETE);
  }

  @Test
  public void shouldAggregateHoppingWindow() {
    // Given:
    givenTable("CREATE TABLE %s AS "
        + "SELECT ITEMID, COUNT(ITEMID), SUM(ORDERUNITS), SUM(ORDERUNITS * 10) "
        + "FROM " + ORDERS_STREAM + " WINDOW HOPPING (SIZE 10 SECONDS, ADVANCE BY 5 SECONDS) "
        + "WHERE ITEMID = 'ITEM_1' GROUP BY ITEMID;");

    final long firstWindowStart = tenSecWindowStartMs;
    final long secondWindowStart = firstWindowStart + TimeUnit.SECONDS.toMillis(5);

    final Map<Windowed<String>, GenericRow> expected = ImmutableMap.of(
        new Windowed<>("ITEM_1", new TimeWindow(firstWindowStart, Long.MAX_VALUE)),
        new GenericRow(ImmutableList.of("ITEM_1", 2, 20.0, 200.0)),
        new Windowed<>("ITEM_1", new TimeWindow(secondWindowStart, Long.MAX_VALUE)),
        new GenericRow(ImmutableList.of("ITEM_1", 2, 20.0, 200.0))
    );

    // Then:
    assertOutputOf(resultStream0, expected, is(expected));
    assertTableCanBeUsedAsSource(expected, is(expected));
    assertTopicsCleanedUp(TopicCleanupPolicy.DELETE);
  }

  @Test
  public void shouldAggregateSessionWindow() {
    // Given:
    givenTable("CREATE TABLE %s AS "
        + "SELECT ORDERID, COUNT(*), SUM(ORDERUNITS) "
        + "FROM " + ORDERS_STREAM + " WINDOW SESSION (10 SECONDS) "
        + "GROUP BY ORDERID;");

    final long sessionEnd = batch0SentMs + batch1Delay;

    final Map<Windowed<String>, GenericRow> expected = ImmutableMap
        .<Windowed<String>, GenericRow>builder()
        .put(new Windowed<>("ORDER_1", new SessionWindow(batch0SentMs, sessionEnd)),
            new GenericRow(ImmutableList.of("ORDER_1", 2, 20.0)))
        .put(new Windowed<>("ORDER_2", new SessionWindow(batch0SentMs, sessionEnd)),
            new GenericRow(ImmutableList.of("ORDER_2", 2, 40.0)))
        .put(new Windowed<>("ORDER_3", new SessionWindow(batch0SentMs, sessionEnd)),
            new GenericRow(ImmutableList.of("ORDER_3", 2, 60.0)))
        .put(new Windowed<>("ORDER_4", new SessionWindow(batch0SentMs, sessionEnd)),
            new GenericRow(ImmutableList.of("ORDER_4", 2, 80.0)))
        .put(new Windowed<>("ORDER_5", new SessionWindow(batch0SentMs, sessionEnd)),
            new GenericRow(ImmutableList.of("ORDER_5", 2, 100.0)))
        .put(new Windowed<>("ORDER_6", new SessionWindow(batch0SentMs, sessionEnd)),
            new GenericRow(ImmutableList.of("ORDER_6", 6, 420.0)))
        .build();

    // Then:
    assertOutputOf(resultStream0, expected, mapHasItems(expected));
    assertTableCanBeUsedAsSource(expected, mapHasItems(expected));
    assertTopicsCleanedUp(TopicCleanupPolicy.DELETE);
  }

  private void givenTable(final String sql) {
    kaypherContext.sql(String.format(sql, resultStream0));
    final DataSource<?> source = kaypherContext.getMetaStore().getSource(SourceName.of(resultStream0));
    resultSchema = PhysicalSchema.from(
        source.getSchema(),
        source.getSerdeOptions()
    );
  }

  @SuppressWarnings("unchecked")
  private <K> void assertOutputOf(
      final String streamName,
      final Map<K, GenericRow> expected,
      final Matcher<? super Map<K, GenericRow>> tableRowMatcher
  ) {
    final Deserializer keyDeserializer = getKeyDeserializerFor(expected.keySet().iterator().next());

    TEST_HARNESS.verifyAvailableRows(
        streamName, hasUniqueRecords(tableRowMatcher), JSON, resultSchema, keyDeserializer,
        VERIFY_TIMEOUT);
  }

  private <K> void assertTableCanBeUsedAsSource(
      final Map<K, GenericRow> expected,
      final Matcher<? super Map<K, GenericRow>> tableRowMatcher
  ) {
    kaypherContext.sql("CREATE TABLE " + resultStream1 + " AS SELECT * FROM " + resultStream0 + ";");

    final DataSource<?> source = kaypherContext.getMetaStore().getSource(SourceName.of(resultStream1));

    resultSchema = PhysicalSchema.from(
        source.getSchema(),
        source.getSerdeOptions()
    );

    assertOutputOf(resultStream1, expected, tableRowMatcher);
  }

  private void assertTopicsCleanedUp(final TopicCleanupPolicy topicCleanupPolicy) {
    assertThat("Initial topics", getTopicNames(), hasSize(5));

    kaypherContext.getPersistentQueries().forEach(QueryMetadata::close);

    assertThatEventually("After cleanup", this::getTopicNames,
        containsInAnyOrder(resultStream0, resultStream1));

    assertThat(topicClient.getTopicCleanupPolicy(resultStream0),
        is(topicCleanupPolicy));
  }

  private Set<String> getTopicNames() {
    final Set<String> names = topicClient.listTopicNames();
    names.removeAll(preExistingTopics);
    return names;
  }

  private static Deserializer getKeyDeserializerFor(final Object key) {
    if (key instanceof Windowed) {
      if (((Windowed) key).window() instanceof SessionWindow) {
        return SESSION_WINDOWED_DESERIALIZER;
      }
      return TIME_WINDOWED_DESERIALIZER;
    }

    return STRING_DESERIALIZER;
  }

  private void createOrdersStream() {
    kaypherContext.sql("CREATE STREAM " + ORDERS_STREAM + " ("
        + "ORDERTIME bigint, "
        + "ORDERID varchar, "
        + "ITEMID varchar, "
        + "ORDERUNITS double, "
        + "PRICEARRAY array<double>, "
        + "KEYVALUEMAP map<varchar, double>) "
        + "WITH (kafka_topic='" + sourceTopicName + "', value_format='JSON', key='ordertime');");
  }
}
