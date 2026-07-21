package io.github.wasiliystrecker.idempotency.core;

import java.util.Arrays;
import java.util.Objects;

/**
 * Serialized completed result.
 *
 * @param codecId codec and schema identifier
 * @param payload serialized result bytes
 */
public record StoredResult(String codecId, byte[] payload) {
  /** Makes the payload immutable and validates the codec identifier. */
  public StoredResult {
    Objects.requireNonNull(codecId, "codecId");
    Objects.requireNonNull(payload, "payload");
    if (codecId.isBlank()
        || codecId.length() > 128
        || codecId.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("codecId must contain 1-128 printable characters");
    }
    payload = payload.clone();
  }

  /**
   * Returns a defensive copy of serialized result bytes.
   *
   * @return copied payload
   */
  @Override
  public byte[] payload() {
    return payload.clone();
  }

  @Override
  public boolean equals(Object candidate) {
    return this == candidate
        || (candidate instanceof StoredResult other
            && codecId.equals(other.codecId)
            && Arrays.equals(payload, other.payload));
  }

  @Override
  public int hashCode() {
    return 31 * codecId.hashCode() + Arrays.hashCode(payload);
  }

  @Override
  public String toString() {
    return "StoredResult[codecId=" + codecId + ", payloadBytes=" + payload.length + "]";
  }
}
