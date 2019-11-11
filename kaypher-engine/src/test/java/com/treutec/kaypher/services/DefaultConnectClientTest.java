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

package com.treutec.kaypher.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.treutec.kaypher.json.JsonMapper;
import com.treutec.kaypher.metastore.model.MetaStoreMatchers.OptionalMatchers;
import com.treutec.kaypher.services.ConnectClient.ConnectResponse;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorInfo;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorStateInfo;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorStateInfo.ConnectorState;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorStateInfo.TaskState;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorType;
import org.apache.kafka.connect.util.ConnectorTaskId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DefaultConnectClientTest {

  private static final ObjectMapper MAPPER = JsonMapper.INSTANCE.mapper;
  private static final ConnectorInfo SAMPLE_INFO = new ConnectorInfo(
      "foo",
      ImmutableMap.of("key", "value"),
      ImmutableList.of(new ConnectorTaskId("foo", 1)),
      ConnectorType.SOURCE
  );
  private static final ConnectorStateInfo SAMPLE_STATUS = new ConnectorStateInfo(
      "foo",
      new ConnectorState("state", "worker", "msg"),
      ImmutableList.of(
          new TaskState(0, "taskState", "worker", "taskMsg")
      ),
      ConnectorType.SOURCE
  );
  private static final String AUTH_HEADER = "Basic FOOBAR";

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(
      WireMockConfiguration.wireMockConfig()
          .dynamicPort()
  );

  private ConnectClient client;

  @Before
  public void setup() {
    client = new DefaultConnectClient("http://localhost:" + wireMockRule.port(), Optional.of(AUTH_HEADER));
  }

  @Test
  public void testCreate() throws JsonProcessingException {
    // Given:
    WireMock.stubFor(
        WireMock.post(WireMock.urlEqualTo("/connectors"))
            .withHeader(HttpHeaders.AUTHORIZATION, new EqualToPattern(AUTH_HEADER))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.SC_CREATED)
                .withBody(MAPPER.writeValueAsString(SAMPLE_INFO)))
    );

    // When:
    final ConnectResponse<ConnectorInfo> response =
        client.create("foo", ImmutableMap.of());

    // Then:
    assertThat(response.datum(), OptionalMatchers.of(is(SAMPLE_INFO)));
    assertThat("Expected no error!", !response.error().isPresent());
  }

  @Test
  public void testCreateWithError() throws JsonProcessingException {
    // Given:
    WireMock.stubFor(
        WireMock.post(WireMock.urlEqualTo("/connectors"))
            .withHeader(HttpHeaders.AUTHORIZATION, new EqualToPattern(AUTH_HEADER))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .withBody("Oh no!"))
    );

    // When:
    final ConnectResponse<ConnectorInfo> response =
        client.create("foo", ImmutableMap.of());

    // Then:
    assertThat("Expected no datum!", !response.datum().isPresent());
    assertThat(response.error(), OptionalMatchers.of(is("Oh no!")));
  }

  @Test
  public void testList() throws JsonProcessingException {
    // Given:
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/connectors"))
            .withHeader(HttpHeaders.AUTHORIZATION, new EqualToPattern(AUTH_HEADER))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.SC_OK)
                .withBody(MAPPER.writeValueAsString(ImmutableList.of("one", "two"))))
    );

    // When:
    final ConnectResponse<List<String>> response = client.connectors();

    // Then:
    assertThat(response.datum(), OptionalMatchers.of(is(ImmutableList.of("one", "two"))));
    assertThat("Expected no error!", !response.error().isPresent());
  }

  @Test
  public void testDescribe() throws JsonProcessingException {
    // Given:
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/connectors/foo"))
            .withHeader(HttpHeaders.AUTHORIZATION, new EqualToPattern(AUTH_HEADER))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.SC_OK)
                .withBody(MAPPER.writeValueAsString(SAMPLE_INFO)))
    );

    // When:
    final ConnectResponse<ConnectorInfo> response = client.describe("foo");

    // Then:
    assertThat(response.datum(), OptionalMatchers.of(is(SAMPLE_INFO)));
    assertThat("Expected no error!", !response.error().isPresent());
  }

  @Test
  public void testStatus() throws JsonProcessingException {
    // Given:
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/connectors/foo/status"))
            .withHeader(HttpHeaders.AUTHORIZATION, new EqualToPattern(AUTH_HEADER))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.SC_OK)
                .withBody(MAPPER.writeValueAsString(SAMPLE_STATUS)))
    );

    // When:
    final ConnectResponse<ConnectorStateInfo> response = client.status("foo");

    // Then:
    final ConnectorStateInfo connectorStateInfo = response.datum().get();
    // equals is not implemented on ConnectorStateInfo
    assertThat(connectorStateInfo.name(), is(SAMPLE_STATUS.name()));
    assertThat(connectorStateInfo.type(), is(SAMPLE_STATUS.type()));
    assertThat(connectorStateInfo.connector().state(), is(SAMPLE_STATUS.connector().state()));
    assertThat(connectorStateInfo.connector().workerId(), is(SAMPLE_STATUS.connector().workerId()));
    assertThat(connectorStateInfo.connector().trace(), is(SAMPLE_STATUS.connector().trace()));
    assertThat(connectorStateInfo.tasks().size(), is(SAMPLE_STATUS.tasks().size()));
    assertThat(connectorStateInfo.tasks().get(0).id(), is(SAMPLE_STATUS.tasks().get(0).id()));
    assertThat("Expected no error!", !response.error().isPresent());
  }

  @Test
  public void testDelete() throws JsonProcessingException {
    // Given:
    WireMock.stubFor(
        WireMock.delete(WireMock.urlEqualTo("/connectors/foo"))
            .withHeader(HttpHeaders.AUTHORIZATION, new EqualToPattern(AUTH_HEADER))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.SC_NO_CONTENT))
    );

    // When:
    final ConnectResponse<String> response = client.delete("foo");

    // Then:
    assertThat(response.datum(), OptionalMatchers.of(is("foo")));
    assertThat("Expected no error!", !response.error().isPresent());
  }

  @Test
  public void testListShouldRetryOnFailure() throws JsonProcessingException {
    // Given:
    WireMock.stubFor(
        WireMock.get(WireMock.urlEqualTo("/connectors"))
            .withHeader(HttpHeaders.AUTHORIZATION, new EqualToPattern(AUTH_HEADER))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .withBody("Encountered an error!"))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.SC_OK)
                .withBody(MAPPER.writeValueAsString(ImmutableList.of("one", "two"))))
    );

    // When:
    final ConnectResponse<List<String>> response = client.connectors();

    // Then:
    assertThat(response.datum(), OptionalMatchers.of(is(ImmutableList.of("one", "two"))));
    assertThat("Expected no error!", !response.error().isPresent());
  }

}