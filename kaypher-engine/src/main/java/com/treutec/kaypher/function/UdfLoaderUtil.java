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
package com.treutec.kaypher.function;

import com.treutec.kaypher.function.udf.UdfMetadata;
import java.io.File;
import java.util.Optional;

public final class UdfLoaderUtil {
  private UdfLoaderUtil() {}

  public static FunctionRegistry load(final MutableFunctionRegistry functionRegistry) {
    new UserFunctionLoader(
        functionRegistry,
        new File("src/test/resources/udf-example.jar"),
        UdfLoaderUtil.class.getClassLoader(),
        value -> false, Optional.empty(), true
    )
        .load();

    return functionRegistry;
  }

  public static UdfFactory createTestUdfFactory(final KaypherScalarFunction udf) {
    final UdfMetadata metadata = new UdfMetadata(
        udf.getFunctionName().name(),
        udf.getDescription(),
        "Test Author",
        "",
        KaypherScalarFunction.INTERNAL_PATH,
        false);

    return new UdfFactory(udf.getKudfClass(), metadata);
  }
}
