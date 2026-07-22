package io.github.wasiliystrecker.idempotency.example.order;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Runnable reference application for the idempotency starter. */
@SpringBootApplication
public class OrderApiApplication {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Starts the HTTP API.
   *
   * @param args Spring Boot arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(OrderApiApplication.class, args);
  }
}
