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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import io.confluent.connect.avro.AvroData;
import io.confluent.connect.avro.AvroDataConfig;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import com.treutec.kaypher.execution.ddl.commands.CreateSourceCommand;
import com.treutec.kaypher.execution.ddl.commands.KaypherTopic;
import com.treutec.kaypher.name.ColumnName;
import com.treutec.kaypher.name.SourceName;
import com.treutec.kaypher.schema.kaypher.LogicalSchema;
import com.treutec.kaypher.schema.kaypher.LogicalSchema.Builder;
import com.treutec.kaypher.schema.kaypher.PhysicalSchema;
import com.treutec.kaypher.schema.kaypher.SchemaConverters;
import com.treutec.kaypher.schema.kaypher.SchemaConverters.ConnectToSqlTypeConverter;
import com.treutec.kaypher.schema.kaypher.types.SqlTypes;
import com.treutec.kaypher.serde.Format;
import com.treutec.kaypher.serde.FormatInfo;
import com.treutec.kaypher.serde.KeyFormat;
import com.treutec.kaypher.serde.SerdeOption;
import com.treutec.kaypher.serde.ValueFormat;
import com.treutec.kaypher.serde.avro.AvroSchemas;
import com.treutec.kaypher.serde.connect.ConnectSchemaTranslator;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.apache.kafka.connect.data.Schema;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AvroUtilTest {

  private static final SourceName STREAM_NAME = SourceName.of("some_stream");

  private static final String AVRO_SCHEMA_STRING = "{"
      + "\"namespace\": \"some.namespace\","
      + " \"name\": \"orders\","
      + " \"type\": \"record\","
      + " \"fields\": ["
      + "     {\"name\": \"ordertime\", \"type\": \"long\"},"
      + "     {\"name\": \"orderid\",  \"type\": \"long\"},"
      + "     {\"name\": \"itemid\", \"type\": \"string\"},"
      + "     {\"name\": \"orderunits\", \"type\": \"double\"},"
      + "     {\"name\": \"arraycol\", \"type\": {\"type\": \"array\", \"items\": \"double\"}},"
      + "     {\"name\": \"mapcol\", \"type\": {\"type\": \"map\", \"values\": \"double\"}}"
      + " ]"
      + "}";

  private static final String SINGLE_FIELD_AVRO_SCHEMA_STRING = "{"
      + "\"namespace\": \"some.namespace\","
      + " \"name\": \"orders\","
      + " \"type\": \"record\","
      + " \"fields\": ["
      + "     {\"name\": \"ordertime\", \"type\": \"long\"}"
      + " ]"
      + "}";

  private static final String STATEMENT_TEXT = "STATEMENT";

  private static final LogicalSchema MUTLI_FIELD_SCHEMA =
      toKaypherSchema(AVRO_SCHEMA_STRING);

  private static final LogicalSchema SINGLE_FIELD_SCHEMA =
      toKaypherSchema(SINGLE_FIELD_AVRO_SCHEMA_STRING);

  private static final LogicalSchema SCHEMA_WITH_MAPS = LogicalSchema.builder()
      .valueColumn(ColumnName.of("notmap"), SqlTypes.BIGINT)
      .valueColumn(ColumnName.of("mapcol"), SqlTypes.map(SqlTypes.INTEGER))
      .build();

  private static final String SCHEMA_NAME = "schema_name";

  private static final KaypherTopic RESULT_TOPIC = new KaypherTopic(
      "actual-name",
      KeyFormat.nonWindowed(FormatInfo.of(Format.KAFKA)),
      ValueFormat.of(FormatInfo.of(Format.AVRO, Optional.of(SCHEMA_NAME), Optional.empty())),
      false);

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  @Mock
  private SchemaRegistryClient srClient;
  @Mock
  private CreateSourceCommand ddlCommand;

  private final KaypherConfig kaypherConfig = new KaypherConfig(Collections.emptyMap());

  @Before
  public void setUp() {
    when(ddlCommand.getSerdeOptions()).thenReturn(SerdeOption.none());
    when(ddlCommand.getSchema()).thenReturn(MUTLI_FIELD_SCHEMA);
    when(ddlCommand.getTopic()).thenReturn(RESULT_TOPIC);
  }

  @Test
  public void shouldValidateSchemaEvolutionWithCorrectSubject() throws Exception {
    // Given:
    when(srClient.testCompatibility(anyString(), any())).thenReturn(true);

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);

    // Then:
    verify(srClient).testCompatibility(eq(RESULT_TOPIC.getKafkaTopicName() + "-value"), any());
  }

  @Test
  public void shouldValidateSchemaEvolutionWithCorrectSchema() throws Exception {
    // Given:
    final PhysicalSchema schema = PhysicalSchema.from(MUTLI_FIELD_SCHEMA, SerdeOption.none());

    final org.apache.avro.Schema expectedAvroSchema = AvroSchemas
        .getAvroSchema(schema.valueSchema(), SCHEMA_NAME, kaypherConfig);
    when(srClient.testCompatibility(anyString(), any())).thenReturn(true);

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);

    // Then:
    verify(srClient).testCompatibility(any(), eq(expectedAvroSchema));
  }

  @Test
  public void shouldValidateSchemaWithMaps() throws Exception {
    // Given:
    when(ddlCommand.getSchema()).thenReturn(SCHEMA_WITH_MAPS);
    final PhysicalSchema schema = PhysicalSchema
        .from(SCHEMA_WITH_MAPS, SerdeOption.none());

    when(srClient.testCompatibility(anyString(), any())).thenReturn(true);

    final org.apache.avro.Schema expectedAvroSchema = AvroSchemas
        .getAvroSchema(schema.valueSchema(), SCHEMA_NAME, kaypherConfig);

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);

    // Then:
    verify(srClient).testCompatibility(any(), eq(expectedAvroSchema));
  }

  @Test
  public void shouldValidateWrappedSingleFieldSchemaEvolution() throws Exception {
    // Given:
    when(ddlCommand.getSchema()).thenReturn(SINGLE_FIELD_SCHEMA);
    final PhysicalSchema schema = PhysicalSchema
        .from(SINGLE_FIELD_SCHEMA, SerdeOption.none());

    when(srClient.testCompatibility(anyString(), any())).thenReturn(true);

    final org.apache.avro.Schema expectedAvroSchema = AvroSchemas
        .getAvroSchema(schema.valueSchema(), SCHEMA_NAME, kaypherConfig);

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);

    // Then:
    verify(srClient).testCompatibility(any(), eq(expectedAvroSchema));
  }

  @Test
  public void shouldValidateUnwrappedSingleFieldSchemaEvolution() throws Exception {
    // Given:
    when(ddlCommand.getSchema()).thenReturn(SINGLE_FIELD_SCHEMA);
    when(ddlCommand.getSerdeOptions())
        .thenReturn(ImmutableSet.of(SerdeOption.UNWRAP_SINGLE_VALUES));
    final PhysicalSchema schema = PhysicalSchema
        .from(SINGLE_FIELD_SCHEMA, SerdeOption.of(SerdeOption.UNWRAP_SINGLE_VALUES));

    when(srClient.testCompatibility(anyString(), any())).thenReturn(true);

    final org.apache.avro.Schema expectedAvroSchema = AvroSchemas
        .getAvroSchema(schema.valueSchema(), SCHEMA_NAME, kaypherConfig);

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);

    // Then:
    verify(srClient).testCompatibility(any(), eq(expectedAvroSchema));
  }

  @Test
  public void shouldNotThrowInvalidEvolution() throws Exception {
    // Given:
    when(srClient.testCompatibility(any(), any())).thenReturn(true);

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);
  }

  @Test
  public void shouldReturnInvalidEvolution() throws Exception {
    // Given:
    when(srClient.testCompatibility(any(), any())).thenReturn(false);

    expectedException.expect(KaypherException.class);
    expectedException.expectMessage("Cannot register avro schema for actual-name as the schema is incompatible with the current schema version registered for the topic");

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);
  }

  @Test
  public void shouldNotThrowInvalidEvolutionIfSubjectNotRegistered() throws Exception {
    // Given:
    when(srClient.testCompatibility(any(), any()))
        .thenThrow(new RestClientException("Unknown subject", 404, 40401));

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);
  }

  @Test
  public void shouldThrowOnSrAuthorizationErrors() throws Exception {
    // Given:
    when(srClient.testCompatibility(any(), any()))
        .thenThrow(new RestClientException("Unknown subject", 403, 40401));

    // Expect:
    expectedException.expect(KaypherException.class);
    expectedException.expectMessage("Could not connect to Schema Registry service");
    expectedException.expectMessage(containsString(String.format(
        "Not authorized to access Schema Registry subject: [%s]",
        ddlCommand.getTopic().getKafkaTopicName()
            + KaypherConstants.SCHEMA_REGISTRY_VALUE_SUFFIX
    )));

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);
  }

  @Test
  public void shouldThrowOnAnyOtherEvolutionSrException() throws Exception {
    // Given:
    when(srClient.testCompatibility(any(), any()))
        .thenThrow(new RestClientException("Unknown subject", 500, 40401));

    // Expect:
    expectedException.expect(KaypherException.class);
    expectedException.expectMessage("Could not connect to Schema Registry service");

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);
  }

  @Test
  public void shouldThrowOnAnyOtherEvolutionIOException() throws Exception {
    // Given:
    when(srClient.testCompatibility(any(), any()))
        .thenThrow(new IOException("something"));

    // Expect:
    expectedException.expect(KaypherException.class);
    expectedException.expectMessage("Could not check Schema compatibility");

    // When:
    AvroUtil.throwOnInvalidSchemaEvolution(STATEMENT_TEXT, ddlCommand, srClient, kaypherConfig);
  }

  private static LogicalSchema toKaypherSchema(final String avroSchemaString) {
    final org.apache.avro.Schema avroSchema =
        new org.apache.avro.Schema.Parser().parse(avroSchemaString);
    final AvroData avroData = new AvroData(new AvroDataConfig(Collections.emptyMap()));
    final Schema connectSchema = new ConnectSchemaTranslator()
        .toKaypherSchema(avroData.toConnectSchema(avroSchema));

    final ConnectToSqlTypeConverter converter = SchemaConverters
        .connectToSqlConverter();

    final Builder builder = LogicalSchema.builder();
    connectSchema.fields()
        .forEach(f -> builder.valueColumn(ColumnName.of(f.name()), converter.toSqlType(f.schema())));

    return builder.build();
  }
}
