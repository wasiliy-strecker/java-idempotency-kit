package io.github.wasiliystrecker.idempotency.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Exclusive store ownership required to finish an acquired operation.
 *
 * @param key safe operation identity
 * @param fingerprint request fingerprint that acquired ownership
 * @param ownerToken unguessable compare-and-set token
 * @param expiresAt database or store lease deadline
 */
public record Lease(
    IdempotencyKey key, RequestFingerprint fingerprint, UUID ownerToken, Instant expiresAt) {

  /** Validates lease identity. */
  public Lease {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(fingerprint, "fingerprint");
    Objects.requireNonNull(ownerToken, "ownerToken");
    Objects.requireNonNull(expiresAt, "expiresAt");
  }
}
