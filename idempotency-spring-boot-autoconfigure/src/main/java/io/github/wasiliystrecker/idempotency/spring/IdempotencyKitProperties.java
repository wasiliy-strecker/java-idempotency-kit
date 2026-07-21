package io.github.wasiliystrecker.idempotency.spring;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** External configuration for the starter's store, state machine, and cleanup worker. */
@ConfigurationProperties("idempotency-kit")
public final class IdempotencyKitProperties {
  private boolean enabled = true;
  private Store store = Store.JDBC;
  private Duration retention = Duration.ofHours(24);
  private Duration lease = Duration.ofSeconds(30);
  private int maxResultBytes = 1_048_576;
  private boolean validateSchema = true;
  private final Cleanup cleanup = new Cleanup();

  /** Creates properties with production-oriented defaults. */
  public IdempotencyKitProperties() {}

  /**
   * Reports whether auto-configuration is active.
   *
   * @return whether the starter is active
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Enables or disables auto-configuration.
   *
   * @param enabled whether the starter is active
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns the selected built-in store.
   *
   * @return configured persistence implementation
   */
  public Store getStore() {
    return store;
  }

  /**
   * Selects a built-in store.
   *
   * @param store persistence implementation
   */
  public void setStore(Store store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  /**
   * Returns the terminal record lifetime.
   *
   * @return default terminal retention
   */
  public Duration getRetention() {
    return retention;
  }

  /**
   * Sets the terminal record lifetime.
   *
   * @param retention default terminal retention
   */
  public void setRetention(Duration retention) {
    this.retention = Objects.requireNonNull(retention, "retention");
  }

  /**
   * Returns the exclusive processing lifetime.
   *
   * @return default exclusive processing lease
   */
  public Duration getLease() {
    return lease;
  }

  /**
   * Sets the exclusive processing lifetime.
   *
   * @param lease default exclusive processing lease
   */
  public void setLease(Duration lease) {
    this.lease = Objects.requireNonNull(lease, "lease");
  }

  /**
   * Returns the serialized result bound.
   *
   * @return maximum serialized result size
   */
  public int getMaxResultBytes() {
    return maxResultBytes;
  }

  /**
   * Sets the serialized result bound.
   *
   * @param maxResultBytes maximum serialized result size
   */
  public void setMaxResultBytes(int maxResultBytes) {
    this.maxResultBytes = maxResultBytes;
  }

  /**
   * Reports whether startup checks the PostgreSQL schema.
   *
   * @return whether PostgreSQL schema validation runs during startup
   */
  public boolean isValidateSchema() {
    return validateSchema;
  }

  /**
   * Enables or disables the PostgreSQL startup check.
   *
   * @param validateSchema whether PostgreSQL schema validation runs during startup
   */
  public void setValidateSchema(boolean validateSchema) {
    this.validateSchema = validateSchema;
  }

  /**
   * Returns mutable cleanup binding properties.
   *
   * @return bounded cleanup worker settings
   */
  public Cleanup getCleanup() {
    return cleanup;
  }

  void validate() {
    if (maxResultBytes < 1) {
      throw new IllegalArgumentException("idempotency-kit.max-result-bytes must be positive");
    }
    // The core command performs the complete duration validation in one place.
    new io.github.wasiliystrecker.idempotency.core.IdempotencyCommand(
        io.github.wasiliystrecker.idempotency.core.IdempotencyKey.of("configuration", "probe"),
        io.github.wasiliystrecker.idempotency.core.RequestFingerprint.sha256("probe"),
        retention,
        lease,
        io.github.wasiliystrecker.idempotency.core.FailurePolicy.RETAIN);
    cleanup.validate();
  }

  /** Available built-in persistence implementations. */
  public enum Store {
    /** PostgreSQL coordination for durable multi-instance applications. */
    JDBC,

    /** In-process coordination for tests and single-instance applications. */
    MEMORY
  }

  /** Bounded expired-record cleanup settings. */
  public static final class Cleanup {
    private boolean enabled = true;
    private Duration interval = Duration.ofMinutes(10);
    private int batchSize = 500;

    /** Creates cleanup settings with bounded production defaults. */
    public Cleanup() {}

    /**
     * Reports whether periodic cleanup is active.
     *
     * @return whether periodic cleanup runs
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Enables or disables periodic cleanup.
     *
     * @param enabled whether periodic cleanup runs
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Returns the delay between completed cleanup runs.
     *
     * @return delay between cleanup runs
     */
    public Duration getInterval() {
      return interval;
    }

    /**
     * Sets the delay between completed cleanup runs.
     *
     * @param interval delay between cleanup runs
     */
    public void setInterval(Duration interval) {
      this.interval = Objects.requireNonNull(interval, "interval");
    }

    /**
     * Returns the maximum cleanup batch size.
     *
     * @return maximum records deleted in one run
     */
    public int getBatchSize() {
      return batchSize;
    }

    /**
     * Sets the maximum cleanup batch size.
     *
     * @param batchSize maximum records deleted in one run
     */
    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    private void validate() {
      if (interval.isNegative() || interval.isZero()) {
        throw new IllegalArgumentException("idempotency-kit.cleanup.interval must be positive");
      }
      if (batchSize < 1) {
        throw new IllegalArgumentException("idempotency-kit.cleanup.batch-size must be positive");
      }
    }
  }
}
