package io.github.wasiliystrecker.idempotency.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable policy for one idempotent operation.
 *
 * @param key safe operation identity
 * @param fingerprint request payload fingerprint
 * @param retention duration for which key reuse remains protected
 * @param lease exclusive processing duration before takeover is allowed
 * @param failurePolicy explicit behavior after an operation exception
 */
public record IdempotencyCommand(
    IdempotencyKey key,
    RequestFingerprint fingerprint,
    Duration retention,
    Duration lease,
    FailurePolicy failurePolicy) {

  /** Validates durations and required policy values. */
  public IdempotencyCommand {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(fingerprint, "fingerprint");
    Objects.requireNonNull(retention, "retention");
    Objects.requireNonNull(lease, "lease");
    Objects.requireNonNull(failurePolicy, "failurePolicy");
    if (lease.compareTo(Duration.ofMillis(100)) < 0) {
      throw new IllegalArgumentException("lease must be at least PT0.1S");
    }
    if (retention.compareTo(lease) <= 0) {
      throw new IllegalArgumentException("retention must be longer than lease");
    }
    if (lease.compareTo(Duration.ofDays(1)) > 0) {
      throw new IllegalArgumentException("lease must not exceed P1D");
    }
    if (retention.compareTo(Duration.ofDays(365)) > 0) {
      throw new IllegalArgumentException("retention must not exceed P365D");
    }
  }
}
