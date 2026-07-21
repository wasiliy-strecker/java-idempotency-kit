package io.github.wasiliystrecker.idempotency.core;

/** Raised when a stored result cannot be safely reconstructed. */
public final class ResultDecodingException extends IdempotencyException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a sanitized decoding failure without retaining payload-bearing causes.
   *
   * @param key safe hashed identity
   */
  public ResultDecodingException(IdempotencyKey key) {
    super("Stored result decoding failed for key reference " + key.reference(), key);
  }
}
