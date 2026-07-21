package io.github.wasiliystrecker.idempotency.example.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Validated order input used as the request fingerprint.
 *
 * @param customerReference caller's order reference
 * @param sku product identifier
 * @param quantity requested units
 * @param unitPrice price per unit
 */
public record CreateOrderRequest(
    @NotBlank @Size(max = 80) String customerReference,
    @NotBlank @Size(max = 80) String sku,
    @Min(1) @Max(10_000) int quantity,
    @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal unitPrice) {}
