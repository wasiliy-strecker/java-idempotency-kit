package io.github.wasiliystrecker.idempotency.core;

/** Describes whether a caller performed an operation or decoded its stored result. */
public enum IdempotencyOutcome {
  /** The current caller acquired ownership and ran the operation. */
  EXECUTED,

  /** The current caller decoded a previously completed result. */
  REPLAYED
}
