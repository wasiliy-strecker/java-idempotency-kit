package io.github.wasiliystrecker.idempotency.core;

/** Determines whether a failed operation may be acquired again before retention expires. */
public enum FailurePolicy {
  /** A later attempt with the same fingerprint may acquire a new lease immediately. */
  RETRY,

  /** Later attempts remain blocked until the failed record expires or is removed explicitly. */
  RETAIN
}
