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

package com.koneksys.kaypher.util.timestamp;

import com.koneksys.kaypher.ddl.DdlConfig;
import com.koneksys.kaypher.util.KsqlException;
import com.koneksys.kaypher.util.SchemaUtil;
import com.koneksys.kaypher.util.StringUtil;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;

public final class TimestampExtractionPolicyFactory {

  private TimestampExtractionPolicyFactory() {
  }

  public static TimestampExtractionPolicy create(
      final Schema schema,
      final String timestampColumnName,
      final String timestampFormat) {
    if (timestampColumnName == null) {
      return new MetadataTimestampExtractionPolicy();
    }

    final String fieldName = StringUtil.cleanQuotes(timestampColumnName.toUpperCase());
    final Field timestampField = SchemaUtil.getFieldByName(schema,
        fieldName)
        .orElseThrow(() -> new KsqlException(String.format(
            "No column with the provided timestamp column name in the "
                + "WITH clause, %s, exists in the defined schema.",
            fieldName
        )));

    final Schema.Type timestampFieldType = timestampField.schema().type();
    if (timestampFieldType == Schema.Type.STRING) {
      if (timestampFormat == null) {
        throw new KsqlException("A String timestamp field has been specified without"
            + " also specifying the "
            + DdlConfig.TIMESTAMP_FORMAT_PROPERTY.toLowerCase());
      }
      return new StringTimestampExtractionPolicy(
          fieldName,
          StringUtil.cleanQuotes(timestampFormat));
    }

    if (timestampFieldType == Schema.Type.INT64) {
      return new LongColumnTimestampExtractionPolicy(fieldName);
    }

    throw new KsqlException(
        "Timestamp column, " + timestampColumnName + ", should be LONG(INT64)"
            + " or a String with a "
            + DdlConfig.TIMESTAMP_FORMAT_PROPERTY.toLowerCase()
            + " specified");
  }

}
