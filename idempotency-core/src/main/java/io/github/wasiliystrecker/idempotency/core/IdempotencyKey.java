package io.github.wasiliystrecker.idempotency.core;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Safe lookup identity containing a public namespace and the SHA-256 digest of a raw key.
 *
 * @param namespace stable low-cardinality operation namespace
 * @param digest lowercase SHA-256 digest; the raw key is deliberately not retained
 */
public record IdempotencyKey(String namespace, String digest) {
  private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

  /** Validates a safe key identity. */
  public IdempotencyKey {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(digest, "digest");
    if (!NAMESPACE.matcher(namespace).matches()) {
      throw new IllegalArgumentException(
          "namespace must be 1-128 lowercase letters, digits, dots, underscores, or hyphens");
    }
    if (!SHA_256.matcher(digest).matches()) {
      throw new IllegalArgumentException("digest must be a lowercase SHA-256 value");
    }
  }

  /**
   * Hashes a raw client key and immediately discards it.
   *
   * @param namespace stable operation namespace
   * @param rawKey nonblank client key with at most 512 characters
   * @return safe identity that does not retain the raw key
   */
  public static IdempotencyKey of(String namespace, String rawKey) {
    Objects.requireNonNull(rawKey, "rawKey");
    if (rawKey.isBlank() || rawKey.length() > 512) {
      throw new IllegalArgumentException("rawKey must contain 1-512 nonblank characters");
    }
    return new IdempotencyKey(namespace, Digests.sha256(rawKey.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Returns a short diagnostic reference that cannot recover the raw key.
   *
   * @return first twelve hexadecimal digest characters
   */
  public String reference() {
    return digest.substring(0, 12);
  }

  @Override
  public String toString() {
    return "IdempotencyKey[namespace=" + namespace + ", reference=" + reference() + "]";
  }
}
