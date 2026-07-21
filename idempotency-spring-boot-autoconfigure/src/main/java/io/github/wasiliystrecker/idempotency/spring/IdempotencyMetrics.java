package io.github.wasiliystrecker.idempotency.spring;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

final class IdempotencyMetrics {
  private final MeterRegistry registry;

  IdempotencyMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  void recordOperation(String namespace, String outcome, long elapsedNanos) {
    if (registry == null) {
      return;
    }
    try {
      registry
          .counter("idempotency.operations", "namespace", namespace, "outcome", outcome)
          .increment();
      registry
          .timer("idempotency.operation.duration", "namespace", namespace, "outcome", outcome)
          .record(elapsedNanos, TimeUnit.NANOSECONDS);
    } catch (RuntimeException ignored) {
      // Observability must never change operation behavior.
    }
  }

  void recordCleanup(int removed, Duration elapsed) {
    if (registry == null) {
      return;
    }
    try {
      registry.counter("idempotency.cleanup.records").increment(removed);
      registry.timer("idempotency.cleanup.duration").record(elapsed);
    } catch (RuntimeException ignored) {
      // Observability must never stop cleanup scheduling.
    }
  }
}
