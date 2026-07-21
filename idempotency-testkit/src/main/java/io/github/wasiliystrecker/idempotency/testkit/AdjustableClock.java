package io.github.wasiliystrecker.idempotency.testkit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/** Mutable UTC clock for deterministic lease and retention tests. */
public final class AdjustableClock extends Clock {
  private Instant instant;

  /**
   * Creates a clock at the supplied instant.
   *
   * @param instant initial time
   */
  public AdjustableClock(Instant instant) {
    this.instant = Objects.requireNonNull(instant, "instant");
  }

  /**
   * Moves the clock forward.
   *
   * @param duration nonnegative elapsed time
   */
  public synchronized void advance(Duration duration) {
    Objects.requireNonNull(duration, "duration");
    if (duration.isNegative()) {
      throw new IllegalArgumentException("duration must not be negative");
    }
    instant = instant.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    if (!ZoneOffset.UTC.equals(Objects.requireNonNull(zone, "zone"))) {
      throw new IllegalArgumentException("AdjustableClock supports UTC only");
    }
    return this;
  }

  @Override
  public synchronized Instant instant() {
    return instant;
  }
}
