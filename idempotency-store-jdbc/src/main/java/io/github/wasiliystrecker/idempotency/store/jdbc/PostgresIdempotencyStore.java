package io.github.wasiliystrecker.idempotency.store.jdbc;

import io.github.wasiliystrecker.idempotency.core.AcquireRequest;
import io.github.wasiliystrecker.idempotency.core.Acquisition;
import io.github.wasiliystrecker.idempotency.core.CompletionRequest;
import io.github.wasiliystrecker.idempotency.core.FailurePolicy;
import io.github.wasiliystrecker.idempotency.core.FailureRequest;
import io.github.wasiliystrecker.idempotency.core.IdempotencyKey;
import io.github.wasiliystrecker.idempotency.core.IdempotencyMaintenanceException;
import io.github.wasiliystrecker.idempotency.core.IdempotencyStore;
import io.github.wasiliystrecker.idempotency.core.IdempotencyStoreException;
import io.github.wasiliystrecker.idempotency.core.Lease;
import io.github.wasiliystrecker.idempotency.core.LostOwnershipException;
import io.github.wasiliystrecker.idempotency.core.RequestFingerprint;
import io.github.wasiliystrecker.idempotency.core.StoredResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * PostgreSQL implementation whose acquisition and terminal transitions are atomic across JVMs.
 * Database time is authoritative, and expired leases can be taken over safely.
 */
public final class PostgresIdempotencyStore implements IdempotencyStore {
  /** Flyway-compatible migration bundled with this module. */
  public static final String MIGRATION_RESOURCE =
      "db/idempotency/postgresql/V1__create_idempotency_record.sql";

  private static final String INSERT =
      """
      INSERT INTO java_idempotency_record (
          namespace, key_digest, request_fingerprint, status, owner_token,
          lease_expires_at, record_expires_at, retryable)
      VALUES (?, ?, ?, 'PROCESSING', ?,
          clock_timestamp() + (? * interval '1 millisecond'),
          clock_timestamp() + (? * interval '1 millisecond'), false)
      ON CONFLICT DO NOTHING
      RETURNING lease_expires_at
      """;

  private static final String SELECT_FOR_UPDATE =
      """
      SELECT request_fingerprint, status, owner_token, lease_expires_at,
             record_expires_at, result_codec, result_payload, retryable,
             clock_timestamp() AS database_now
        FROM java_idempotency_record
       WHERE namespace = ? AND key_digest = ?
       FOR UPDATE
      """;

  private static final String ACQUIRE_EXISTING =
      """
      UPDATE java_idempotency_record
         SET request_fingerprint = ?, status = 'PROCESSING', owner_token = ?,
             lease_expires_at = ?, record_expires_at = ?, result_codec = NULL,
             result_payload = NULL, retryable = false, updated_at = clock_timestamp()
       WHERE namespace = ? AND key_digest = ?
      """;

  private static final String COMPLETE =
      """
      UPDATE java_idempotency_record
         SET status = 'COMPLETED', owner_token = NULL, lease_expires_at = NULL,
             record_expires_at = clock_timestamp() + (? * interval '1 millisecond'),
             result_codec = ?, result_payload = ?, retryable = false,
             updated_at = clock_timestamp()
       WHERE namespace = ? AND key_digest = ? AND request_fingerprint = ?
         AND status = 'PROCESSING' AND owner_token = ?
         AND lease_expires_at > clock_timestamp()
      """;

  private static final String FAIL =
      """
      UPDATE java_idempotency_record
         SET status = 'FAILED', owner_token = NULL, lease_expires_at = NULL,
             record_expires_at = clock_timestamp() + (? * interval '1 millisecond'),
             result_codec = NULL, result_payload = NULL, retryable = ?,
             updated_at = clock_timestamp()
       WHERE namespace = ? AND key_digest = ? AND request_fingerprint = ?
         AND status = 'PROCESSING' AND owner_token = ?
         AND lease_expires_at > clock_timestamp()
      """;

  private static final String PURGE =
      """
      WITH expired AS (
          SELECT namespace, key_digest
            FROM java_idempotency_record
           WHERE record_expires_at <= clock_timestamp()
           ORDER BY record_expires_at
           FOR UPDATE SKIP LOCKED
           LIMIT ?
      )
      DELETE FROM java_idempotency_record target
       USING expired
       WHERE target.namespace = expired.namespace
         AND target.key_digest = expired.key_digest
      RETURNING 1
      """;

  private final DataSource dataSource;

  /**
   * Creates a store backed by an application-managed data source.
   *
   * @param dataSource PostgreSQL connection source
   */
  public PostgresIdempotencyStore(DataSource dataSource) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
  }

  @Override
  public Acquisition acquire(AcquireRequest request) {
    Objects.requireNonNull(request, "request");
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        Acquisition inserted = insert(connection, request);
        Acquisition decision = inserted != null ? inserted : decideExisting(connection, request);
        connection.commit();
        return decision;
      } catch (SQLException | RuntimeException failure) {
        rollback(connection, failure);
        throw failure;
      }
    } catch (SQLException failure) {
      throw new IdempotencyStoreException("acquire", request.command().key(), failure);
    }
  }

  @Override
  public void complete(CompletionRequest request) {
    Objects.requireNonNull(request, "request");
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(COMPLETE)) {
      statement.setLong(1, milliseconds(request.retention()));
      statement.setString(2, request.result().codecId());
      statement.setBytes(3, request.result().payload());
      bindLeaseIdentity(statement, 4, request.lease());
      requireOwnership(statement.executeUpdate(), request.lease().key());
    } catch (SQLException failure) {
      throw new IdempotencyStoreException("complete", request.lease().key(), failure);
    }
  }

  @Override
  public void fail(FailureRequest request) {
    Objects.requireNonNull(request, "request");
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(FAIL)) {
      statement.setLong(1, milliseconds(request.retention()));
      statement.setBoolean(2, request.policy() == FailurePolicy.RETRY);
      bindLeaseIdentity(statement, 3, request.lease());
      requireOwnership(statement.executeUpdate(), request.lease().key());
    } catch (SQLException failure) {
      throw new IdempotencyStoreException("fail", request.lease().key(), failure);
    }
  }

  @Override
  public int purgeExpired(int maxRecords) {
    if (maxRecords < 1) {
      throw new IllegalArgumentException("maxRecords must be positive");
    }
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(PURGE)) {
      statement.setInt(1, maxRecords);
      int removed = 0;
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          removed++;
        }
      }
      return removed;
    } catch (SQLException failure) {
      throw new IdempotencyMaintenanceException("cleanup", failure);
    }
  }

  /**
   * Fails fast when the required migration has not been applied.
   *
   * @throws IdempotencyMaintenanceException when the table or required columns are unavailable
   */
  public void validateSchema() {
    String sql =
        """
        SELECT namespace, key_digest, request_fingerprint, status, owner_token,
               lease_expires_at, record_expires_at, result_codec, result_payload, retryable
          FROM java_idempotency_record
         WHERE false
        """;
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rows = statement.executeQuery()) {
      if (rows.next()) {
        throw new IllegalStateException("Schema validation query unexpectedly returned a row");
      }
    } catch (SQLException failure) {
      throw new IdempotencyMaintenanceException("schema validation", failure);
    }
  }

  private static Acquisition insert(Connection connection, AcquireRequest request)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
      statement.setString(1, request.command().key().namespace());
      statement.setString(2, request.command().key().digest());
      statement.setString(3, request.command().fingerprint().digest());
      statement.setObject(4, request.ownerToken());
      statement.setLong(5, milliseconds(request.command().lease()));
      statement.setLong(6, milliseconds(request.command().retention()));
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          return null;
        }
        Instant expiresAt = rows.getTimestamp("lease_expires_at").toInstant();
        return new Acquisition.Acquired(lease(request, expiresAt));
      }
    }
  }

  private static Acquisition decideExisting(Connection connection, AcquireRequest request)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SELECT_FOR_UPDATE)) {
      statement.setString(1, request.command().key().namespace());
      statement.setString(2, request.command().key().digest());
      try (ResultSet row = statement.executeQuery()) {
        if (!row.next()) {
          return insertAfterConcurrentDeletion(connection, request);
        }
        Instant now = row.getTimestamp("database_now").toInstant();
        Instant recordExpiresAt = row.getTimestamp("record_expires_at").toInstant();
        if (!recordExpiresAt.isAfter(now)) {
          return acquireExisting(connection, request, now);
        }
        RequestFingerprint fingerprint =
            new RequestFingerprint(row.getString("request_fingerprint"));
        if (!fingerprint.equals(request.command().fingerprint())) {
          return new Acquisition.Conflict();
        }
        return switch (row.getString("status")) {
          case "COMPLETED" ->
              new Acquisition.Replay(
                  new StoredResult(row.getString("result_codec"), row.getBytes("result_payload")));
          case "PROCESSING" -> decideProcessing(connection, request, row, now);
          case "FAILED" ->
              row.getBoolean("retryable")
                  ? acquireExisting(connection, request, now)
                  : new Acquisition.PreviousFailure();
          default -> throw new SQLException("Unknown idempotency record status");
        };
      }
    }
  }

  private static Acquisition insertAfterConcurrentDeletion(
      Connection connection, AcquireRequest request) throws SQLException {
    Acquisition inserted = insert(connection, request);
    if (inserted == null) {
      throw new SQLException("Concurrent idempotency record changed repeatedly");
    }
    return inserted;
  }

  private static Acquisition decideProcessing(
      Connection connection, AcquireRequest request, ResultSet row, Instant now)
      throws SQLException {
    Instant leaseExpiresAt = row.getTimestamp("lease_expires_at").toInstant();
    return leaseExpiresAt.isAfter(now)
        ? new Acquisition.InProgress(leaseExpiresAt)
        : acquireExisting(connection, request, now);
  }

  private static Acquisition acquireExisting(
      Connection connection, AcquireRequest request, Instant now) throws SQLException {
    Instant leaseExpiresAt = now.plusMillis(milliseconds(request.command().lease()));
    Instant recordExpiresAt = now.plusMillis(milliseconds(request.command().retention()));
    try (PreparedStatement statement = connection.prepareStatement(ACQUIRE_EXISTING)) {
      statement.setString(1, request.command().fingerprint().digest());
      statement.setObject(2, request.ownerToken());
      statement.setTimestamp(3, Timestamp.from(leaseExpiresAt));
      statement.setTimestamp(4, Timestamp.from(recordExpiresAt));
      statement.setString(5, request.command().key().namespace());
      statement.setString(6, request.command().key().digest());
      if (statement.executeUpdate() != 1) {
        throw new SQLException("Idempotency record disappeared while locked");
      }
    }
    return new Acquisition.Acquired(lease(request, leaseExpiresAt));
  }

  private static Lease lease(AcquireRequest request, Instant expiresAt) {
    return new Lease(
        request.command().key(), request.command().fingerprint(), request.ownerToken(), expiresAt);
  }

  private static void bindLeaseIdentity(PreparedStatement statement, int offset, Lease lease)
      throws SQLException {
    statement.setString(offset, lease.key().namespace());
    statement.setString(offset + 1, lease.key().digest());
    statement.setString(offset + 2, lease.fingerprint().digest());
    statement.setObject(offset + 3, lease.ownerToken());
  }

  private static void requireOwnership(int updated, IdempotencyKey key) {
    if (updated != 1) {
      throw new LostOwnershipException(key);
    }
  }

  private static long milliseconds(Duration duration) {
    return Math.max(1, duration.toMillis());
  }

  private static void rollback(Connection connection, Throwable primaryFailure) {
    try {
      connection.rollback();
    } catch (SQLException rollbackFailure) {
      primaryFailure.addSuppressed(rollbackFailure);
    }
  }
}
