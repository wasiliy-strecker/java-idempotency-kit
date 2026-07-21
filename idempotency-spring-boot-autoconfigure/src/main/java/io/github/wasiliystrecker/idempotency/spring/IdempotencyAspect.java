package io.github.wasiliystrecker.idempotency.spring;

import io.github.wasiliystrecker.idempotency.core.FailurePolicy;
import io.github.wasiliystrecker.idempotency.core.IdempotencyCommand;
import io.github.wasiliystrecker.idempotency.core.IdempotencyExecutor;
import io.github.wasiliystrecker.idempotency.core.IdempotencyInProgressException;
import io.github.wasiliystrecker.idempotency.core.IdempotencyKey;
import io.github.wasiliystrecker.idempotency.core.IdempotencyKeyConflictException;
import io.github.wasiliystrecker.idempotency.core.IdempotencyOutcome;
import io.github.wasiliystrecker.idempotency.core.IdempotencyResult;
import io.github.wasiliystrecker.idempotency.core.PreviousAttemptFailedException;
import io.github.wasiliystrecker.idempotency.core.RequestFingerprint;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
final class IdempotencyAspect {
  private final IdempotencyExecutor executor;
  private final IdempotencyKitProperties properties;
  private final ObjectMapper resultMapper;
  private final ObjectMapper fingerprintMapper;
  private final IdempotencyMetrics metrics;
  private final ExpressionParser expressionParser = new SpelExpressionParser();
  private final Map<String, Expression> expressions = new ConcurrentHashMap<>();
  private final DefaultParameterNameDiscoverer parameterNames =
      new DefaultParameterNameDiscoverer();

  IdempotencyAspect(
      IdempotencyExecutor executor,
      IdempotencyKitProperties properties,
      ObjectMapper objectMapper,
      IdempotencyMetrics metrics) {
    this.executor = executor;
    this.properties = properties;
    this.resultMapper = objectMapper;
    this.fingerprintMapper =
        objectMapper
            .rebuild()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    this.metrics = metrics;
  }

  @Around("@annotation(annotation)")
  Object invoke(ProceedingJoinPoint joinPoint, Idempotent annotation) throws Throwable {
    long started = System.nanoTime();
    String outcome = "error";
    try {
      Method method = mostSpecificMethod(joinPoint);
      rejectUnsupportedReturnType(method);
      EvaluationContext context =
          new MethodBasedEvaluationContext(
              joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNames);
      String rawKey = requiredString(evaluate(annotation.key(), context), "key");
      Object fingerprintValue = evaluate(annotation.fingerprint(), context);
      RequestFingerprint fingerprint = fingerprint(fingerprintValue);
      IdempotencyCommand command =
          new IdempotencyCommand(
              IdempotencyKey.of(annotation.namespace(), rawKey),
              fingerprint,
              duration(annotation.retention(), properties.getRetention(), "retention"),
              duration(annotation.lease(), properties.getLease(), "lease"),
              failurePolicy(annotation));
      IdempotencyResult<Object> result =
          executor.execute(
              command,
              new JacksonMethodResultCodec(
                  resultMapper, method.getGenericReturnType(), annotation.resultVersion()),
              () -> proceed(joinPoint));
      outcome = result.outcome() == IdempotencyOutcome.EXECUTED ? "executed" : "replayed";
      return result.value();
    } catch (IdempotencyInProgressException failure) {
      outcome = "in_progress";
      throw failure;
    } catch (IdempotencyKeyConflictException failure) {
      outcome = "conflict";
      throw failure;
    } catch (PreviousAttemptFailedException failure) {
      outcome = "previous_failure";
      throw failure;
    } finally {
      metrics.recordOperation(annotation.namespace(), outcome, System.nanoTime() - started);
    }
  }

  private Method mostSpecificMethod(ProceedingJoinPoint joinPoint) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Object target = joinPoint.getTarget();
    return target == null ? method : AopUtils.getMostSpecificMethod(method, target.getClass());
  }

  private static Object proceed(ProceedingJoinPoint joinPoint) throws Exception {
    try {
      return joinPoint.proceed();
    } catch (Exception failure) {
      throw failure;
    } catch (Error failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new IllegalStateException("Annotated invocation failed", failure);
    }
  }

  private Object evaluate(String expression, EvaluationContext context) {
    try {
      return expressions
          .computeIfAbsent(expression, expressionParser::parseExpression)
          .getValue(context);
    } catch (RuntimeException failure) {
      throw new IdempotencyInvocationException("Could not evaluate an idempotency expression");
    }
  }

  private RequestFingerprint fingerprint(Object value) {
    if (value == null) {
      throw new IdempotencyInvocationException("Fingerprint expression returned null");
    }
    try {
      return value instanceof byte[] bytes
          ? RequestFingerprint.sha256(bytes)
          : RequestFingerprint.sha256(fingerprintMapper.writeValueAsBytes(value));
    } catch (RuntimeException failure) {
      throw new IdempotencyInvocationException("Could not create a request fingerprint");
    }
  }

  private static String requiredString(Object value, String name) {
    if (value == null || value.toString().isBlank()) {
      throw new IdempotencyInvocationException(name + " expression returned no value");
    }
    return value.toString();
  }

  private static Duration duration(String override, Duration fallback, String name) {
    if (override.isBlank()) {
      return fallback;
    }
    try {
      return Duration.parse(override);
    } catch (RuntimeException failure) {
      throw new IdempotencyInvocationException(name + " must be an ISO-8601 duration");
    }
  }

  private static FailurePolicy failurePolicy(Idempotent annotation) {
    return annotation.failurePolicy();
  }

  private static void rejectUnsupportedReturnType(Method method) {
    if (method.getReturnType() == Void.TYPE) {
      throw new IdempotencyInvocationException("Idempotent methods must return a value");
    }
    if (java.util.concurrent.CompletionStage.class.isAssignableFrom(method.getReturnType())) {
      throw new IdempotencyInvocationException("Asynchronous idempotent methods are not supported");
    }
  }
}
