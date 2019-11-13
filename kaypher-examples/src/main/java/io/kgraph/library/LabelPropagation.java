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

package io.kgraph.library;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import io.kgraph.EdgeWithValue;
import io.kgraph.VertexWithValue;
import io.kgraph.pregel.ComputeFunction;

public class LabelPropagation<EV> implements ComputeFunction<Long, Long, EV, Map<Long, Long>> {

    @Override
    public void compute(
        int superstep,
        VertexWithValue<Long, Long> vertex,
        Iterable<Map<Long, Long>> messages,
        Iterable<EdgeWithValue<Long, EV>> edges,
        Callback<Long, Long, EV, Map<Long, Long>> cb) {

        Long vertexValue = vertex.value();

        Map<Long, Long> counts = new TreeMap<>();
        for (Map<Long, Long> message : messages) {
            message.forEach((k, v) -> counts.merge(k, v, (v1, v2) -> v1 + v2));
        }
        if (!counts.isEmpty()) {
            Long maxKey = counts.entrySet().stream()
                .max((e1, e2) -> (int) (e1.getValue() - e2.getValue() != 0
                    ? e1.getValue() - e2.getValue() : e1.getKey() - e2.getKey())).get().getKey();

            if (vertexValue < maxKey) {
                vertexValue = maxKey;
                cb.setNewVertexValue(vertexValue);
            }
        }
        for (EdgeWithValue<Long, EV> edge : edges) {
            cb.sendMessageTo(edge.target(), Collections.singletonMap(vertexValue, 1L));
        }
    }
}
