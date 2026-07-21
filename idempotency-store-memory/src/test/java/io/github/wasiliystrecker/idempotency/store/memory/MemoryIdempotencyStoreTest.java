package io.github.wasiliystrecker.idempotency.store.memory;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wasiliystrecker.idempotency.core.AcquireRequest;
import io.github.wasiliystrecker.idempotency.core.Acquisition;
import io.github.wasiliystrecker.idempotency.core.FailurePolicy;
import io.github.wasiliystrecker.idempotency.core.IdempotencyCommand;
import io.github.wasiliystrecker.idempotency.core.IdempotencyKey;
import io.github.wasiliystrecker.idempotency.core.IdempotencyStore;
import io.github.wasiliystrecker.idempotency.core.RequestFingerprint;
import io.github.wasiliystrecker.idempotency.testkit.AdjustableClock;
import io.github.wasiliystrecker.idempotency.testkit.IdempotencyStoreContract;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class MemoryIdempotencyStoreTest extends IdempotencyStoreContract {
  private AdjustableClock clock;
  private MemoryIdempotencyStore store;

  @Override
  protected IdempotencyStore newStore() {
    clock = new AdjustableClock(Instant.parse("2026-07-21T08:00:00Z"));
    store = new MemoryIdempotencyStore(clock);
    return store;
  }

  @Override
  protected void elapse(Duration duration) {
    clock.advance(duration);
  }

  @Test
  void reportsTheLocalEntryCount() {
    assertThat(store.size()).isZero();
  }

  @Test
  void grantsExactlyOneOwnerUnderVirtualThreadContention() throws Exception {
    int contenders = 32;
    CountDownLatch ready = new CountDownLatch(contenders);
    CountDownLatch start = new CountDownLatch(1);
    IdempotencyCommand command =
        new IdempotencyCommand(
            IdempotencyKey.of("orders", "concurrent-memory-key"),
            RequestFingerprint.sha256("same-request"),
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            FailurePolicy.RETAIN);
    List<Future<Acquisition>> futures = new ArrayList<>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int index = 0; index < contenders; index++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return store.acquire(new AcquireRequest(command, UUID.randomUUID()));
                }));
      }
      ready.await();
      start.countDown();

      List<Acquisition> decisions = new ArrayList<>();
      for (Future<Acquisition> future : futures) {
        decisions.add(future.get());
      }
      assertThat(decisions.stream().filter(Acquisition.Acquired.class::isInstance)).hasSize(1);
      assertThat(decisions.stream().filter(Acquisition.InProgress.class::isInstance)).hasSize(31);
    }
  }
}
