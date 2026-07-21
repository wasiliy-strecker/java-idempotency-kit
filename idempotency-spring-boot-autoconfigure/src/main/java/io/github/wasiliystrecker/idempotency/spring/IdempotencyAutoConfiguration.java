package io.github.wasiliystrecker.idempotency.spring;

import io.github.wasiliystrecker.idempotency.core.IdempotencyExecutor;
import io.github.wasiliystrecker.idempotency.core.IdempotencyStore;
import io.github.wasiliystrecker.idempotency.store.jdbc.PostgresIdempotencyStore;
import io.github.wasiliystrecker.idempotency.store.memory.MemoryIdempotencyStore;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/** Auto-configures a durable or local store, annotation interceptor, metrics, and cleanup. */
@AutoConfiguration
@EnableConfigurationProperties(IdempotencyKitProperties.class)
@ConditionalOnProperty(
    prefix = "idempotency-kit",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class IdempotencyAutoConfiguration {
  /** Creates the auto-configuration. */
  public IdempotencyAutoConfiguration() {}

  /**
   * Creates the local store only when it is selected explicitly.
   *
   * @return in-process store
   */
  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @ConditionalOnProperty(prefix = "idempotency-kit", name = "store", havingValue = "memory")
  MemoryIdempotencyStore memoryIdempotencyStore() {
    return new MemoryIdempotencyStore();
  }

  /**
   * Creates the durable store by default. A missing data source deliberately fails startup.
   *
   * @param dataSource application data source
   * @return PostgreSQL store
   */
  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @ConditionalOnProperty(
      prefix = "idempotency-kit",
      name = "store",
      havingValue = "jdbc",
      matchIfMissing = true)
  PostgresIdempotencyStore postgresIdempotencyStore(DataSource dataSource) {
    return new PostgresIdempotencyStore(dataSource);
  }

  /**
   * Creates the framework-independent executor.
   *
   * @param store selected or user-provided store
   * @param properties validated bounds
   * @return executor
   */
  @Bean
  @ConditionalOnMissingBean
  IdempotencyExecutor idempotencyExecutor(
      IdempotencyStore store, IdempotencyKitProperties properties) {
    properties.validate();
    return new IdempotencyExecutor(store, properties.getMaxResultBytes());
  }

  /**
   * Creates low-cardinality Micrometer instrumentation when a registry is available.
   *
   * @param registries optional application registry
   * @return metrics bridge or no-op bridge
   */
  @Bean
  @ConditionalOnMissingBean
  IdempotencyMetrics idempotencyMetrics(ObjectProvider<MeterRegistry> registries) {
    return new IdempotencyMetrics(registries.getIfUnique());
  }

  /**
   * Activates annotation interception.
   *
   * @param executor core coordinator
   * @param properties defaults
   * @param objectMapper Boot-managed result mapper
   * @param metrics metrics bridge
   * @return aspect ordered outside transaction advice
   */
  @Bean
  @ConditionalOnMissingBean
  IdempotencyAspect idempotencyAspect(
      IdempotencyExecutor executor,
      IdempotencyKitProperties properties,
      ObjectMapper objectMapper,
      IdempotencyMetrics metrics) {
    return new IdempotencyAspect(executor, properties, objectMapper, metrics);
  }

  /**
   * Validates the bundled PostgreSQL schema after database initializers such as Flyway have run.
   *
   * @param store PostgreSQL store
   * @return startup validation runner
   */
  @Bean
  @ConditionalOnBean(PostgresIdempotencyStore.class)
  @ConditionalOnProperty(
      prefix = "idempotency-kit",
      name = "validate-schema",
      havingValue = "true",
      matchIfMissing = true)
  ApplicationRunner idempotencySchemaValidator(PostgresIdempotencyStore store) {
    return arguments -> store.validateSchema();
  }

  /**
   * Starts bounded background cleanup.
   *
   * @param store selected store
   * @param properties cleanup settings
   * @param metrics metrics bridge
   * @return lifecycle-owned cleanup worker
   */
  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(
      prefix = "idempotency-kit.cleanup",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  IdempotencyCleanupService idempotencyCleanupService(
      IdempotencyStore store, IdempotencyKitProperties properties, IdempotencyMetrics metrics) {
    properties.validate();
    return new IdempotencyCleanupService(
        store,
        metrics,
        properties.getCleanup().getInterval(),
        properties.getCleanup().getBatchSize());
  }
}
