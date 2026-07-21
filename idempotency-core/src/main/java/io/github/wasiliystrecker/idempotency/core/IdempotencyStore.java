package io.github.wasiliystrecker.idempotency.core;

/** Atomic persistence boundary shared by local and distributed store implementations. */
public interface IdempotencyStore {
  /**
   * Atomically decides whether an operation may execute, replay, or must be rejected.
   *
   * @param request proposed ownership and operation policy
   * @return acquisition decision
   */
  Acquisition acquire(AcquireRequest request);

  /**
   * Persists a successful result only while the supplied owner still owns the record.
   *
   * @param request guarded completion
   * @throws LostOwnershipException when the lease is no longer current
   */
  void complete(CompletionRequest request);

  /**
   * Persists failure policy without storing exception details.
   *
   * @param request guarded failure transition
   * @throws LostOwnershipException when the lease is no longer current
   */
  void fail(FailureRequest request);

  /**
   * Removes at most the requested number of expired records.
   *
   * @param maxRecords positive cleanup bound
   * @return number of removed records
   */
  int purgeExpired(int maxRecords);
}
