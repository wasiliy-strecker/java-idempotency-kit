package io.github.wasiliystrecker.idempotency.core;

/** Raised when a store-wide maintenance operation cannot complete. */
public final class IdempotencyMaintenanceException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a sanitized maintenance failure.
   *
   * @param operation safe operation name
   * @param cause infrastructure cause
   */
  public IdempotencyMaintenanceException(String operation, Throwable cause) {
    super("Idempotency store " + operation + " failed", cause);
  }
}
