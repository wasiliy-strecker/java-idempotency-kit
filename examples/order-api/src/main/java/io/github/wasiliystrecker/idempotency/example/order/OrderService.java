package io.github.wasiliystrecker.idempotency.example.order;

import io.github.wasiliystrecker.idempotency.core.FailurePolicy;
import io.github.wasiliystrecker.idempotency.spring.Idempotent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService {
  private final OrderRepository repository;
  private final Clock clock;

  OrderService(OrderRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  @Idempotent(
      namespace = "orders.create",
      key = "#idempotencyKey",
      fingerprint = "#request",
      resultVersion = "v1",
      failurePolicy = FailurePolicy.RETAIN)
  public OrderResponse create(String idempotencyKey, CreateOrderRequest request) {
    BigDecimal totalPrice = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
    OrderResponse order =
        new OrderResponse(
            UUID.randomUUID(),
            request.customerReference(),
            request.sku(),
            request.quantity(),
            request.unitPrice(),
            totalPrice,
            "CREATED",
            Instant.now(clock));
    repository.insert(order);
    return order;
  }
}
