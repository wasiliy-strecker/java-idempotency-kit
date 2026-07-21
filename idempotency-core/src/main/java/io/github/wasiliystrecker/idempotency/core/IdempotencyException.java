package io.github.wasiliystrecker.idempotency.core;

/** Base class for safe idempotency failures. */
public abstract class IdempotencyException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Static low-cardinality operation namespace. */
  private final String namespace;

  /** Short one-way digest reference suitable for diagnostics. */
  private final String keyReference;

  /**
   * Creates a failure without retaining raw request material.
   *
   * @param message safe diagnostic
   * @param key safe hashed identity
   */
  protected IdempotencyException(String message, IdempotencyKey key) {
    super(message);
    this.namespace = key.namespace();
    this.keyReference = key.reference();
  }

  /**
   * Creates a failure with an infrastructure cause.
   *
   * @param message safe diagnostic
   * @param key safe hashed identity
   * @param cause failure that must not contain raw request material
   */
  protected IdempotencyException(String message, IdempotencyKey key, Throwable cause) {
    super(message, cause);
    this.namespace = key.namespace();
    this.keyReference = key.reference();
  }

  /**
   * Returns the static operation namespace.
   *
   * @return operation namespace
   */
  public final String namespace() {
    return namespace;
  }

  /**
   * Returns a short digest reference instead of the raw key.
   *
   * @return safe key reference
   */
  public final String keyReference() {
    return keyReference;
  }
}
