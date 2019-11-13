/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kgraph.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BiFunction;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;

import io.kgraph.Edge;
import io.kgraph.GraphSerialized;
import io.kgraph.KGraph;
import io.vavr.Tuple2;

public class GraphGenerators {

    public static KGraph<Long, Long, Long> completeGraph(
        StreamsBuilder builder, Properties producerConfig, int numVertices) {
        List<KeyValue<Edge<Long>, Long>> edgeList = new ArrayList<>();
        for (long i = 0; i < numVertices; i++) {
            for (long j = 0; j < numVertices; j++) {
                if (i != j) edgeList.add(new KeyValue<>(new Edge<>(i, j), 1L));
            }
        }
        KTable<Edge<Long>, Long> edges = StreamUtils.tableFromCollection(
            builder, producerConfig, new KryoSerde<>(), Serdes.Long(), edgeList);

        return KGraph.fromEdges(edges, v -> 1L,
            GraphSerialized.with(Serdes.Long(), Serdes.Long(), Serdes.Long()));
    }

    public static KGraph<Long, Tuple2<Long, Long>, Long> gridGraph(
        StreamsBuilder builder, Properties producerConfig, int numRows, int numCols) {
        BiFunction<Long, Long, Long> posToIdx = (row, col) -> row * numCols + col;
        List<KeyValue<Long, Tuple2<Long, Long>>> vertexList = new ArrayList<>();
        for (long row = 0; row < numRows; row++) {
            for (long col = 0; col < numCols; col++) {
                vertexList.add(new KeyValue<>(posToIdx.apply(row, col), new Tuple2<>(row, col)));
            }
        }
        KTable<Long, Tuple2<Long, Long>> vertices = StreamUtils.tableFromCollection(
            builder, producerConfig, Serdes.Long(), new KryoSerde<>(), vertexList);

        KTable<Edge<Long>, Long> edges = vertices
            .toStream()
            .flatMap((v, tuple) -> {
                List<KeyValue<Edge<Long>, Long>> result = new ArrayList<>();
                long row = tuple._1;
                long col = tuple._2;
                if (row + 1 < numRows) {
                    result.add(new KeyValue<>(new Edge<>(posToIdx.apply(row, col), posToIdx.apply(row + 1, col)), 1L));
                }
                if (col + 1 < numCols) {
                    result.add(new KeyValue<>(new Edge<>(posToIdx.apply(row, col), posToIdx.apply(row, col + 1)), 1L));
                }
                return result;
            })
            .groupByKey(Grouped.with(new KryoSerde<>(), Serdes.Long()))
            .reduce((v1, v2) -> v2, Materialized.with(new KryoSerde<>(), Serdes.Long()));

        return new KGraph<>(vertices, edges, GraphSerialized.with(Serdes.Long(), new KryoSerde<>(), Serdes.Long()));
    }

    public static KGraph<Long, Long, Long> starGraph(
        StreamsBuilder builder, Properties producerConfig, int numVertices) {
        List<KeyValue<Edge<Long>, Long>> edgeList = new ArrayList<>();
        for (long i = 1; i < numVertices; i++) {
            edgeList.add(new KeyValue<>(new Edge<>(i, 0L), 1L));
        }
        KTable<Edge<Long>, Long> edges = StreamUtils.tableFromCollection(
            builder, producerConfig, new KryoSerde<>(), Serdes.Long(), edgeList);

        return KGraph.fromEdges(edges, v -> 1L,
            GraphSerialized.with(Serdes.Long(), Serdes.Long(), Serdes.Long()));
    }
}
