package io.github.wasiliystrecker.idempotency.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class IdentityTest {
  @Test
  void hashesAndDiscardsRawKeys() {
    IdempotencyKey key = IdempotencyKey.of("orders.create", "customer-secret-42");

    assertThat(key.digest()).hasSize(64);
    assertThat(key.toString()).doesNotContain("customer-secret-42");
    assertThat(key.reference()).hasSize(12);
  }

  @Test
  void defensivelyCopiesFingerprintInput() {
    byte[] request = {1, 2, 3};
    RequestFingerprint fingerprint = RequestFingerprint.sha256(request);
    request[0] = 9;

    assertThat(fingerprint).isEqualTo(RequestFingerprint.sha256(new byte[] {1, 2, 3}));
  }

  @Test
  void rejectsUnsafeNamespaces() {
    assertThatThrownBy(() -> IdempotencyKey.of("Orders/Create", "key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("namespace");
  }
}
