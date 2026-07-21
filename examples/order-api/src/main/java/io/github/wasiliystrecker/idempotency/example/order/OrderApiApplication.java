package io.github.wasiliystrecker.idempotency.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Runnable reference application for the idempotency starter. */
@SpringBootApplication
public class OrderApiApplication {
  /**
   * Starts the HTTP API.
   *
   * @param args Spring Boot arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(OrderApiApplication.class, args);
  }
}
