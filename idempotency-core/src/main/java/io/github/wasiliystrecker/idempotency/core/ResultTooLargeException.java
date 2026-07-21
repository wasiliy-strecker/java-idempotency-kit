package io.github.wasiliystrecker.idempotency.core;

/** Raised when a result exceeds the configured persistence bound. */
public final class ResultTooLargeException extends IdempotencyException {
  private static final long serialVersionUID = 1L;

  /** Rejected encoded byte count. */
  private final int actualBytes;

  /** Configured encoded byte limit. */
  private final int maximumBytes;

  /**
   * Creates a bounded-result failure.
   *
   * @param key safe hashed identity
   * @param actualBytes encoded result size
   * @param maximumBytes configured size bound
   */
  public ResultTooLargeException(IdempotencyKey key, int actualBytes, int maximumBytes) {
    super(
        "Encoded result exceeds " + maximumBytes + " bytes for key reference " + key.reference(),
        key);
    this.actualBytes = actualBytes;
    this.maximumBytes = maximumBytes;
  }

  /**
   * Returns the rejected payload size.
   *
   * @return actual encoded bytes
   */
  public int actualBytes() {
    return actualBytes;
  }

  /**
   * Returns the configured payload bound.
   *
   * @return maximum encoded bytes
   */
  public int maximumBytes() {
    return maximumBytes;
  }
}
