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

package com.koneksys.kaypher.parser;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.koneksys.kaypher.ddl.DdlConfig;
import com.koneksys.kaypher.function.TestFunctionRegistry;
import com.koneksys.kaypher.metastore.MutableMetaStore;
import com.koneksys.kaypher.metastore.model.KaypherStream;
import com.koneksys.kaypher.metastore.model.KaypherTable;
import com.koneksys.kaypher.metastore.model.KaypherTopic;
import com.koneksys.kaypher.parser.KaypherParser.PreparedStatement;
import com.koneksys.kaypher.parser.exception.ParseFailedException;
import com.koneksys.kaypher.parser.tree.AliasedRelation;
import com.koneksys.kaypher.parser.tree.ArithmeticUnaryExpression;
import com.koneksys.kaypher.parser.tree.ComparisonExpression;
import com.koneksys.kaypher.parser.tree.CreateStream;
import com.koneksys.kaypher.parser.tree.CreateStreamAsSelect;
import com.koneksys.kaypher.parser.tree.CreateTable;
import com.koneksys.kaypher.parser.tree.DropStream;
import com.koneksys.kaypher.parser.tree.DropTable;
import com.koneksys.kaypher.parser.tree.Expression;
import com.koneksys.kaypher.parser.tree.FunctionCall;
import com.koneksys.kaypher.parser.tree.InsertInto;
import com.koneksys.kaypher.parser.tree.IntegerLiteral;
import com.koneksys.kaypher.parser.tree.Join;
import com.koneksys.kaypher.parser.tree.ListProperties;
import com.koneksys.kaypher.parser.tree.ListQueries;
import com.koneksys.kaypher.parser.tree.ListStreams;
import com.koneksys.kaypher.parser.tree.ListTables;
import com.koneksys.kaypher.parser.tree.ListTopics;
import com.koneksys.kaypher.parser.tree.Literal;
import com.koneksys.kaypher.parser.tree.LongLiteral;
import com.koneksys.kaypher.parser.tree.Query;
import com.koneksys.kaypher.parser.tree.RegisterTopic;
import com.koneksys.kaypher.parser.tree.SearchedCaseExpression;
import com.koneksys.kaypher.parser.tree.SelectItem;
import com.koneksys.kaypher.parser.tree.SetProperty;
import com.koneksys.kaypher.parser.tree.SingleColumn;
import com.koneksys.kaypher.parser.tree.Statement;
import com.koneksys.kaypher.parser.tree.Struct;
import com.koneksys.kaypher.parser.tree.Type.SqlType;
import com.koneksys.kaypher.parser.tree.WithinExpression;
import com.koneksys.kaypher.serde.json.KaypherJsonTopicSerDe;
import com.koneksys.kaypher.util.KaypherException;
import com.koneksys.kaypher.util.MetaStoreFixture;
import com.koneksys.kaypher.util.timestamp.MetadataTimestampExtractionPolicy;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;


public class KaypherParserTest {

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  private MutableMetaStore metaStore;


  @Before
  public void init() {

    metaStore = MetaStoreFixture.getNewMetaStore(new TestFunctionRegistry());

    final Schema addressSchema = SchemaBuilder.struct()
        .field("NUMBER", Schema.OPTIONAL_INT64_SCHEMA)
        .field("STREET", Schema.OPTIONAL_STRING_SCHEMA)
        .field("CITY", Schema.OPTIONAL_STRING_SCHEMA)
        .field("STATE", Schema.OPTIONAL_STRING_SCHEMA)
        .field("ZIPCODE", Schema.OPTIONAL_INT64_SCHEMA)
        .optional().build();

    final Schema categorySchema = SchemaBuilder.struct()
        .field("ID", Schema.OPTIONAL_INT64_SCHEMA)
        .field("NAME", Schema.OPTIONAL_STRING_SCHEMA)
        .optional().build();

    final Schema itemInfoSchema = SchemaBuilder.struct()
        .field("ITEMID", Schema.INT64_SCHEMA)
        .field("NAME", Schema.STRING_SCHEMA)
        .field("CATEGORY", categorySchema)
        .optional().build();

    final SchemaBuilder schemaBuilder = SchemaBuilder.struct();
    final Schema schemaBuilderOrders = schemaBuilder
        .field("ORDERTIME", Schema.INT64_SCHEMA)
        .field("ORDERID", Schema.OPTIONAL_INT64_SCHEMA)
        .field("ITEMID", Schema.OPTIONAL_STRING_SCHEMA)
        .field("ITEMINFO", itemInfoSchema)
        .field("ORDERUNITS", Schema.INT32_SCHEMA)
        .field("ARRAYCOL",SchemaBuilder.array(Schema.FLOAT64_SCHEMA).optional().build())
        .field("MAPCOL", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.FLOAT64_SCHEMA).optional().build())
        .field("ADDRESS", addressSchema)
        .build();

    final KaypherTopic
        kaypherTopicOrders =
        new KaypherTopic("ADDRESS_TOPIC", "orders_topic", new KaypherJsonTopicSerDe(), false);

    final KaypherStream kaypherStreamOrders = new KaypherStream<>(
        "sqlexpression",
        "ADDRESS",
        schemaBuilderOrders,
        Optional.of(schemaBuilderOrders.field("ORDERTIME")),
        new MetadataTimestampExtractionPolicy(),
        kaypherTopicOrders,
        Serdes::String);

    metaStore.putTopic(kaypherTopicOrders);
    metaStore.putSource(kaypherStreamOrders);

    final KaypherTopic
        kaypherTopicItems =
        new KaypherTopic("ITEMS_TOPIC", "item_topic", new KaypherJsonTopicSerDe(), false);
    final KaypherTable<String> kaypherTableOrders = new KaypherTable<>(
        "sqlexpression",
        "ITEMID",
        itemInfoSchema,
        Optional.ofNullable(itemInfoSchema.field("ITEMID")),
        new MetadataTimestampExtractionPolicy(),
        kaypherTopicItems,
        Serdes::String);
    metaStore.putTopic(kaypherTopicItems);
    metaStore.putSource(kaypherTableOrders);
  }

  @Test
  public void testSimpleQuery() {
    final String simpleQuery = "SELECT col0, col2, col3 FROM test1 WHERE col0 > 100;";
    final PreparedStatement<?> statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore);

    assertThat(statement.getStatementText(), is(simpleQuery));
    Assert.assertTrue("testSimpleQuery fails", statement.getStatement() instanceof Query);
    final Query query = (Query) statement.getStatement();
    Assert.assertTrue("testSimpleQuery fails", query.getSelect().getSelectItems().size() == 3);
    assertThat(query.getFrom(), not(nullValue()));
    Assert.assertTrue("testSimpleQuery fails", query.getWhere().isPresent());
    Assert.assertTrue("testSimpleQuery fails", query.getWhere().get() instanceof ComparisonExpression);
    final ComparisonExpression comparisonExpression = (ComparisonExpression)query.getWhere().get();
    Assert.assertTrue("testSimpleQuery fails", comparisonExpression.getType().getValue().equalsIgnoreCase(">"));

  }

  @Test
  public void testProjection() {
    final String queryStr = "SELECT col0, col2, col3 FROM test1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testProjection fails", statement instanceof Query);
    final Query query = (Query) statement;
    Assert.assertTrue("testProjection fails", query.getSelect().getSelectItems().size() == 3);
    Assert.assertTrue("testProjection fails", query.getSelect().getSelectItems().get(0) instanceof SingleColumn);
    final SingleColumn column0 = (SingleColumn)query.getSelect().getSelectItems().get(0);
    Assert.assertTrue("testProjection fails", column0.getAlias().get().equalsIgnoreCase("COL0"));
    Assert.assertTrue("testProjection fails", column0.getExpression().toString().equalsIgnoreCase("TEST1.COL0"));
  }

  @Test
  public void testProjectionWithArrayMap() {
    final String queryStr = "SELECT col0, col2, col3, col4[0], col5['key1'] FROM test1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testProjectionWithArrayMap fails", statement instanceof Query);
    final Query query = (Query) statement;
    Assert.assertTrue("testProjectionWithArrayMap fails", query.getSelect().getSelectItems()
                                                  .size() == 5);
    Assert.assertTrue("testProjectionWithArrayMap fails", query.getSelect().getSelectItems().get(0) instanceof SingleColumn);
    final SingleColumn column0 = (SingleColumn)query.getSelect().getSelectItems().get(0);
    Assert.assertTrue("testProjectionWithArrayMap fails", column0.getAlias().get().equalsIgnoreCase("COL0"));
    Assert.assertTrue("testProjectionWithArrayMap fails", column0.getExpression().toString().equalsIgnoreCase("TEST1.COL0"));

    final SingleColumn column3 = (SingleColumn)query.getSelect().getSelectItems().get(3);
    final SingleColumn column4 = (SingleColumn)query.getSelect().getSelectItems().get(4);
    Assert.assertTrue("testProjectionWithArrayMap fails", column3.getExpression().toString()
        .equalsIgnoreCase("TEST1.COL4[0]"));
    Assert.assertTrue("testProjectionWithArrayMap fails", column4.getExpression().toString()
        .equalsIgnoreCase("TEST1.COL5['key1']"));
  }

  @Test
  public void testProjectFilter() {
    final String queryStr = "SELECT col0, col2, col3 FROM test1 WHERE col0 > 100;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testSimpleQuery fails", statement instanceof Query);
    final Query query = (Query) statement;

    Assert.assertTrue("testProjectFilter fails", query.getWhere().get() instanceof ComparisonExpression);
    final ComparisonExpression comparisonExpression = (ComparisonExpression)query.getWhere().get();
    Assert.assertTrue("testProjectFilter fails", comparisonExpression.toString().equalsIgnoreCase("(TEST1.COL0 > 100)"));
    Assert.assertTrue("testProjectFilter fails", query.getSelect().getSelectItems().size() == 3);

  }

  @Test
  public void testBinaryExpression() {
    final String queryStr = "SELECT col0+10, col2, col3-col1 FROM test1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testBinaryExpression fails", statement instanceof Query);
    final Query query = (Query) statement;
    final SingleColumn column0 = (SingleColumn)query.getSelect().getSelectItems().get(0);
    Assert.assertTrue("testBinaryExpression fails", column0.getAlias().get().equalsIgnoreCase("KSQL_COL_0"));
    Assert.assertTrue("testBinaryExpression fails", column0.getExpression().toString().equalsIgnoreCase("(TEST1.COL0 + 10)"));
  }

  @Test
  public void testBooleanExpression() {
    final String queryStr = "SELECT col0 = 10, col2, col3 > col1 FROM test1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testBooleanExpression fails", statement instanceof Query);
    final Query query = (Query) statement;
    final SingleColumn column0 = (SingleColumn)query.getSelect().getSelectItems().get(0);
    Assert.assertTrue("testBooleanExpression fails", column0.getAlias().get().equalsIgnoreCase("KSQL_COL_0"));
    Assert.assertTrue("testBooleanExpression fails", column0.getExpression().toString().equalsIgnoreCase("(TEST1.COL0 = 10)"));
  }

  @Test
  public void testLiterals() {
    final String queryStr = "SELECT 10, col2, 'test', 2.5, true, -5 FROM test1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testLiterals fails", statement instanceof Query);
    final Query query = (Query) statement;
    final SingleColumn column0 = (SingleColumn)query.getSelect().getSelectItems().get(0);
    Assert.assertTrue("testLiterals fails", column0.getAlias().get().equalsIgnoreCase("KSQL_COL_0"));
    Assert.assertTrue("testLiterals fails", column0.getExpression().toString().equalsIgnoreCase("10"));

    final SingleColumn column1 = (SingleColumn)query.getSelect().getSelectItems().get(1);
    Assert.assertTrue("testLiterals fails", column1.getAlias().get().equalsIgnoreCase("COL2"));
    Assert.assertTrue("testLiterals fails", column1.getExpression().toString().equalsIgnoreCase("TEST1.COL2"));

    final SingleColumn column2 = (SingleColumn)query.getSelect().getSelectItems().get(2);
    Assert.assertTrue("testLiterals fails", column2.getAlias().get().equalsIgnoreCase("KSQL_COL_2"));
    Assert.assertTrue("testLiterals fails", column2.getExpression().toString().equalsIgnoreCase("'test'"));

    final SingleColumn column3 = (SingleColumn)query.getSelect().getSelectItems().get(3);
    Assert.assertTrue("testLiterals fails", column3.getAlias().get().equalsIgnoreCase("KSQL_COL_3"));
    Assert.assertTrue("testLiterals fails", column3.getExpression().toString().equalsIgnoreCase("2.5"));

    final SingleColumn column4 = (SingleColumn)query.getSelect().getSelectItems().get(4);
    Assert.assertTrue("testLiterals fails", column4.getAlias().get().equalsIgnoreCase("KSQL_COL_4"));
    Assert.assertTrue("testLiterals fails", column4.getExpression().toString().equalsIgnoreCase("true"));

    final SingleColumn column5 = (SingleColumn)query.getSelect().getSelectItems().get(5);
    Assert.assertTrue("testLiterals fails", column5.getAlias().get().equalsIgnoreCase("KSQL_COL_5"));
    Assert.assertTrue("testLiterals fails", column5.getExpression().toString().equalsIgnoreCase("-5"));
  }

  private <T, L extends Literal> void shouldParseNumericLiteral(final T value,
                                                                final L expectedValue) {
    final String queryStr = String.format("SELECT " + value.toString() + " FROM test1;", value);
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    assertThat(statement, instanceOf(Query.class));
    final Query query = (Query) statement;
    final SingleColumn column0
        = (SingleColumn) query.getSelect().getSelectItems().get(0);
    assertThat(column0.getAlias().get(), equalTo("KSQL_COL_0"));
    assertThat(column0.getExpression(), instanceOf(expectedValue.getClass()));
    assertThat(column0.getExpression(), equalTo(expectedValue));
  }

  @Test
  public void shouldParseIntegerLiterals() {
    shouldParseNumericLiteral(0, new IntegerLiteral(0));
    shouldParseNumericLiteral(10, new IntegerLiteral(10));
    shouldParseNumericLiteral(Integer.MAX_VALUE, new IntegerLiteral(Integer.MAX_VALUE));
  }

  @Test
  public void shouldParseLongLiterals() {
    shouldParseNumericLiteral(Integer.MAX_VALUE + 100L, new LongLiteral(Integer.MAX_VALUE + 100L));
  }

  @Test
  public void shouldParseNegativeInteger() {
    final String queryStr = String.format("SELECT -12345 FROM test1;");
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    assertThat(statement, instanceOf(Query.class));
    final Query query = (Query) statement;
    final SingleColumn column0
        = (SingleColumn) query.getSelect().getSelectItems().get(0);
    assertThat(column0.getAlias().get(), equalTo("KSQL_COL_0"));
    assertThat(column0.getExpression(), instanceOf(ArithmeticUnaryExpression.class));
    final ArithmeticUnaryExpression aue = (ArithmeticUnaryExpression) column0.getExpression();
    assertThat(aue.getValue(), instanceOf(IntegerLiteral.class));
    assertThat(((IntegerLiteral) aue.getValue()).getValue(), equalTo(12345));
    assertThat(aue.getSign(), equalTo(ArithmeticUnaryExpression.Sign.MINUS));
  }

  @Test
  public void testBooleanLogicalExpression() {
    final String
        queryStr =
        "SELECT 10, col2, 'test', 2.5, true, -5 FROM test1 WHERE col1 = 10 AND col2 LIKE 'val' OR col4 > 2.6 ;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testSimpleQuery fails", statement instanceof Query);
    final Query query = (Query) statement;
    final SingleColumn column0 = (SingleColumn)query.getSelect().getSelectItems().get(0);
    Assert.assertTrue("testProjection fails", column0.getAlias().get().equalsIgnoreCase("KSQL_COL_0"));
    Assert.assertTrue("testProjection fails", column0.getExpression().toString().equalsIgnoreCase("10"));

    final SingleColumn column1 = (SingleColumn)query.getSelect().getSelectItems().get(1);
    Assert.assertTrue("testProjection fails", column1.getAlias().get().equalsIgnoreCase("COL2"));
    Assert.assertTrue("testProjection fails", column1.getExpression().toString().equalsIgnoreCase("TEST1.COL2"));

    final SingleColumn column2 = (SingleColumn)query.getSelect().getSelectItems().get(2);
    Assert.assertTrue("testProjection fails", column2.getAlias().get().equalsIgnoreCase("KSQL_COL_2"));
    Assert.assertTrue("testProjection fails", column2.getExpression().toString().equalsIgnoreCase("'test'"));

  }

  @Test
  public void shouldParseStructFieldAccessCorrectly() {
    final String simpleQuery = "SELECT iteminfo->category->name, address->street FROM orders WHERE address->state = 'CA';";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();


    Assert.assertTrue("testSimpleQuery fails", statement instanceof Query);
    final Query query = (Query) statement;
    assertThat("testSimpleQuery fails", query.getSelect().getSelectItems().size(), equalTo(2));
    final SingleColumn singleColumn0 = (SingleColumn) query.getSelect().getSelectItems().get(0);
    final SingleColumn singleColumn1 = (SingleColumn) query.getSelect().getSelectItems().get(1);
    assertThat(singleColumn0.getExpression(), instanceOf(FunctionCall.class));
    final FunctionCall functionCall0 = (FunctionCall) singleColumn0.getExpression();
    assertThat(functionCall0.toString(), equalTo("FETCH_FIELD_FROM_STRUCT(FETCH_FIELD_FROM_STRUCT(ORDERS.ITEMINFO, 'CATEGORY'), 'NAME')"));

    final FunctionCall functionCall1 = (FunctionCall) singleColumn1.getExpression();
    assertThat(functionCall1.toString(), equalTo("FETCH_FIELD_FROM_STRUCT(ORDERS.ADDRESS, 'STREET')"));

  }

  @Test
  public void testSimpleLeftJoin() {
    final String
        queryStr =
        "SELECT t1.col1, t2.col1, t2.col4, col5, t2.col2 FROM test1 t1 LEFT JOIN test2 t2 ON "
        + "t1.col1 = t2.col1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testSimpleQuery fails", statement instanceof Query);
    final Query query = (Query) statement;
    Assert.assertTrue("testSimpleLeftJoin fails", query.getFrom() instanceof Join);
    final Join join = (Join) query.getFrom();
    Assert.assertTrue("testSimpleLeftJoin fails", join.getType().toString().equalsIgnoreCase("LEFT"));

    Assert.assertTrue("testSimpleLeftJoin fails", ((AliasedRelation)join.getLeft()).getAlias().equalsIgnoreCase("T1"));
    Assert.assertTrue("testSimpleLeftJoin fails", ((AliasedRelation)join.getRight()).getAlias().equalsIgnoreCase("T2"));

  }

  @Test
  public void testLeftJoinWithFilter() {
    final String
        queryStr =
        "SELECT t1.col1, t2.col1, t2.col4, t2.col2 FROM test1 t1 LEFT JOIN test2 t2 ON t1.col1 = "
        + "t2.col1 WHERE t2.col2 = 'test';";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testSimpleQuery fails", statement instanceof Query);
    final Query query = (Query) statement;
    Assert.assertTrue("testLeftJoinWithFilter fails", query.getFrom() instanceof Join);
    final Join join = (Join) query.getFrom();
    Assert.assertTrue("testLeftJoinWithFilter fails", join.getType().toString().equalsIgnoreCase("LEFT"));

    Assert.assertTrue("testLeftJoinWithFilter fails", ((AliasedRelation)join.getLeft()).getAlias().equalsIgnoreCase("T1"));
    Assert.assertTrue("testLeftJoinWithFilter fails", ((AliasedRelation)join.getRight()).getAlias().equalsIgnoreCase("T2"));

    Assert.assertTrue("testLeftJoinWithFilter fails", query.getWhere().get().toString().equalsIgnoreCase("(T2.COL2 = 'test')"));
  }

  @Test
  public void testSelectAll() {
    final String queryStr = "SELECT * FROM test1 t1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testSelectAll fails", statement instanceof Query);
    final Query query = (Query) statement;
    Assert.assertTrue("testSelectAll fails", query.getSelect().getSelectItems()
                                                 .size() == 8);
  }

  @Test
  public void testReservedColumnIdentifers() {
    assertQuerySucceeds("SELECT ROWTIME as ROWTIME FROM test1 t1;");
    assertQuerySucceeds("SELECT ROWKEY as ROWKEY FROM test1 t1;");
  }

  @Test
  public void testReservedRowTimeAlias() {
    expectedException.expect(ParseFailedException.class);
    expectedException.expectMessage(containsString(
        "ROWTIME is a reserved token for implicit column. You cannot use it as an alias for a column."));

    KaypherParserTestUtil.buildSingleAst("SELECT C1 as ROWTIME FROM test1 t1;", metaStore);
  }

  @Test
  public void testReservedRowKeyAlias() {
    expectedException.expect(ParseFailedException.class);
    expectedException.expectMessage(containsString(
        "ROWKEY is a reserved token for implicit column. You cannot use it as an alias for a column."));

    KaypherParserTestUtil.buildSingleAst("SELECT C2 as ROWKEY FROM test1 t1;", metaStore);
  }

  @Test
  public void testSelectAllJoin() {
    final String
        queryStr =
        "SELECT * FROM test1 t1 LEFT JOIN test2 t2 ON t1.col1 = t2.col1 WHERE t2.col2 = 'test';";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testSimpleQuery fails", statement instanceof Query);
    final Query query = (Query) statement;
    Assert.assertTrue("testSelectAllJoin fails", query.getFrom() instanceof Join);
    final Join join = (Join) query.getFrom();
    Assert.assertTrue("testSelectAllJoin fails", query.getSelect().getSelectItems
        ().size() == 15);
    Assert.assertTrue("testLeftJoinWithFilter fails", ((AliasedRelation)join.getLeft()).getAlias().equalsIgnoreCase("T1"));
    Assert.assertTrue("testLeftJoinWithFilter fails", ((AliasedRelation)join.getRight()).getAlias().equalsIgnoreCase("T2"));
  }

  @Test
  public void testUDF() {
    final String queryStr = "SELECT lcase(col1), concat(col2,'hello'), floor(abs(col3)) FROM test1 t1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testSelectAll fails", statement instanceof Query);
    final Query query = (Query) statement;

    final SingleColumn column0 = (SingleColumn)query.getSelect().getSelectItems().get(0);
    Assert.assertTrue("testProjection fails", column0.getAlias().get().equalsIgnoreCase("KSQL_COL_0"));
    Assert.assertTrue("testProjection fails", column0.getExpression().toString().equalsIgnoreCase("LCASE(T1.COL1)"));

    final SingleColumn column1 = (SingleColumn)query.getSelect().getSelectItems().get(1);
    Assert.assertTrue("testProjection fails", column1.getAlias().get().equalsIgnoreCase("KSQL_COL_1"));
    Assert.assertTrue("testProjection fails", column1.getExpression().toString().equalsIgnoreCase("CONCAT(T1.COL2, 'hello')"));

    final SingleColumn column2 = (SingleColumn)query.getSelect().getSelectItems().get(2);
    Assert.assertTrue("testProjection fails", column2.getAlias().get().equalsIgnoreCase("KSQL_COL_2"));
    Assert.assertTrue("testProjection fails", column2.getExpression().toString().equalsIgnoreCase("FLOOR(ABS(T1.COL3))"));
  }

  @Test
  public void testRegisterTopic() {
    final String
        queryStr =
        "REGISTER TOPIC orders_topic WITH (value_format = 'avro',kafka_topic='orders_topic');";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testRegisterTopic failed.", statement instanceof RegisterTopic);
    final RegisterTopic registerTopic = (RegisterTopic)statement;
    Assert.assertTrue("testRegisterTopic failed.", registerTopic
        .getName().toString().equalsIgnoreCase("ORDERS_TOPIC"));
    Assert.assertTrue("testRegisterTopic failed.", registerTopic.getProperties().size() == 2);
    Assert.assertTrue("testRegisterTopic failed.", registerTopic.getProperties().get(DdlConfig.VALUE_FORMAT_PROPERTY).toString().equalsIgnoreCase("'avro'"));
  }

  @Test
  public void testCreateStreamWithTopic() {
    final String
        queryStr =
        "CREATE STREAM orders (ordertime bigint, orderid varchar, itemid varchar, orderunits "
        + "double) WITH (registered_topic = 'orders_topic' , key='ordertime');";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testCreateStream failed.", statement instanceof CreateStream);
    final CreateStream createStream = (CreateStream)statement;
    Assert.assertTrue("testCreateStream failed.", createStream.getName().toString().equalsIgnoreCase("ORDERS"));
    Assert.assertTrue("testCreateStream failed.", createStream.getElements().size() == 4);
    Assert.assertTrue("testCreateStream failed.", createStream.getElements().get(0).getName().toString().equalsIgnoreCase("ordertime"));
    Assert.assertTrue("testCreateStream failed.", createStream.getProperties().get(DdlConfig.TOPIC_NAME_PROPERTY).toString().equalsIgnoreCase("'orders_topic'"));
  }

  @Test
  public void testCreateStreamWithTopicWithStruct() {
    final String
        queryStr =
        "CREATE STREAM orders (ordertime bigint, orderid varchar, itemid varchar, orderunits "
        + "double, arraycol array<double>, mapcol map<varchar, double>, "
        + "order_address STRUCT< number VARCHAR, street VARCHAR, zip INTEGER, city "
        + "VARCHAR, state VARCHAR >) WITH (registered_topic = 'orders_topic' , key='ordertime');";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testCreateStream failed.", statement instanceof CreateStream);
    final CreateStream createStream = (CreateStream)statement;
    assertThat(createStream.getName().toString().toUpperCase(), equalTo("ORDERS"));
    assertThat(createStream.getElements().size(), equalTo(7));
    assertThat(createStream.getElements().get(0).getName().toString().toLowerCase(), equalTo("ordertime"));
    assertThat(createStream.getElements().get(6).getType().getSqlType(), equalTo(SqlType.STRUCT));
    final Struct struct = (Struct) createStream.getElements().get(6).getType();
    assertThat(struct.getFields(), hasSize(5));
    assertThat(struct.getFields().get(0).getType().getSqlType(), equalTo(SqlType.STRING));
    assertThat(createStream.getProperties().get(DdlConfig.TOPIC_NAME_PROPERTY).toString().toLowerCase(),
               equalTo("'orders_topic'"));
  }

  @Test
  public void testCreateStream() {
    final String
        queryStr =
        "CREATE STREAM orders (ordertime bigint, orderid varchar, itemid varchar, orderunits "
        + "double) WITH (value_format = 'avro', kafka_topic='orders_topic');";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testCreateStream failed.", statement instanceof CreateStream);
    final CreateStream createStream = (CreateStream)statement;
    Assert.assertTrue("testCreateStream failed.", createStream.getName().toString().equalsIgnoreCase("ORDERS"));
    Assert.assertTrue("testCreateStream failed.", createStream.getElements().size() == 4);
    Assert.assertTrue("testCreateStream failed.", createStream.getElements().get(0).getName().toString().equalsIgnoreCase("ordertime"));
    Assert.assertTrue("testCreateStream failed.", createStream.getProperties().get(DdlConfig.KAFKA_TOPIC_NAME_PROPERTY).toString().equalsIgnoreCase("'orders_topic'"));
    Assert.assertTrue("testCreateStream failed.", createStream.getProperties().get(DdlConfig
                                                                                       .VALUE_FORMAT_PROPERTY).toString().equalsIgnoreCase("'avro'"));
  }

  @Test
  public void testCreateTableWithTopic() {
    final String
        queryStr =
        "CREATE TABLE users (usertime bigint, userid varchar, regionid varchar, gender varchar) WITH (registered_topic = 'users_topic', key='userid', statestore='user_statestore');";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testRegisterTopic failed.", statement instanceof CreateTable);
    final CreateTable createTable = (CreateTable)statement;
    Assert.assertTrue("testCreateTable failed.", createTable.getName().toString().equalsIgnoreCase("USERS"));
    Assert.assertTrue("testCreateTable failed.", createTable.getElements().size() == 4);
    Assert.assertTrue("testCreateTable failed.", createTable.getElements().get(0).getName().toString().equalsIgnoreCase("usertime"));
    Assert.assertTrue("testCreateTable failed.", createTable.getProperties().get(DdlConfig.TOPIC_NAME_PROPERTY).toString().equalsIgnoreCase("'users_topic'"));
  }

  @Test
  public void testCreateTable() {
    final String
        queryStr =
        "CREATE TABLE users (usertime bigint, userid varchar, regionid varchar, gender varchar) "
        + "WITH (kafka_topic = 'users_topic', value_format='json', key = 'userid');";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testRegisterTopic failed.", statement instanceof CreateTable);
    final CreateTable createTable = (CreateTable)statement;
    Assert.assertTrue("testCreateTable failed.", createTable.getName().toString().equalsIgnoreCase("USERS"));
    Assert.assertTrue("testCreateTable failed.", createTable.getElements().size() == 4);
    Assert.assertTrue("testCreateTable failed.", createTable.getElements().get(0).getName().toString().equalsIgnoreCase("usertime"));
    Assert.assertTrue("testCreateTable failed.", createTable.getProperties().get(DdlConfig.KAFKA_TOPIC_NAME_PROPERTY)
        .toString().equalsIgnoreCase("'users_topic'"));
    Assert.assertTrue("testCreateTable failed.", createTable.getProperties().get(DdlConfig.VALUE_FORMAT_PROPERTY)
        .toString().equalsIgnoreCase("'json'"));
  }

  @Test
  public void testCreateStreamAsSelect() {
    final String queryStr =
        "CREATE STREAM bigorders_json WITH (value_format = 'json', "
        + "kafka_topic='bigorders_topic') AS SELECT * FROM orders WHERE orderunits > 5 ;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    assertThat( statement, instanceOf(CreateStreamAsSelect.class));
    final CreateStreamAsSelect createStreamAsSelect = (CreateStreamAsSelect)statement;
    assertThat(createStreamAsSelect.getName().toString().toLowerCase(), equalTo("bigorders_json"));
    final Query query = createStreamAsSelect.getQuery();
    assertThat(query.getSelect().getSelectItems().size(), equalTo(8));
    assertThat(query.getWhere().get().toString().toUpperCase(), equalTo("(ORDERS.ORDERUNITS > 5)"));
    assertThat(((AliasedRelation)query.getFrom()).getAlias().toUpperCase(), equalTo("ORDERS"));
  }

  @Test
  /*
      TODO: Handle so-called identifier expressions as values in table properties (right now, the lack of single quotes
      around in the variables <format> and <kafkaTopic> cause things to break).
   */
  @Ignore
  public void testCreateTopicFormatWithoutQuotes() {
    final String kaypherTopic = "unquoted_topic";
    final String format = "json";
    final String kafkaTopic = "case_insensitive_kafka_topic";

    final String queryStr = format(
        "REGISTER TOPIC %s WITH (value_format = %s, kafka_topic = %s);",
        kaypherTopic,
        format,
        kafkaTopic
    );
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue(statement instanceof RegisterTopic);
    final RegisterTopic registerTopic = (RegisterTopic) statement;
    Assert.assertTrue(registerTopic.getName().toString().equalsIgnoreCase(kaypherTopic));
    Assert.assertTrue(registerTopic.getProperties().size() == 2);
    Assert.assertTrue(registerTopic
                          .getProperties().get(DdlConfig.VALUE_FORMAT_PROPERTY).toString().equalsIgnoreCase(format));
    Assert.assertTrue(registerTopic.getProperties().get(DdlConfig.KAFKA_TOPIC_NAME_PROPERTY).toString().equalsIgnoreCase(kafkaTopic));
  }

  @Test
  public void testShouldFailIfWrongKeyword() {
    try {
      final String simpleQuery = "SELLECT col0, col2, col3 FROM test1 WHERE col0 > 100;";
      KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore);
      fail(format("Expected query: %s to fail", simpleQuery));
    } catch (final ParseFailedException e) {
      final String errorMessage = e.getMessage();
      Assert.assertTrue(errorMessage.toLowerCase().contains(("line 1:1: mismatched input 'SELLECT'" + " expecting").toLowerCase()));
    }
  }

  @Test
  public void testSelectTumblingWindow() {

    final String
        queryStr =
        "select itemid, sum(orderunits) from orders window TUMBLING ( size 30 second) where orderunits > 5 group by itemid;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testSelectTumblingWindow failed.", statement instanceof Query);
    final Query query = (Query) statement;
    Assert.assertTrue("testCreateTable failed.", query.getSelect().getSelectItems
        ().size() == 2);
    Assert.assertTrue("testSelectTumblingWindow failed.", query.getWhere().get().toString().equalsIgnoreCase("(ORDERS.ORDERUNITS > 5)"));
    Assert.assertTrue("testSelectTumblingWindow failed.", ((AliasedRelation)query.getFrom()).getAlias().equalsIgnoreCase("ORDERS"));
    Assert.assertTrue("testSelectTumblingWindow failed.", query
                                                               .getWindow().isPresent());
    Assert.assertTrue("testSelectTumblingWindow failed.", query
        .getWindow().get().toString().equalsIgnoreCase(" WINDOW STREAMWINDOW  TUMBLING ( SIZE 30 SECONDS ) "));
  }

  @Test
  public void testSelectHoppingWindow() {

    final String
        queryStr =
        "select itemid, sum(orderunits) from orders window HOPPING ( size 30 second, advance by 5"
        + " seconds) "
        + "where "
        + "orderunits"
        + " > 5 group by itemid;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    assertThat(statement, instanceOf(Query.class));
    final Query query = (Query) statement;
    assertThat(query.getSelect().getSelectItems().size(), equalTo(2));
    assertThat(query.getWhere().get().toString(), equalTo("(ORDERS.ORDERUNITS > 5)"));
    assertThat(((AliasedRelation)query.getFrom()).getAlias().toUpperCase(), equalTo("ORDERS"));
    Assert.assertTrue("window expression isn't present", query
        .getWindow().isPresent());
    assertThat(query.getWindow().get().toString().toUpperCase(),
        equalTo(" WINDOW STREAMWINDOW  HOPPING ( SIZE 30 SECONDS , ADVANCE BY 5 SECONDS ) "));
  }

  @Test
  public void testSelectSessionWindow() {

    final String
        queryStr =
        "select itemid, sum(orderunits) from orders window SESSION ( 30 second) where "
        + "orderunits > 5 group by itemid;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(queryStr, metaStore).getStatement();
    Assert.assertTrue("testSelectSessionWindow failed.", statement instanceof Query);
    final Query query = (Query) statement;
    Assert.assertTrue("testCreateTable failed.", query.getSelect().getSelectItems
        ().size() == 2);
    Assert.assertTrue("testSelectSessionWindow failed.", query.getWhere().get().toString().equalsIgnoreCase("(ORDERS.ORDERUNITS > 5)"));
    Assert.assertTrue("testSelectSessionWindow failed.", ((AliasedRelation)query.getFrom()).getAlias().equalsIgnoreCase("ORDERS"));
    Assert.assertTrue("testSelectSessionWindow failed.", query
        .getWindow().isPresent());
    Assert.assertTrue("testSelectSessionWindow failed.", query
        .getWindow().get().toString().equalsIgnoreCase(" WINDOW STREAMWINDOW  SESSION "
                                                                 + "( 30 SECONDS ) "));
  }

  @Test
  public void testShowTopics() {
    final String simpleQuery = "SHOW TOPICS;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();
    Assert.assertTrue(statement instanceof ListTopics);
    final ListTopics listTopics = (ListTopics) statement;
    Assert.assertTrue(listTopics.toString().equalsIgnoreCase("ListTopics{}"));
  }

  @Test
  public void testShowStreams() {
    final String simpleQuery = "SHOW STREAMS;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();
    Assert.assertTrue(statement instanceof ListStreams);
    final ListStreams listStreams = (ListStreams) statement;
    assertThat(listStreams.toString(), is("ListStreams{showExtended=false}"));
    Assert.assertThat(listStreams.getShowExtended(), is(false));
  }

  @Test
  public void testShowTables() {
    final String simpleQuery = "SHOW TABLES;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();
    Assert.assertTrue(statement instanceof ListTables);
    final ListTables listTables = (ListTables) statement;
    assertThat(listTables.toString(), is("ListTables{showExtended=false}"));
    Assert.assertThat(listTables.getShowExtended(), is(false));
  }

  @Test
  public void shouldReturnListQueriesForShowQueries() {
    final String statementString = "SHOW QUERIES;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    Assert.assertThat(statement, instanceOf(ListQueries.class));
    final ListQueries listQueries = (ListQueries)statement;
    Assert.assertThat(listQueries.getShowExtended(), is(false));
  }

  @Test
  public void testShowProperties() {
    final String simpleQuery = "SHOW PROPERTIES;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();
    Assert.assertTrue(statement instanceof ListProperties);
    final ListProperties listProperties = (ListProperties) statement;
    Assert.assertTrue(listProperties.toString().equalsIgnoreCase("ListProperties{}"));
  }

  @Test
  public void testSetProperties() {
    final String simpleQuery = "set 'auto.offset.reset'='earliest';";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();
    Assert.assertTrue(statement instanceof SetProperty);
    final SetProperty setProperty = (SetProperty) statement;
    assertThat(setProperty.toString(), is("SetProperty{propertyName='auto.offset.reset', propertyValue='earliest'}"));
    Assert.assertTrue(setProperty.getPropertyName().equalsIgnoreCase("auto.offset.reset"));
    Assert.assertTrue(setProperty.getPropertyValue().equalsIgnoreCase("earliest"));
  }

  @Test
  public void testSelectSinkProperties() {
    final String simpleQuery = "create stream s1 with (timestamp='orderid', partitions = 3) as select "
                         + "col1, col2"
                         + " from orders where col2 is null and col3 is not null or (col3*col2 = "
                         + "12);";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();

    Assert.assertTrue("testSelectTumblingWindow failed.", statement instanceof CreateStreamAsSelect);
    final Query query = ((CreateStreamAsSelect) statement).getQuery();
    Assert.assertTrue(query.getWhere().toString().equalsIgnoreCase("Optional[(((ORDERS.COL2 IS NULL) AND (ORDERS.COL3 IS NOT NULL)) OR ((ORDERS.COL3 * ORDERS.COL2) = 12))]"));
  }

  @Test
  public void shouldParseDropStream() {
    final String simpleQuery = "DROP STREAM STREAM1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();
    assertThat(statement, instanceOf(DropStream.class));
    final DropStream dropStream = (DropStream)  statement;
    assertThat(dropStream.getName().toString().toUpperCase(), equalTo("STREAM1"));
    assertThat(dropStream.getIfExists(), is(false));
  }

  @Test
  public void shouldParseDropTable() {
    final String simpleQuery = "DROP TABLE TABLE1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();
    assertThat(statement, instanceOf(DropTable.class));
    final DropTable dropTable = (DropTable)  statement;
    assertThat(dropTable.getName().toString().toUpperCase(), equalTo("TABLE1"));
    assertThat(dropTable.getIfExists(), is(false));
  }

  @Test
  public void shouldParseDropStreamIfExists() {
    final String simpleQuery = "DROP STREAM IF EXISTS STREAM1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();
    assertThat(statement, instanceOf(DropStream.class));
    final DropStream dropStream = (DropStream)  statement;
    assertThat(dropStream.getName().toString().toUpperCase(), equalTo("STREAM1"));
    assertThat(dropStream.getIfExists(), is(true));
  }

  @Test
  public void shouldParseDropTableIfExists() {
    final String simpleQuery = "DROP TABLE IF EXISTS TABLE1;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore).getStatement();
    assertThat(statement, instanceOf(DropTable.class));
    final DropTable dropTable = (DropTable)  statement;
    assertThat(dropTable.getName().toString().toUpperCase(), equalTo("TABLE1"));
    assertThat(dropTable.getIfExists(), is(true));
  }

  @Test
  public void testInsertInto() {
    final String insertIntoString = "INSERT INTO test0 "
        + "SELECT col0, col2, col3 FROM test1 WHERE col0 > 100;";

    final Statement statement = KaypherParserTestUtil.buildSingleAst(insertIntoString, metaStore)
        .getStatement();


    assertThat(statement, instanceOf(InsertInto.class));
    final InsertInto insertInto = (InsertInto) statement;
    assertThat(insertInto.getTarget().toString(), equalTo("TEST0"));
    final Query query = insertInto.getQuery();
    assertThat( query.getSelect().getSelectItems().size(), equalTo(3));
    assertThat(query.getFrom(), not(nullValue()));
    assertThat(query.getWhere().isPresent(), equalTo(true));
    assertThat(query.getWhere().get(),  instanceOf(ComparisonExpression.class));
    final ComparisonExpression comparisonExpression = (ComparisonExpression)query.getWhere().get();
    assertThat(comparisonExpression.getType().getValue(), equalTo(">"));

  }

  @Test
  public void shouldSetShowDescriptionsForShowStreamsDescriptions() {
    final String statementString = "SHOW STREAMS EXTENDED;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    Assert.assertThat(statement, instanceOf(ListStreams.class));
    final ListStreams listStreams = (ListStreams)statement;
    Assert.assertThat(listStreams.getShowExtended(), is(true));
  }

  @Test
  public void shouldSetShowDescriptionsForShowTablesDescriptions() {
    final String statementString = "SHOW TABLES EXTENDED;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    Assert.assertThat(statement, instanceOf(ListTables.class));
    final ListTables listTables = (ListTables)statement;
    Assert.assertThat(listTables.getShowExtended(), is(true));
  }

  @Test
  public void shouldSetShowDescriptionsForShowQueriesDescriptions() {
    final String statementString = "SHOW QUERIES EXTENDED;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    Assert.assertThat(statement, instanceOf(ListQueries.class));
    final ListQueries listQueries = (ListQueries)statement;
    Assert.assertThat(listQueries.getShowExtended(), is(true));
  }
  
  private void assertQuerySucceeds(final String sql) {
    final Statement statement = KaypherParserTestUtil.buildSingleAst(sql, metaStore).getStatement();
    assertThat(statement, instanceOf(Query.class));
  }

  @Test
  public void shouldSetWithinExpressionWithSingleWithin() {
    final String statementString = "CREATE STREAM foobar as SELECT * from TEST1 JOIN ORDERS WITHIN "
                                   + "10 SECONDS ON TEST1.col1 = ORDERS.ORDERID ;";

    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();

    assertThat(statement, instanceOf(CreateStreamAsSelect.class));

    final CreateStreamAsSelect createStreamAsSelect = (CreateStreamAsSelect) statement;

    final Query query = createStreamAsSelect.getQuery();

    assertThat(query.getFrom(), instanceOf(Join.class));

    final Join join = (Join) query.getFrom();

    assertTrue(join.getWithinExpression().isPresent());

    final WithinExpression withinExpression = join.getWithinExpression().get();

    assertEquals(10L, withinExpression.getBefore());
    assertEquals(10L, withinExpression.getAfter());
    assertEquals(TimeUnit.SECONDS, withinExpression.getBeforeTimeUnit());
    assertEquals(Join.Type.INNER, join.getType());
  }


  @Test
  public void shouldSetWithinExpressionWithBeforeAndAfter() {
    final String statementString = "CREATE STREAM foobar as SELECT * from TEST1 JOIN ORDERS "
                                   + "WITHIN (10 seconds, 20 minutes) "
                                   + "ON TEST1.col1 = ORDERS.ORDERID ;";

    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();

    assertThat(statement, instanceOf(CreateStreamAsSelect.class));

    final CreateStreamAsSelect createStreamAsSelect = (CreateStreamAsSelect) statement;

    final Query query = createStreamAsSelect.getQuery();

    assertThat(query.getFrom(), instanceOf(Join.class));

    final Join join = (Join) query.getFrom();

    assertTrue(join.getWithinExpression().isPresent());

    final WithinExpression withinExpression = join.getWithinExpression().get();

    assertEquals(10L, withinExpression.getBefore());
    assertEquals(20L, withinExpression.getAfter());
    assertEquals(TimeUnit.SECONDS, withinExpression.getBeforeTimeUnit());
    assertEquals(TimeUnit.MINUTES, withinExpression.getAfterTimeUnit());
    assertEquals(Join.Type.INNER, join.getType());
  }

  @Test
  public void shouldHaveInnerJoinTypeWithExplicitInnerKeyword() {
    final String statementString = "CREATE STREAM foobar as SELECT * from TEST1 INNER JOIN TEST2 "
                                   + "ON TEST1.col1 = TEST2.col1;";

    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();

    assertThat(statement, instanceOf(CreateStreamAsSelect.class));

    final CreateStreamAsSelect createStreamAsSelect = (CreateStreamAsSelect) statement;

    final Query query = createStreamAsSelect.getQuery();

    assertThat(query.getFrom(), instanceOf(Join.class));

    final Join join = (Join) query.getFrom();

    assertEquals(Join.Type.INNER, join.getType());
  }

  @Test
  public void shouldHaveLeftJoinTypeWhenOuterIsSpecified() {
    final String statementString = "CREATE STREAM foobar as SELECT * from TEST1 LEFT OUTER JOIN "
                                   + "TEST2 ON TEST1.col1 = TEST2.col1;";

    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();

    assertThat(statement, instanceOf(CreateStreamAsSelect.class));

    final CreateStreamAsSelect createStreamAsSelect = (CreateStreamAsSelect) statement;

    final Query query = createStreamAsSelect.getQuery();

    assertThat(query.getFrom(), instanceOf(Join.class));

    final Join join = (Join) query.getFrom();

    assertEquals(Join.Type.LEFT, join.getType());
  }

  @Test
  public void shouldHaveLeftJoinType() {
    final String statementString = "CREATE STREAM foobar as SELECT * from TEST1 LEFT JOIN "
                                   + "TEST2 ON TEST1.col1 = TEST2.col1;";

    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();

    assertThat(statement, instanceOf(CreateStreamAsSelect.class));

    final CreateStreamAsSelect createStreamAsSelect = (CreateStreamAsSelect) statement;

    final Query query = createStreamAsSelect.getQuery();

    assertThat(query.getFrom(), instanceOf(Join.class));

    final Join join = (Join) query.getFrom();

    assertEquals(Join.Type.LEFT, join.getType());
  }

  @Test
  public void shouldHaveOuterJoinType() {
    final String statementString = "CREATE STREAM foobar as SELECT * from TEST1 FULL JOIN "
                                   + "TEST2 ON TEST1.col1 = TEST2.col1;";

    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();

    assertThat(statement, instanceOf(CreateStreamAsSelect.class));

    final CreateStreamAsSelect createStreamAsSelect = (CreateStreamAsSelect) statement;

    final Query query = createStreamAsSelect.getQuery();

    assertThat(query.getFrom(), instanceOf(Join.class));

    final Join join = (Join) query.getFrom();

    assertEquals(Join.Type.OUTER, join.getType());
  }

  @Test
  public void shouldHaveOuterJoinTypeWhenOuterKeywordIsSpecified() {
    final String statementString = "CREATE STREAM foobar as SELECT * from TEST1 FULL OUTER JOIN "
                                   + "TEST2 ON TEST1.col1 = TEST2.col1;";

    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();

    assertThat(statement, instanceOf(CreateStreamAsSelect.class));

    final CreateStreamAsSelect createStreamAsSelect = (CreateStreamAsSelect) statement;

    final Query query = createStreamAsSelect.getQuery();

    assertThat(query.getFrom(), instanceOf(Join.class));

    final Join join = (Join) query.getFrom();

    assertEquals(Join.Type.OUTER, join.getType());
  }

  @Test
  public void shouldAddPrefixEvenIfColumnNameIsTheSameAsStream() {
    final String statementString =
        "CREATE STREAM S AS SELECT address FROM address a;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(statement, instanceOf(CreateStreamAsSelect.class));
    final Query query = ((CreateStreamAsSelect) statement).getQuery();
    assertThat(query.getSelect().getSelectItems().get(0),
        equalToColumn("A.ADDRESS", "ADDRESS"));
  }

  @Test
  public void shouldNotAddPrefixIfStreamNameIsPrefix() {
    final String statementString =
        "CREATE STREAM S AS SELECT address.orderid FROM address a;";
    KaypherParserTestUtil.buildSingleAst(statementString, metaStore);
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(statement, instanceOf(CreateStreamAsSelect.class));
    final Query query = ((CreateStreamAsSelect) statement).getQuery();
    assertThat(query.getSelect().getSelectItems().get(0),
        equalToColumn("ADDRESS.ORDERID", "ORDERID"));
  }

  @Test
  public void shouldPassIfStreamColumnNameWithAliasIsNotAmbiguous() {
    final String statementString =
        "CREATE STREAM S AS SELECT a.address->city FROM address a;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(statement, instanceOf(CreateStreamAsSelect.class));
    final Query query = ((CreateStreamAsSelect) statement).getQuery();
    assertThat(query.getSelect().getSelectItems().get(0),
        equalToColumn("FETCH_FIELD_FROM_STRUCT(A.ADDRESS, 'CITY')", "ADDRESS__CITY"));
  }

  @Test
  public void shouldPassIfStreamColumnNameIsNotAmbiguous() {
    final String statementString =
        "CREATE STREAM S AS SELECT address.address->city FROM address a;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(statement, instanceOf(CreateStreamAsSelect.class));
    final Query query = ((CreateStreamAsSelect) statement).getQuery();

    final SelectItem item = query.getSelect().getSelectItems().get(0);
    assertThat(item, equalToColumn(
        "FETCH_FIELD_FROM_STRUCT(ADDRESS.ADDRESS, 'CITY')",
        "ADDRESS__CITY"
    ));
  }

  @Test(expected = KaypherException.class)
  public void shouldFailJoinQueryParseIfStreamColumnNameWithNoAliasIsAmbiguous() {
    final String statementString =
        "CREATE STREAM S AS SELECT itemid FROM address a JOIN itemid on a.itemid = itemid.itemid;";
    KaypherParserTestUtil.buildSingleAst(statementString, metaStore);
  }

  @Test
  public void shouldPassJoinQueryParseIfStreamColumnNameWithAliasIsNotAmbiguous() {
    final String statementString =
        "CREATE STREAM S AS SELECT itemid.itemid FROM address a JOIN itemid on a.itemid = itemid.itemid;";
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();
    assertThat(statement, instanceOf(CreateStreamAsSelect.class));
    final Query query = ((CreateStreamAsSelect) statement).getQuery();
    assertThat(query.getSelect().getSelectItems().get(0),
        equalToColumn("ITEMID.ITEMID", "ITEMID_ITEMID"));
  }

  @Test
  public void testSelectWithOnlyColumns() {
    expectedException.expect(ParseFailedException.class);
    expectedException.expectMessage("line 1:21: extraneous input ';' expecting {',', 'FROM'}");

    final String simpleQuery = "SELECT ONLY, COLUMNS;";
    KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore);;
  }

  @Test
  public void testSelectWithMissingComma() {
    expectedException.expect(ParseFailedException.class);
    expectedException.expectMessage(containsString("line 1:12: extraneous input 'C' expecting"));

    final String simpleQuery = "SELECT A B C FROM address;";
    KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore);
  }

  @Test
  public void testSelectWithMultipleFroms() {
    expectedException.expect(ParseFailedException.class);
    expectedException.expectMessage(containsString("line 1:22: mismatched input ',' expecting"));

    final String simpleQuery = "SELECT * FROM address, itemid;";
    KaypherParserTestUtil.buildSingleAst(simpleQuery, metaStore);;
  }

  @Test
  public void shouldParseSimpleComment() {
    final String statementString = "--this is a comment.\n"
        + "SHOW STREAMS;";

    final List<PreparedStatement<?>> statements =  KaypherParserTestUtil.buildAst(statementString, metaStore);

    assertThat(statements, hasSize(1));
    assertThat(statements.get(0).getStatement(), is(instanceOf(ListStreams.class)));
  }

  @Test
  public void shouldParseBracketedComment() {
    final String statementString = "/* this is a bracketed comment. */\n"
        + "SHOW STREAMS;"
        + "/*another comment!*/";

    final List<PreparedStatement<?>> statements = KaypherParserTestUtil.buildAst(statementString, metaStore);

    assertThat(statements, hasSize(1));
    assertThat(statements.get(0).getStatement(), is(instanceOf(ListStreams.class)));
  }

  @Test
  public void shouldParseMultiLineWithInlineComments() {
    final String statementString =
        "SHOW -- inline comment\n"
        + "STREAMS;";

    final List<PreparedStatement<?>> statements =  KaypherParserTestUtil.buildAst(statementString, metaStore);

    assertThat(statements, hasSize(1));
    assertThat(statements.get(0).getStatement(), is(instanceOf(ListStreams.class)));
  }

  @Test
  public void shouldParseMultiLineWithInlineBracketedComments() {
    final String statementString =
        "SHOW /* inline\n"
            + "comment */\n"
            + "STREAMS;";

    final List<PreparedStatement<?>> statements =  KaypherParserTestUtil.buildAst(statementString, metaStore);

    assertThat(statements, hasSize(1));
    assertThat(statements.get(0).getStatement(), is(instanceOf(ListStreams.class)));
  }

  @Test
  public void shouldBuildSearchedCaseStatement() {
    // Given:
    final String statementString =
        "CREATE STREAM S AS SELECT CASE WHEN orderunits < 10 THEN 'small' WHEN orderunits < 100 THEN 'medium' ELSE 'large' END FROM orders;";

    // When:
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();

    // Then:
    final SearchedCaseExpression searchedCaseExpression = getSearchedCaseExpressionFromCsas(statement);
    assertThat(searchedCaseExpression.getWhenClauses().size(), equalTo(2));
    assertThat(searchedCaseExpression.getWhenClauses().get(0).getOperand().toString(), equalTo("(ORDERS.ORDERUNITS < 10)"));
    assertThat(searchedCaseExpression.getWhenClauses().get(0).getResult().toString(), equalTo("'small'"));
    assertThat(searchedCaseExpression.getWhenClauses().get(1).getOperand().toString(), equalTo("(ORDERS.ORDERUNITS < 100)"));
    assertThat(searchedCaseExpression.getWhenClauses().get(1).getResult().toString(), equalTo("'medium'"));
    assertTrue(searchedCaseExpression.getDefaultValue().isPresent());
    assertThat(searchedCaseExpression.getDefaultValue().get().toString(), equalTo("'large'"));
  }

  @Test
  public void shouldBuildSearchedCaseWithoutDefaultStatement() {
    // Given:
    final String statementString =
        "CREATE STREAM S AS SELECT CASE WHEN orderunits < 10 THEN 'small' WHEN orderunits < 100 THEN 'medium' END FROM orders;";

    // When:
    final Statement statement = KaypherParserTestUtil.buildSingleAst(statementString, metaStore)
        .getStatement();

    // Then:
    final SearchedCaseExpression searchedCaseExpression = getSearchedCaseExpressionFromCsas(statement);
    assertThat(searchedCaseExpression.getDefaultValue().isPresent(), equalTo(false));
  }

  private static SearchedCaseExpression getSearchedCaseExpressionFromCsas(final Statement statement) {
    final Query query = ((CreateStreamAsSelect) statement).getQuery();
    final Expression caseExpression = ((SingleColumn) query.getSelect().getSelectItems().get(0)).getExpression();
    return (SearchedCaseExpression) caseExpression;
  }

  private static Matcher<SelectItem> equalToColumn(
      final String expression,
      final String alias) {
    return new TypeSafeMatcher<SelectItem>() {
      @Override
      protected boolean matchesSafely(SelectItem item) {
        if (!(item instanceof SingleColumn)) {
          return false;
        }

        SingleColumn column = (SingleColumn) item;
        return Objects.equals(column.getExpression().toString(), expression)
            && Objects.equals(column.getAlias().orElse(null), alias)
            && Objects.equals(column.getAllColumns().isPresent(), false);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            String.format("Expression: %s, Alias: %s",
                expression,
                alias));
      }
    };
  }

}