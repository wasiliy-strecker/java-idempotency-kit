package io.github.wasiliystrecker.idempotency.core;

/** Raised when a successful application result cannot be safely stored. */
public final class ResultEncodingException extends IdempotencyException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a sanitized encoding failure without retaining a potentially unsafe codec cause.
   *
   * @param key safe hashed identity
   */
  public ResultEncodingException(IdempotencyKey key) {
    super("Result encoding failed for key reference " + key.reference(), key);
  }
}
