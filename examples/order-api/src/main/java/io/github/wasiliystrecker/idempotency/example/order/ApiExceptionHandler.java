package io.github.wasiliystrecker.idempotency.example.order;

import io.github.wasiliystrecker.idempotency.core.IdempotencyInProgressException;
import io.github.wasiliystrecker.idempotency.core.IdempotencyKeyConflictException;
import io.github.wasiliystrecker.idempotency.core.IdempotencyStoreException;
import io.github.wasiliystrecker.idempotency.core.PreviousAttemptFailedException;
import io.github.wasiliystrecker.idempotency.spring.IdempotencyInvocationException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
  private static final URI CONFLICT_TYPE =
      URI.create(
          "https://github.com/wasiliy-strecker/java-idempotency-kit/blob/main/docs/guarantees-and-failure-modes.md#precise-guarantee");
  private static final URI IN_PROGRESS_TYPE =
      URI.create(
          "https://github.com/wasiliy-strecker/java-idempotency-kit/blob/main/docs/guarantees-and-failure-modes.md#lease-sizing");
  private static final URI PREVIOUS_FAILURE_TYPE =
      URI.create(
          "https://github.com/wasiliy-strecker/java-idempotency-kit/blob/main/docs/guarantees-and-failure-modes.md#retain");

  @ExceptionHandler(IdempotencyKeyConflictException.class)
  ResponseEntity<ProblemDetail> keyConflict(IdempotencyKeyConflictException failure) {
    ProblemDetail problem =
        problem(
            HttpStatus.CONFLICT,
            CONFLICT_TYPE,
            "Idempotency key conflict",
            "The supplied key is already associated with different request data.");
    problem.setProperty("keyReference", failure.keyReference());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  @ExceptionHandler(IdempotencyInProgressException.class)
  ResponseEntity<ProblemDetail> inProgress(IdempotencyInProgressException failure) {
    ProblemDetail problem =
        problem(
            HttpStatus.CONFLICT,
            IN_PROGRESS_TYPE,
            "Request already in progress",
            "Another caller currently owns the processing lease.");
    problem.setProperty("keyReference", failure.keyReference());
    problem.setProperty("retryAfter", failure.retryAfter());
    long retryAfterSeconds =
        Math.max(1, Duration.between(Instant.now(), failure.retryAfter()).toSeconds());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
        .body(problem);
  }

  @ExceptionHandler(PreviousAttemptFailedException.class)
  ResponseEntity<ProblemDetail> previousFailure(PreviousAttemptFailedException failure) {
    ProblemDetail problem =
        problem(
            HttpStatus.CONFLICT,
            PREVIOUS_FAILURE_TYPE,
            "Previous attempt retained",
            "A previous attempt failed and the configured policy blocks automatic retries.");
    problem.setProperty("keyReference", failure.keyReference());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  @ExceptionHandler(IdempotencyInvocationException.class)
  ResponseEntity<ProblemDetail> invalidInvocation(IdempotencyInvocationException failure) {
    ProblemDetail problem =
        problem(
            HttpStatus.BAD_REQUEST,
            URI.create(
                "https://github.com/wasiliy-strecker/java-idempotency-kit/blob/main/docs/architecture.md#spring-invocation-flow"),
            "Invalid idempotency input",
            failure.getMessage());
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(IdempotencyStoreException.class)
  ResponseEntity<ProblemDetail> unavailable(IdempotencyStoreException failure) {
    ProblemDetail problem =
        problem(
            HttpStatus.SERVICE_UNAVAILABLE,
            URI.create(
                "https://github.com/wasiliy-strecker/java-idempotency-kit/blob/main/docs/guarantees-and-failure-modes.md#failure-matrix"),
            "Idempotency store unavailable",
            "The request was not processed because coordination storage is unavailable.");
    problem.setProperty("keyReference", failure.keyReference());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
  }

  private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(type);
    problem.setTitle(title);
    return problem;
  }
}
