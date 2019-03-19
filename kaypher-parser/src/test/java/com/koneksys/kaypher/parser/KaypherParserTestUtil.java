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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import com.koneksys.kaypher.metastore.MetaStore;
import com.koneksys.kaypher.parser.KaypherParser.PreparedStatement;
import com.koneksys.kaypher.parser.tree.Statement;
import java.util.List;
import java.util.stream.Collectors;

public final class KaypherParserTestUtil {

  private static final KaypherParser KSQL_PARSER = new DefaultKaypherParser();

  private KaypherParserTestUtil() {
  }

  @SuppressWarnings("unchecked")
  public static <T extends Statement> PreparedStatement<T> buildSingleAst(
      final String sql,
      final MetaStore metaStore
  ) {
    final List<PreparedStatement<?>> statements = buildAst(sql, metaStore);
    assertThat(statements, hasSize(1));
    return (PreparedStatement<T>)statements.get(0);
  }

  public static List<PreparedStatement<?>> buildAst(final String sql, final MetaStore metaStore) {
    return KSQL_PARSER.parse(sql).stream()
        .map(parsed -> KSQL_PARSER.prepare(parsed, metaStore))
        .collect(Collectors.toList());
  }
}