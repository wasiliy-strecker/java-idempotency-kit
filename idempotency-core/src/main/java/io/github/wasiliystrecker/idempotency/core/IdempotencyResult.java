package io.github.wasiliystrecker.idempotency.core;

import java.util.Objects;

/**
 * Successful execution or replay result.
 *
 * @param value operation value, which may be {@code null} when the codec supports it
 * @param outcome whether this call executed or replayed work
 * @param <T> result value type
 */
public record IdempotencyResult<T>(T value, IdempotencyOutcome outcome) {
  /** Validates the outcome. */
  public IdempotencyResult {
    Objects.requireNonNull(outcome, "outcome");
  }
}
