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

package com.treutec.kaypher.connect.supported;

import com.treutec.kaypher.connect.Connector;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorInfo;

/**
 * KAYPHER supports some "Blessed" connectors that we integrate well with. To be "blessed"
 * means that we can automatically import topics created by these connectors and that
 * we may provide templates that simplify configuration of these connectors.
 */
public interface SupportedConnector {

  /**
   * Constructs a {@link Connector} from the configuration given to us by connect.
   */
  Optional<Connector> fromConnectInfo(ConnectorInfo info);

  /**
   * Resolves a template configuration into something that connect can understand.
   */
  Map<String, String> resolveConfigs(Map<String, String> configs);

}
