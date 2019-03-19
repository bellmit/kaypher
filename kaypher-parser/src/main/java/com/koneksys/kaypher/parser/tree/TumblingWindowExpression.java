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

package com.koneksys.kaypher.parser.tree;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.Immutable;
import com.koneksys.kaypher.GenericRow;
import com.koneksys.kaypher.function.UdafAggregator;
import com.koneksys.kaypher.metastore.SerdeFactory;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.streams.kstream.Initializer;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;

@Immutable
public class TumblingWindowExpression extends KaypherWindowExpression {

  private final long size;
  private final TimeUnit sizeUnit;

  public TumblingWindowExpression(final long size, final TimeUnit sizeUnit) {
    this(Optional.empty(), size, sizeUnit);
  }

  public TumblingWindowExpression(
      final Optional<NodeLocation> location,
      final long size,
      final TimeUnit sizeUnit
  ) {
    super(location);
    this.size = size;
    this.sizeUnit = requireNonNull(sizeUnit, "sizeUnit");
  }

  public long getSize() {
    return size;
  }

  public TimeUnit getSizeUnit() {
    return sizeUnit;
  }

  @Override
  public <R, C> R accept(final AstVisitor<R, C> visitor, final C context) {
    return visitor.visitTumblingWindowExpression(this, context);
  }

  @Override
  public String toString() {
    return " TUMBLING ( SIZE " + size + " " + sizeUnit + " ) ";
  }

  @Override
  public int hashCode() {
    return Objects.hash(size, sizeUnit);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TumblingWindowExpression tumblingWindowExpression = (TumblingWindowExpression) o;
    return tumblingWindowExpression.size == size && tumblingWindowExpression.sizeUnit == sizeUnit;
  }

  @SuppressWarnings("unchecked")
  @Override
  public KTable applyAggregate(final KGroupedStream groupedStream,
      final Initializer initializer,
      final UdafAggregator aggregator,
      final Materialized<String, GenericRow, ?> materialized) {

    final TimeWindows windows = TimeWindows.of(Duration.ofMillis(sizeUnit.toMillis(size)));

    return groupedStream
        .windowedBy(windows)
        .aggregate(initializer, aggregator, materialized);

  }

  @Override
  public <K> SerdeFactory<Windowed<K>> getKeySerdeFactory(final Class<K> innerType) {
    return () -> WindowedSerdes.timeWindowedSerdeFrom(innerType);
  }
}