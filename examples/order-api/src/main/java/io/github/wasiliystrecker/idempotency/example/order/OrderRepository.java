package io.github.wasiliystrecker.idempotency.example.order;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class OrderRepository {
  private final JdbcTemplate jdbcTemplate;

  OrderRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  void insert(OrderResponse order) {
    jdbcTemplate.update(
        """
        INSERT INTO customer_order (
            id, customer_reference, sku, quantity, unit_price,
            total_price, status, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        order.id(),
        order.customerReference(),
        order.sku(),
        order.quantity(),
        order.unitPrice(),
        order.totalPrice(),
        order.status(),
        Timestamp.from(order.createdAt()));
  }
}
