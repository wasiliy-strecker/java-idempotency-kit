package io.github.wasiliystrecker.idempotency.core;

import java.time.Instant;
import java.util.Objects;

/** Atomic decision returned by an {@link IdempotencyStore}. */
public sealed interface Acquisition
    permits Acquisition.Acquired,
        Acquisition.Replay,
        Acquisition.InProgress,
        Acquisition.Conflict,
        Acquisition.PreviousFailure {

  /**
   * Grants exclusive execution ownership.
   *
   * @param lease token required for terminal transitions
   */
  record Acquired(Lease lease) implements Acquisition {
    /** Validates the lease. */
    public Acquired {
      Objects.requireNonNull(lease, "lease");
    }
  }

  /**
   * Returns a completed serialized result.
   *
   * @param result stored result to decode
   */
  record Replay(StoredResult result) implements Acquisition {
    /** Validates the stored result. */
    public Replay {
      Objects.requireNonNull(result, "result");
    }
  }

  /**
   * Reports a currently owned operation.
   *
   * @param retryAfter earliest known lease takeover time
   */
  record InProgress(Instant retryAfter) implements Acquisition {
    /** Validates the retry timestamp. */
    public InProgress {
      Objects.requireNonNull(retryAfter, "retryAfter");
    }
  }

  /** Reports reuse of the same key with a different request fingerprint. */
  record Conflict() implements Acquisition {}

  /** Reports a retained previous failure for the same key and fingerprint. */
  record PreviousFailure() implements Acquisition {}
}
