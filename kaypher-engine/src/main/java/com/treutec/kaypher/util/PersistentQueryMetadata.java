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

import static java.util.Objects.requireNonNull;

import com.treutec.kaypher.execution.context.QueryContext;
import com.treutec.kaypher.execution.ddl.commands.KaypherTopic;
import com.treutec.kaypher.execution.streams.materialization.Materialization;
import com.treutec.kaypher.execution.streams.materialization.MaterializationProvider;
import com.treutec.kaypher.metastore.model.DataSource.DataSourceType;
import com.treutec.kaypher.name.SourceName;
import com.treutec.kaypher.query.QueryId;
import com.treutec.kaypher.schema.kaypher.PhysicalSchema;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;

/**
 * Metadata of a persistent query, e.g. {@code CREATE STREAM FOO AS SELECT * FROM BAR;}.
 */
public class PersistentQueryMetadata extends QueryMetadata {

  private final QueryId id;
  private final KaypherTopic resultTopic;
  private final SourceName sinkName;
  private final QuerySchemas schemas;
  private final PhysicalSchema resultSchema;
  private final DataSourceType dataSourceType;
  private final Optional<MaterializationProvider> materializationProvider;

  // CHECKSTYLE_RULES.OFF: ParameterNumberCheck
  public PersistentQueryMetadata(
      final String statementString,
      final KafkaStreams kafkaStreams,
      final PhysicalSchema schema,
      final Set<SourceName> sourceNames,
      final SourceName sinkName,
      final String executionPlan,
      final QueryId id,
      final DataSourceType dataSourceType,
      final Optional<MaterializationProvider> materializationProvider,
      final String queryApplicationId,
      final KaypherTopic resultTopic,
      final Topology topology,
      final QuerySchemas schemas,
      final Map<String, Object> streamsProperties,
      final Map<String, Object> overriddenProperties,
      final Consumer<QueryMetadata> closeCallback
  ) {
    // CHECKSTYLE_RULES.ON: ParameterNumberCheck
    super(
        statementString,
        kafkaStreams,
        schema.logicalSchema(),
        sourceNames,
        executionPlan,
        queryApplicationId,
        topology,
        streamsProperties,
        overriddenProperties,
        closeCallback);

    this.id = requireNonNull(id, "id");
    this.resultTopic = requireNonNull(resultTopic, "resultTopic");
    this.sinkName = Objects.requireNonNull(sinkName, "sinkName");
    this.schemas = requireNonNull(schemas, "schemas");
    this.resultSchema = requireNonNull(schema, "schema");
    this.materializationProvider =
        requireNonNull(materializationProvider, "materializationProvider");
    this.dataSourceType = Objects.requireNonNull(dataSourceType, "dataSourceType");
  }

  private PersistentQueryMetadata(
      final PersistentQueryMetadata other,
      final Consumer<QueryMetadata> closeCallback
  ) {
    super(other, closeCallback);
    this.id = other.id;
    this.resultTopic = other.resultTopic;
    this.sinkName = other.sinkName;
    this.schemas = other.schemas;
    this.resultSchema = other.resultSchema;
    this.materializationProvider = other.materializationProvider;
    this.dataSourceType = other.dataSourceType;
  }

  public PersistentQueryMetadata copyWith(final Consumer<QueryMetadata> closeCallback) {
    return new PersistentQueryMetadata(this, closeCallback);
  }

  public DataSourceType getDataSourceType() {
    return dataSourceType;
  }

  public QueryId getQueryId() {
    return id;
  }

  public KaypherTopic getResultTopic() {
    return resultTopic;
  }

  public SourceName getSinkName() {
    return sinkName;
  }

  public String getSchemasDescription() {
    return schemas.toString();
  }

  public PhysicalSchema getPhysicalSchema() {
    return resultSchema;
  }

  public Optional<Materialization> getMaterialization(
      final QueryId queryId,
      final QueryContext.Stacker contextStacker
  ) {
    return materializationProvider.map(builder -> builder.build(queryId, contextStacker));
  }
}
