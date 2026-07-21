package io.github.wasiliystrecker.idempotency.core;

/**
 * Operation that may report a checked failure.
 *
 * @param <T> supplied value type
 */
@FunctionalInterface
public interface CheckedSupplier<T> {
  /**
   * Performs the operation.
   *
   * @return successful result
   * @throws Exception application failure
   */
  T get() throws Exception;
}
