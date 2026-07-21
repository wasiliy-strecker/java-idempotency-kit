package io.github.wasiliystrecker.idempotency.core;

/** Raised when stored bytes were produced by a different result schema codec. */
public final class ResultCodecMismatchException extends IdempotencyException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a safe codec mismatch failure.
   *
   * @param key safe hashed identity
   */
  public ResultCodecMismatchException(IdempotencyKey key) {
    super("Stored result codec does not match for key reference " + key.reference(), key);
  }
}
