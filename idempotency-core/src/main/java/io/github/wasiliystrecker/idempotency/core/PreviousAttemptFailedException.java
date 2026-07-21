package io.github.wasiliystrecker.idempotency.core;

/** Raised when a prior failure was deliberately retained. */
public final class PreviousAttemptFailedException extends IdempotencyException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a retained-failure response.
   *
   * @param key safe hashed identity
   */
  public PreviousAttemptFailedException(IdempotencyKey key) {
    super("A previous attempt is retained for key reference " + key.reference(), key);
  }
}
