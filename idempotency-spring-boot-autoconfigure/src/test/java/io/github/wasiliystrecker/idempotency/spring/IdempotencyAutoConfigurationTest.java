package io.github.wasiliystrecker.idempotency.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wasiliystrecker.idempotency.core.IdempotencyKeyConflictException;
import io.github.wasiliystrecker.idempotency.core.IdempotencyStore;
import io.github.wasiliystrecker.idempotency.store.memory.MemoryIdempotencyStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

final class IdempotencyAutoConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AopAutoConfiguration.class, IdempotencyAutoConfiguration.class))
          .withPropertyValues(
              "idempotency-kit.store=memory", "idempotency-kit.cleanup.enabled=false")
          .withUserConfiguration(SampleConfiguration.class);

  @Test
  void executesOnceAndReplaysTheTypedResult() {
    contextRunner.run(
        context -> {
          SampleOrderService service = context.getBean(SampleOrderService.class);
          CreateOrder request = new CreateOrder("sku-42", new BigDecimal("19.95"));

          OrderReceipt first = service.create("client-request-1", request);
          OrderReceipt replay = service.create("client-request-1", request);

          assertThat(first).isEqualTo(new OrderReceipt("order-1", new BigDecimal("19.95")));
          assertThat(replay).isEqualTo(first);
          assertThat(service.executions()).isEqualTo(1);
          SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
          assertThat(
                  registry
                      .get("idempotency.operations")
                      .tag("namespace", "orders.create")
                      .tag("outcome", "executed")
                      .counter()
                      .count())
              .isEqualTo(1.0);
          assertThat(
                  registry
                      .get("idempotency.operations")
                      .tag("namespace", "orders.create")
                      .tag("outcome", "replayed")
                      .counter()
                      .count())
              .isEqualTo(1.0);
        });
  }

  @Test
  void rejectsKeyReuseForDifferentWork() {
    contextRunner.run(
        context -> {
          SampleOrderService service = context.getBean(SampleOrderService.class);
          service.create("client-request-2", new CreateOrder("sku-42", BigDecimal.ONE));

          assertThatThrownBy(
                  () ->
                      service.create("client-request-2", new CreateOrder("sku-99", BigDecimal.TEN)))
              .isInstanceOf(IdempotencyKeyConflictException.class);
          assertThat(service.executions()).isEqualTo(1);
        });
  }

  @Test
  void backsOffForAnApplicationProvidedStore() {
    MemoryIdempotencyStore customStore = new MemoryIdempotencyStore();
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class))
        .withPropertyValues("idempotency-kit.cleanup.enabled=false")
        .withBean(IdempotencyStore.class, () -> customStore)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(IdempotencyStore.class)).isSameAs(customStore);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class SampleConfiguration {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    SimpleMeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    SampleOrderService sampleOrderService() {
      return new SampleOrderService();
    }
  }

  static class SampleOrderService {
    private final AtomicInteger executions = new AtomicInteger();

    @Idempotent(namespace = "orders.create", key = "#idempotencyKey", fingerprint = "#request")
    public OrderReceipt create(String idempotencyKey, CreateOrder request) {
      return new OrderReceipt("order-" + executions.incrementAndGet(), request.totalAmount());
    }

    int executions() {
      return executions.get();
    }
  }

  record CreateOrder(String sku, BigDecimal totalAmount) {}

  record OrderReceipt(String orderId, BigDecimal totalAmount) {}
}
