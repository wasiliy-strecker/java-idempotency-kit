package io.github.wasiliystrecker.idempotency.spring;

import io.github.wasiliystrecker.idempotency.core.RequestFingerprint;
import io.github.wasiliystrecker.idempotency.core.ResultCodec;
import java.lang.reflect.Type;
import java.util.Objects;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

final class JacksonMethodResultCodec implements ResultCodec<Object> {
  private final ObjectMapper objectMapper;
  private final JavaType resultType;
  private final String id;

  JacksonMethodResultCodec(ObjectMapper objectMapper, Type resultType, String resultVersion) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    Objects.requireNonNull(resultType, "resultType");
    Objects.requireNonNull(resultVersion, "resultVersion");
    if (resultVersion.isBlank()
        || resultVersion.length() > 64
        || resultVersion.chars().anyMatch(Character::isISOControl)) {
      throw new IdempotencyInvocationException(
          "Result version must contain 1-64 printable characters");
    }
    this.resultType = objectMapper.constructType(resultType);
    this.id =
        "jackson3/"
            + RequestFingerprint.sha256(resultType.getTypeName() + "|" + resultVersion).digest();
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public byte[] encode(Object value) throws Exception {
    return objectMapper.writeValueAsBytes(value);
  }

  @Override
  public Object decode(byte[] payload) throws Exception {
    return objectMapper.readValue(payload, resultType);
  }
}
