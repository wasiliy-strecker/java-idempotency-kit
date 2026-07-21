package io.github.wasiliystrecker.idempotency.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Store command that records a safe failure without retaining exception details.
 *
 * @param lease current ownership token
 * @param policy whether a later identical request may retry
 * @param retention failed-record retention
 */
public record FailureRequest(Lease lease, FailurePolicy policy, Duration retention) {
  /** Validates failure input. */
  public FailureRequest {
    Objects.requireNonNull(lease, "lease");
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(retention, "retention");
    if (retention.isNegative() || retention.isZero()) {
      throw new IllegalArgumentException("retention must be positive");
    }
  }
}
