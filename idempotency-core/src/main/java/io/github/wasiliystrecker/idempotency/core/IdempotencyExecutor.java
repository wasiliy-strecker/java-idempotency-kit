package io.github.wasiliystrecker.idempotency.core;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Coordinates atomic acquisition, execution, result persistence, and replay. */
public final class IdempotencyExecutor {
  /** Default maximum serialized result size: one mebibyte. */
  public static final int DEFAULT_MAX_RESULT_BYTES = 1_048_576;

  private final IdempotencyStore store;
  private final int maxResultBytes;
  private final Supplier<UUID> ownerTokens;

  /**
   * Creates an executor with a one-mebibyte result bound.
   *
   * @param store atomic persistence boundary
   */
  public IdempotencyExecutor(IdempotencyStore store) {
    this(store, DEFAULT_MAX_RESULT_BYTES);
  }

  /**
   * Creates an executor with an explicit result bound.
   *
   * @param store atomic persistence boundary
   * @param maxResultBytes positive serialized result limit
   */
  public IdempotencyExecutor(IdempotencyStore store, int maxResultBytes) {
    this(store, maxResultBytes, UUID::randomUUID);
  }

  IdempotencyExecutor(IdempotencyStore store, int maxResultBytes, Supplier<UUID> ownerTokens) {
    this.store = Objects.requireNonNull(store, "store");
    if (maxResultBytes < 1) {
      throw new IllegalArgumentException("maxResultBytes must be positive");
    }
    this.maxResultBytes = maxResultBytes;
    this.ownerTokens = Objects.requireNonNull(ownerTokens, "ownerTokens");
  }

  /**
   * Executes once for an acquired identity or decodes its completed result.
   *
   * @param command identity and failure policy
   * @param codec deterministic result codec
   * @param operation application work run only by an acquired owner
   * @param <T> result type
   * @return executed or replayed result
   * @throws Exception original application exception
   * @throws IdempotencyException safe coordination or result failure
   */
  public <T> IdempotencyResult<T> execute(
      IdempotencyCommand command, ResultCodec<T> codec, CheckedSupplier<T> operation)
      throws Exception {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(codec, "codec");
    Objects.requireNonNull(operation, "operation");
    String codecId = validateCodecId(codec.id());
    Acquisition acquisition =
        store.acquire(new AcquireRequest(command, Objects.requireNonNull(ownerTokens.get())));

    return switch (acquisition) {
      case Acquisition.Acquired acquired ->
          executeOwned(command, codec, codecId, operation, acquired.lease());
      case Acquisition.Replay replay -> replay(command.key(), codec, codecId, replay.result());
      case Acquisition.InProgress inProgress ->
          throw new IdempotencyInProgressException(command.key(), inProgress.retryAfter());
      case Acquisition.Conflict ignored -> throw new IdempotencyKeyConflictException(command.key());
      case Acquisition.PreviousFailure ignored ->
          throw new PreviousAttemptFailedException(command.key());
    };
  }

  private <T> IdempotencyResult<T> executeOwned(
      IdempotencyCommand command,
      ResultCodec<T> codec,
      String codecId,
      CheckedSupplier<T> operation,
      Lease lease)
      throws Exception {
    T value;
    try {
      value = operation.get();
    } catch (Exception applicationFailure) {
      recordFailure(
          new FailureRequest(lease, command.failurePolicy(), command.retention()),
          applicationFailure);
      throw applicationFailure;
    }

    byte[] encoded;
    try {
      encoded = Objects.requireNonNull(codec.encode(value), "codec payload");
    } catch (Exception encodingFailure) {
      ResultEncodingException safeFailure = new ResultEncodingException(command.key());
      recordFailure(
          new FailureRequest(lease, FailurePolicy.RETAIN, command.retention()), safeFailure);
      throw safeFailure;
    }
    if (encoded.length > maxResultBytes) {
      ResultTooLargeException tooLarge =
          new ResultTooLargeException(command.key(), encoded.length, maxResultBytes);
      recordFailure(new FailureRequest(lease, FailurePolicy.RETAIN, command.retention()), tooLarge);
      throw tooLarge;
    }

    store.complete(
        new CompletionRequest(lease, new StoredResult(codecId, encoded), command.retention()));
    return new IdempotencyResult<>(value, IdempotencyOutcome.EXECUTED);
  }

  private static <T> IdempotencyResult<T> replay(
      IdempotencyKey key, ResultCodec<T> codec, String codecId, StoredResult stored) {
    if (!codecId.equals(stored.codecId())) {
      throw new ResultCodecMismatchException(key);
    }
    try {
      return new IdempotencyResult<>(codec.decode(stored.payload()), IdempotencyOutcome.REPLAYED);
    } catch (Exception decodingFailure) {
      throw new ResultDecodingException(key);
    }
  }

  private void recordFailure(FailureRequest request, RuntimeException primaryFailure) {
    try {
      store.fail(request);
    } catch (RuntimeException storeFailure) {
      primaryFailure.addSuppressed(storeFailure);
    }
  }

  private void recordFailure(FailureRequest request, Exception primaryFailure) {
    try {
      store.fail(request);
    } catch (RuntimeException storeFailure) {
      primaryFailure.addSuppressed(storeFailure);
    }
  }

  private static String validateCodecId(String codecId) {
    Objects.requireNonNull(codecId, "codec id");
    if (codecId.isBlank()
        || codecId.length() > 128
        || codecId.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("codec id must contain 1-128 printable characters");
    }
    return codecId;
  }
}
