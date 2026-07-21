package io.github.wasiliystrecker.idempotency.spring;

import io.github.wasiliystrecker.idempotency.core.FailurePolicy;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a synchronous, non-void method idempotent. Key and fingerprint are Spring Expression
 * Language expressions evaluated against method arguments.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
  /**
   * Stable, low-cardinality operation namespace.
   *
   * @return namespace accepted by the core key model
   */
  String namespace();

  /**
   * Expression resolving to the client-supplied raw idempotency key.
   *
   * @return SpEL expression such as {@code #idempotencyKey}
   */
  String key();

  /**
   * Expression resolving to a deterministic representation of the requested work.
   *
   * @return SpEL expression such as {@code #request}
   */
  String fingerprint();

  /**
   * Override for the default terminal retention, expressed as an ISO-8601 duration.
   *
   * @return duration or an empty string to use configuration
   */
  String retention() default "";

  /**
   * Override for the default processing lease, expressed as an ISO-8601 duration.
   *
   * @return duration or an empty string to use configuration
   */
  String lease() default "";

  /**
   * Explicit serialized result schema version. Increment it before incompatible DTO changes.
   *
   * @return stable, nonblank result schema version
   */
  String resultVersion() default "v1";

  /**
   * Determines whether the same request may run again after an application exception.
   *
   * @return explicit failure behavior
   */
  FailurePolicy failurePolicy() default FailurePolicy.RETAIN;
}
