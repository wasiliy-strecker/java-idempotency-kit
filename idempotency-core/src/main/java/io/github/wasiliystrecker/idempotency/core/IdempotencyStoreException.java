package io.github.wasiliystrecker.idempotency.core;

/** Raised when an idempotency store cannot complete an infrastructure operation. */
public final class IdempotencyStoreException extends IdempotencyException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a store failure.
   *
   * @param operation safe infrastructure operation name
   * @param key safe hashed identity
   * @param cause infrastructure cause
   */
  public IdempotencyStoreException(String operation, IdempotencyKey key, Throwable cause) {
    super(
        "Idempotency store " + operation + " failed for key reference " + key.reference(),
        key,
        cause);
  }
}
