package io.github.wasiliystrecker.idempotency.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Store command for atomic ownership acquisition.
 *
 * @param command operation identity and policy
 * @param ownerToken proposed compare-and-set owner token
 */
public record AcquireRequest(IdempotencyCommand command, UUID ownerToken) {
  /** Validates acquisition input. */
  public AcquireRequest {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(ownerToken, "ownerToken");
  }
}
