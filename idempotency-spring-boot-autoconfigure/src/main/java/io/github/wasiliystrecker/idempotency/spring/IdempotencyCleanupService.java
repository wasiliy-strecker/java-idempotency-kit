package io.github.wasiliystrecker.idempotency.spring;

import io.github.wasiliystrecker.idempotency.core.IdempotencyStore;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

final class IdempotencyCleanupService implements AutoCloseable {
  private static final Log LOGGER = LogFactory.getLog(IdempotencyCleanupService.class);

  private final IdempotencyStore store;
  private final IdempotencyMetrics metrics;
  private final int batchSize;
  private final ScheduledExecutorService scheduler;

  IdempotencyCleanupService(
      IdempotencyStore store, IdempotencyMetrics metrics, Duration interval, int batchSize) {
    this.store = store;
    this.metrics = metrics;
    this.batchSize = batchSize;
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("idempotency-cleanup").factory());
    long delayMillis = interval.toMillis();
    scheduler.scheduleWithFixedDelay(
        this::purgeSafely, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
  }

  private void purgeSafely() {
    long started = System.nanoTime();
    try {
      int removed = store.purgeExpired(batchSize);
      metrics.recordCleanup(removed, Duration.ofNanos(System.nanoTime() - started));
    } catch (RuntimeException failure) {
      LOGGER.warn("Idempotency cleanup failed; the next scheduled run will retry", failure);
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }
}
