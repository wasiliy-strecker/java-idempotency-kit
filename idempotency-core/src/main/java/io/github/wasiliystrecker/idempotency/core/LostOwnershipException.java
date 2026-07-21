package io.github.wasiliystrecker.idempotency.core;

/** Raised when a stale owner attempts a terminal store transition. */
public final class LostOwnershipException extends IdempotencyException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a lost-lease failure.
   *
   * @param key safe hashed identity
   */
  public LostOwnershipException(IdempotencyKey key) {
    super("Processing ownership was lost for key reference " + key.reference(), key);
  }
}
