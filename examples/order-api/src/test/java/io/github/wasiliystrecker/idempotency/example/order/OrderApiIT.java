package io.github.wasiliystrecker.idempotency.example.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
    classes = OrderApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class OrderApiIT {
  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.3-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void replaysTheFirstResponseWithoutCreatingAnotherOrder() throws Exception {
    String payload =
        """
        {"customerReference":"customer-42","sku":"keyboard","quantity":2,"unitPrice":49.90}
        """;

    HttpResponse<String> first = post("integration-request-1", payload);
    HttpResponse<String> replay = post("integration-request-1", payload);

    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(replay.statusCode()).isEqualTo(201);
    assertThat(replay.body()).isEqualTo(first.body());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM customer_order WHERE customer_reference = ?",
                Integer.class,
                "customer-42"))
        .isEqualTo(1);
  }

  @Test
  void reportsAProblemWhenTheSameKeyIdentifiesDifferentWork() throws Exception {
    String firstPayload =
        """
        {"customerReference":"customer-99","sku":"monitor","quantity":1,"unitPrice":199.00}
        """;
    String changedPayload =
        """
        {"customerReference":"customer-99","sku":"monitor","quantity":2,"unitPrice":199.00}
        """;
    assertThat(post("integration-request-2", firstPayload).statusCode()).isEqualTo(201);

    HttpResponse<String> conflict = post("integration-request-2", changedPayload);

    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(conflict.body()).contains("Idempotency key conflict");
  }

  private HttpResponse<String> post(String idempotencyKey, String payload) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/orders"))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
  }
}
