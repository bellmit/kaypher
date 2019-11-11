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
package com.treutec.kaypher.function.udaf.topkdistinct;

import com.google.common.collect.ImmutableList;
import com.treutec.kaypher.function.AggregateFunctionFactory;
import com.treutec.kaypher.function.AggregateFunctionInitArguments;
import com.treutec.kaypher.function.KaypherAggregateFunction;
import com.treutec.kaypher.util.DecimalUtil;
import com.treutec.kaypher.util.KaypherException;
import com.treutec.kaypher.util.SchemaUtil;
import java.util.List;
import org.apache.kafka.connect.data.Schema;

public class TopkDistinctAggFunctionFactory extends AggregateFunctionFactory {

  private static final String NAME = "TOPKDISTINCT";

  private static final List<List<Schema>> SUPPORTED_TYPES = ImmutableList
      .<List<Schema>>builder()
      .add(ImmutableList.of(Schema.OPTIONAL_INT32_SCHEMA))
      .add(ImmutableList.of(Schema.OPTIONAL_INT64_SCHEMA))
      .add(ImmutableList.of(Schema.OPTIONAL_FLOAT64_SCHEMA))
      .add(ImmutableList.of(Schema.OPTIONAL_STRING_SCHEMA))
      .add(ImmutableList.of(DecimalUtil.builder(1, 1)))
      .build();

  public TopkDistinctAggFunctionFactory() {
    super(NAME);
  }

  private static final AggregateFunctionInitArguments DEFAULT_INIT_ARGS =
      new AggregateFunctionInitArguments(0, 1);

  @SuppressWarnings("unchecked")
  @Override
  public KaypherAggregateFunction createAggregateFunction(
      final List<Schema> argTypeList,
      final AggregateFunctionInitArguments initArgs) {
    if (argTypeList.isEmpty()) {
      throw new KaypherException("TOPKDISTINCT function should have two arguments.");
    }
    final int tkValFromArg = (Integer)(initArgs.arg(0));
    final Schema argSchema = argTypeList.get(0);
    switch (argSchema.type()) {
      case INT32:
      case INT64:
      case FLOAT64:
      case STRING:
        return new TopkDistinctKudaf(NAME, initArgs.udafIndex(), tkValFromArg, argSchema,
            SchemaUtil.getJavaType(argSchema));
      case BYTES:
        DecimalUtil.requireDecimal(argSchema);
        return new TopkDistinctKudaf(NAME, initArgs.udafIndex(), tkValFromArg, argSchema,
            SchemaUtil.getJavaType(argSchema));
      default:
        throw new KaypherException("No TOPKDISTINCT aggregate function with " + argTypeList.get(0)
            + " argument type exists!");
    }
  }

  @Override
  public List<List<Schema>> supportedArgs() {
    return SUPPORTED_TYPES;
  }

  @Override
  public AggregateFunctionInitArguments getDefaultArguments() {
    return DEFAULT_INIT_ARGS;
  }
}