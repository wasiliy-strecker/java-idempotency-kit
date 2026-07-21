package io.github.wasiliystrecker.idempotency.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Store command that completes an owned operation.
 *
 * @param lease current ownership token
 * @param result serialized successful result
 * @param retention renewed terminal retention
 */
public record CompletionRequest(Lease lease, StoredResult result, Duration retention) {
  /** Validates completion input. */
  public CompletionRequest {
    Objects.requireNonNull(lease, "lease");
    Objects.requireNonNull(result, "result");
    requirePositive(retention);
  }

  private static void requirePositive(Duration value) {
    Objects.requireNonNull(value, "retention");
    if (value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException("retention must be positive");
    }
  }
}
