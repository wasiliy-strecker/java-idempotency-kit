package io.github.wasiliystrecker.idempotency.core;

import java.time.Instant;
import java.util.Objects;

/** Raised when another owner still holds an unexpired processing lease. */
public final class IdempotencyInProgressException extends IdempotencyException {
  private static final long serialVersionUID = 1L;

  /** Earliest known instant at which lease takeover can be attempted. */
  private final Instant retryAfter;

  /**
   * Creates an in-progress failure.
   *
   * @param key safe hashed identity
   * @param retryAfter earliest known takeover instant
   */
  public IdempotencyInProgressException(IdempotencyKey key, Instant retryAfter) {
    super("Idempotent operation is already in progress for key reference " + key.reference(), key);
    this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
  }

  /**
   * Returns the earliest known retry instant.
   *
   * @return lease deadline
   */
  public Instant retryAfter() {
    return retryAfter;
  }
}
