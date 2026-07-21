package io.github.wasiliystrecker.idempotency.store.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wasiliystrecker.idempotency.core.AcquireRequest;
import io.github.wasiliystrecker.idempotency.core.Acquisition;
import io.github.wasiliystrecker.idempotency.core.FailurePolicy;
import io.github.wasiliystrecker.idempotency.core.IdempotencyCommand;
import io.github.wasiliystrecker.idempotency.core.IdempotencyKey;
import io.github.wasiliystrecker.idempotency.core.IdempotencyStore;
import io.github.wasiliystrecker.idempotency.core.RequestFingerprint;
import io.github.wasiliystrecker.idempotency.testkit.IdempotencyStoreContract;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
final class PostgresIdempotencyStoreIT extends IdempotencyStoreContract {
  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.3-alpine");

  private static PGSimpleDataSource dataSource;
  private PostgresIdempotencyStore store;

  @BeforeAll
  static void migrateSchema() throws SQLException, IOException {
    dataSource = new PGSimpleDataSource();
    dataSource.setServerNames(new String[] {POSTGRES.getHost()});
    dataSource.setPortNumbers(new int[] {POSTGRES.getFirstMappedPort()});
    dataSource.setDatabaseName(POSTGRES.getDatabaseName());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(readMigration());
    }
  }

  @Override
  protected IdempotencyStore newStore() {
    store = new PostgresIdempotencyStore(dataSource);
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("TRUNCATE TABLE java_idempotency_record");
    } catch (SQLException failure) {
      throw new IllegalStateException("Could not isolate PostgreSQL test", failure);
    }
    return store;
  }

  @Override
  protected void elapse(Duration duration) {
    String sql =
        """
        UPDATE java_idempotency_record
           SET lease_expires_at = lease_expires_at - (? * interval '1 millisecond'),
               record_expires_at = record_expires_at - (? * interval '1 millisecond')
        """;
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, duration.toMillis());
      statement.setLong(2, duration.toMillis());
      statement.executeUpdate();
    } catch (SQLException failure) {
      throw new IllegalStateException("Could not advance PostgreSQL test records", failure);
    }
  }

  @Test
  void validatesTheInstalledSchema() {
    store.validateSchema();
  }

  @Test
  void grantsExactlyOneOwnerUnderContention() throws Exception {
    int contenders = 12;
    CountDownLatch ready = new CountDownLatch(contenders);
    CountDownLatch start = new CountDownLatch(1);
    IdempotencyCommand command =
        new IdempotencyCommand(
            IdempotencyKey.of("payments", "concurrent-request"),
            RequestFingerprint.sha256("same-payment"),
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            FailurePolicy.RETAIN);

    List<Future<Acquisition>> futures = new ArrayList<>();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int index = 0; index < contenders; index++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return store.acquire(new AcquireRequest(command, UUID.randomUUID()));
                }));
      }
      ready.await();
      start.countDown();

      List<Acquisition> decisions = new ArrayList<>();
      for (Future<Acquisition> future : futures) {
        decisions.add(future.get());
      }
      assertThat(decisions.stream().filter(Acquisition.Acquired.class::isInstance)).hasSize(1);
      assertThat(decisions.stream().filter(Acquisition.InProgress.class::isInstance)).hasSize(11);
    }
  }

  private static String readMigration() throws IOException {
    ClassLoader loader = PostgresIdempotencyStoreIT.class.getClassLoader();
    try (InputStream stream =
        loader.getResourceAsStream(PostgresIdempotencyStore.MIGRATION_RESOURCE)) {
      if (stream == null) {
        throw new IOException("Bundled PostgreSQL migration is missing");
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
