package io.github.wasiliystrecker.idempotency.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class IdempotencyExecutorTest {
  private static final UUID OWNER = UUID.fromString("9bb69b33-5bf0-4196-a6b3-f1bc3c190b68");
  private static final IdempotencyCommand COMMAND =
      new IdempotencyCommand(
          IdempotencyKey.of("orders", "request-42"),
          RequestFingerprint.sha256("canonical-request"),
          Duration.ofHours(24),
          Duration.ofSeconds(30),
          FailurePolicy.RETAIN);
  private static final ResultCodec<String> CODEC = new Utf8Codec("string/v1");

  @Test
  void executesAndPersistsAnAcquiredResult() throws Exception {
    RecordingStore store = new RecordingStore(acquired());
    IdempotencyExecutor executor = new IdempotencyExecutor(store, 100, () -> OWNER);

    IdempotencyResult<String> result = executor.execute(COMMAND, CODEC, () -> "created");

    assertThat(result).isEqualTo(new IdempotencyResult<>("created", IdempotencyOutcome.EXECUTED));
    assertThat(store.completion.result())
        .isEqualTo(new StoredResult("string/v1", "created".getBytes(UTF_8)));
  }

  @Test
  void replaysWithoutExecutingApplicationWork() throws Exception {
    RecordingStore store =
        new RecordingStore(
            new Acquisition.Replay(new StoredResult("string/v1", "stored".getBytes(UTF_8))));
    AtomicInteger calls = new AtomicInteger();

    IdempotencyResult<String> result =
        new IdempotencyExecutor(store)
            .execute(
                COMMAND,
                CODEC,
                () -> {
                  calls.incrementAndGet();
                  return "new";
                });

    assertThat(result).isEqualTo(new IdempotencyResult<>("stored", IdempotencyOutcome.REPLAYED));
    assertThat(calls).hasValue(0);
  }

  @Test
  void recordsTheConfiguredPolicyAndRethrowsApplicationFailures() {
    RecordingStore store = new RecordingStore(acquired());
    IllegalStateException applicationFailure = new IllegalStateException("safe application error");

    assertThatThrownBy(
            () ->
                new IdempotencyExecutor(store)
                    .execute(
                        COMMAND,
                        CODEC,
                        () -> {
                          throw applicationFailure;
                        }))
        .isSameAs(applicationFailure);
    assertThat(store.failure.policy()).isEqualTo(FailurePolicy.RETAIN);
  }

  @Test
  void sanitizesCodecFailuresAndRetainsTheKey() {
    RecordingStore store = new RecordingStore(acquired());
    ResultCodec<String> unsafeCodec =
        new ResultCodec<>() {
          @Override
          public String id() {
            return "unsafe/v1";
          }

          @Override
          public byte[] encode(String value) {
            throw new IllegalArgumentException("payload: customer-secret");
          }

          @Override
          public String decode(byte[] payload) {
            return "unused";
          }
        };

    assertThatThrownBy(
            () -> new IdempotencyExecutor(store).execute(COMMAND, unsafeCodec, () -> "ok"))
        .isInstanceOf(ResultEncodingException.class)
        .hasMessageNotContaining("customer-secret")
        .hasNoCause();
    assertThat(store.failure.policy()).isEqualTo(FailurePolicy.RETAIN);
  }

  @Test
  void boundsPersistedResults() {
    RecordingStore store = new RecordingStore(acquired());

    assertThatThrownBy(
            () -> new IdempotencyExecutor(store, 2).execute(COMMAND, CODEC, () -> "large"))
        .isInstanceOf(ResultTooLargeException.class)
        .extracting("actualBytes", "maximumBytes")
        .containsExactly(5, 2);
  }

  @Test
  void mapsStoreDecisionsToSpecificFailures() {
    assertThatThrownBy(() -> execute(new Acquisition.Conflict()))
        .isInstanceOf(IdempotencyKeyConflictException.class);
    assertThatThrownBy(() -> execute(new Acquisition.PreviousFailure()))
        .isInstanceOf(PreviousAttemptFailedException.class);
    Instant retryAfter = Instant.parse("2026-07-21T08:00:30Z");
    assertThatThrownBy(() -> execute(new Acquisition.InProgress(retryAfter)))
        .isInstanceOf(IdempotencyInProgressException.class)
        .extracting("retryAfter")
        .isEqualTo(retryAfter);
  }

  @Test
  void rejectsReplayFromAnotherSchema() {
    RecordingStore store =
        new RecordingStore(new Acquisition.Replay(new StoredResult("string/v2", new byte[0])));

    assertThatThrownBy(() -> new IdempotencyExecutor(store).execute(COMMAND, CODEC, () -> "unused"))
        .isInstanceOf(ResultCodecMismatchException.class);
  }

  private static void execute(Acquisition decision) throws Exception {
    new IdempotencyExecutor(new RecordingStore(decision)).execute(COMMAND, CODEC, () -> "unused");
  }

  private static Acquisition acquired() {
    return new Acquisition.Acquired(
        new Lease(
            COMMAND.key(), COMMAND.fingerprint(), OWNER, Instant.parse("2026-07-21T08:01:00Z")));
  }

  private record Utf8Codec(String id) implements ResultCodec<String> {
    @Override
    public byte[] encode(String value) {
      return value.getBytes(UTF_8);
    }

    @Override
    public String decode(byte[] payload) {
      return new String(payload, UTF_8);
    }
  }

  private static final class RecordingStore implements IdempotencyStore {
    private final Deque<Acquisition> decisions = new ArrayDeque<>();
    private CompletionRequest completion;
    private FailureRequest failure;

    private RecordingStore(Acquisition... decisions) {
      this.decisions.addAll(java.util.List.of(decisions));
    }

    @Override
    public Acquisition acquire(AcquireRequest request) {
      return decisions.removeFirst();
    }

    @Override
    public void complete(CompletionRequest request) {
      completion = request;
    }

    @Override
    public void fail(FailureRequest request) {
      failure = request;
    }

    @Override
    public int purgeExpired(int maxRecords) {
      return 0;
    }
  }
}
