package io.github.wasiliystrecker.idempotency.core;

/**
 * Encodes a successful operation result for storage and decodes it during replay.
 *
 * @param <T> result value type
 */
public interface ResultCodec<T> {
  /**
   * Returns a stable codec and schema identifier.
   *
   * @return identifier with at most 128 printable characters
   */
  String id();

  /**
   * Encodes a successful result.
   *
   * @param value result returned by the operation
   * @return serialized bytes
   * @throws Exception when serialization fails
   */
  byte[] encode(T value) throws Exception;

  /**
   * Decodes a stored result.
   *
   * @param payload defensive payload copy
   * @return reconstructed result
   * @throws Exception when deserialization fails
   */
  T decode(byte[] payload) throws Exception;
}
