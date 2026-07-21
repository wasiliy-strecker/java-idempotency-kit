package io.github.wasiliystrecker.idempotency.example.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted and replayable successful result.
 *
 * @param id generated order identity
 * @param customerReference caller's reference
 * @param sku product identifier
 * @param quantity ordered units
 * @param unitPrice price per unit
 * @param totalPrice calculated total
 * @param status initial workflow status
 * @param createdAt creation instant
 */
public record OrderResponse(
    UUID id,
    String customerReference,
    String sku,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal totalPrice,
    String status,
    Instant createdAt) {}
