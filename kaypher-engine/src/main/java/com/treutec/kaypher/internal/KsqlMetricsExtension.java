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
package com.treutec.kaypher.internal;

import java.util.List;
import java.util.Map;
import org.apache.kafka.common.Configurable;

/**
 * This interface provides a way for users to provide custom metrics that will be emitted alongside
 * the default KAYPHER engine JMX metrics.
 */
public interface KaypherMetricsExtension extends Configurable {

  @Override
  void configure(Map<String, ?> config);

  /**
   * Returns custom metrics to be emitted alongside the default KAYPHER engine JMX metrics.
   *
   * @return list of metrics
   */
  List<KaypherMetric> getCustomMetrics();
}
