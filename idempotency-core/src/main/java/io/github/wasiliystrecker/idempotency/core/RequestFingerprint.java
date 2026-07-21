package io.github.wasiliystrecker.idempotency.core;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * SHA-256 request identity used to reject reuse of a key for different work.
 *
 * @param digest lowercase SHA-256 digest
 */
public record RequestFingerprint(String digest) {
  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

  /** Validates the digest representation. */
  public RequestFingerprint {
    Objects.requireNonNull(digest, "digest");
    if (!SHA_256.matcher(digest).matches()) {
      throw new IllegalArgumentException("fingerprint must be a lowercase SHA-256 value");
    }
  }

  /**
   * Creates a fingerprint from canonical request bytes.
   *
   * @param canonicalRequest deterministic request representation
   * @return SHA-256 fingerprint
   */
  public static RequestFingerprint sha256(byte[] canonicalRequest) {
    return new RequestFingerprint(
        Digests.sha256(Objects.requireNonNull(canonicalRequest, "canonicalRequest").clone()));
  }

  /**
   * Creates a fingerprint from a canonical UTF-8 string.
   *
   * @param canonicalRequest deterministic request representation
   * @return SHA-256 fingerprint
   */
  public static RequestFingerprint sha256(String canonicalRequest) {
    return sha256(
        Objects.requireNonNull(canonicalRequest, "canonicalRequest")
            .getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Returns a short safe diagnostic reference.
   *
   * @return first twelve hexadecimal digest characters
   */
  public String reference() {
    return digest.substring(0, 12);
  }

  @Override
  public String toString() {
    return "RequestFingerprint[reference=" + reference() + "]";
  }
}
