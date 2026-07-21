package io.github.wasiliystrecker.idempotency.spring;

/** Raised when an annotated invocation cannot be converted into a safe idempotency command. */
public final class IdempotencyInvocationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates a sanitized invocation failure without retaining request data.
   *
   * @param message safe diagnostic
   */
  public IdempotencyInvocationException(String message) {
    super(message);
  }
}
