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
package com.treutec.kaypher.function.udaf.max;

import com.treutec.kaypher.function.udaf.BaseNumberKudaf;
import org.apache.kafka.connect.data.Schema;

public class DoubleMaxKudaf extends BaseNumberKudaf<Double> {

  DoubleMaxKudaf(final String functionName, final int argIndexInValue) {
    super(functionName,
          argIndexInValue,
          Schema.OPTIONAL_FLOAT64_SCHEMA,
          Double::max,
          "Computes the maximum double value for a key.");
  }

}
