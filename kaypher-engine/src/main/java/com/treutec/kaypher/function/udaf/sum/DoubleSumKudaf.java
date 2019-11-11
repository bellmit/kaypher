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
package com.treutec.kaypher.function.udaf.sum;

import com.treutec.kaypher.execution.function.TableAggregationFunction;
import com.treutec.kaypher.function.BaseAggregateFunction;
import java.util.Collections;
import java.util.function.Function;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.streams.kstream.Merger;

public class DoubleSumKudaf
    extends BaseAggregateFunction<Double, Double, Double>
    implements TableAggregationFunction<Double, Double, Double> {

  DoubleSumKudaf(final String functionName, final int argIndexInValue) {
    super(functionName,
          argIndexInValue, () -> 0.0,
          Schema.OPTIONAL_FLOAT64_SCHEMA,
          Schema.OPTIONAL_FLOAT64_SCHEMA,
          Collections.singletonList(Schema.OPTIONAL_FLOAT64_SCHEMA),
          "Computes the sum for a key.");
  }

  @Override
  public Double aggregate(final Double valueToAdd, final Double aggregateValue) {
    if (valueToAdd == null) {
      return aggregateValue;
    }
    return aggregateValue + valueToAdd;
  }

  @Override
  public Double undo(final Double valueToUndo, final Double aggregateValue) {
    if (valueToUndo == null) {
      return aggregateValue;
    }
    return aggregateValue - valueToUndo;
  }

  @Override
  public Merger<Struct, Double> getMerger() {
    return (aggKey, aggOne, aggTwo) -> aggOne + aggTwo;
  }

  @Override
  public Function<Double, Double> getResultMapper() {
    return Function.identity();
  }

}
