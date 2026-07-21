package io.github.wasiliystrecker.idempotency.core;

/** Raised when a protected key is reused with a different request fingerprint. */
public final class IdempotencyKeyConflictException extends IdempotencyException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a payload conflict.
   *
   * @param key safe hashed identity
   */
  public IdempotencyKeyConflictException(IdempotencyKey key) {
    super("Idempotency key reference " + key.reference() + " belongs to a different request", key);
  }
}
