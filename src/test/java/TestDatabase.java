import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * A fresh in-memory H2 database per test, in MySQL mode, with the tables the worker uses.
 * Table definitions copy platform-infra migrations/V1__initial_schema.sql, except:
 * UNSIGNED is dropped (H2 does not support it) and the job table is cut down to its key,
 * because both worker tables point at job(job_id) and need a job row to exist.
 */
final class TestDatabase implements AutoCloseable {

    static final String USER = "sa";
    static final String PASSWORD = "test";

    final String url;
    // Holding one connection open keeps the in-memory database alive for the whole test.
    private final Connection keepAlive;

    TestDatabase() throws SQLException {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL";
        keepAlive = DriverManager.getConnection(url, USER, PASSWORD);
        execute("""
                CREATE TABLE job (
                    job_id VARCHAR(36) NOT NULL PRIMARY KEY
                )""");
        execute("""
                CREATE TABLE file_progress (
                    job_id VARCHAR(36) NOT NULL,
                    step ENUM ('FETCH', 'VALIDATE', 'LOAD', 'VERIFY') NOT NULL,
                    records_read BIGINT NOT NULL DEFAULT 0,
                    records_inserted BIGINT NOT NULL DEFAULT 0,
                    records_failed BIGINT NOT NULL DEFAULT 0,
                    checkpoint_offset BIGINT NOT NULL DEFAULT 0,
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (job_id, step),
                    FOREIGN KEY (job_id) REFERENCES job(job_id)
                )""");
        execute("""
                CREATE TABLE account_payment (
                    job_id VARCHAR(36) NOT NULL,
                    line_number BIGINT NOT NULL,
                    account_number VARCHAR(32) NOT NULL,
                    email VARCHAR(255),
                    payment_due_date DATE,
                    payment_amount DECIMAL(12,2) NOT NULL,
                    loaded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY (job_id, line_number),
                    FOREIGN KEY (job_id) REFERENCES job(job_id)
                )""");
    }

    void execute(String sql) throws SQLException {
        try (Statement statement = keepAlive.createStatement()) {
            statement.execute(sql);
        }
    }

    long count(String sql) throws SQLException {
        try (Statement statement = keepAlive.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    /** Progress row for the LOAD step, or null if none was written. */
    LoadProgress progress(String jobId) throws SQLException {
        try (Statement statement = keepAlive.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT records_read, records_inserted, records_failed, checkpoint_offset"
                             + " FROM file_progress WHERE job_id = '" + jobId + "' AND step = 'LOAD'")) {
            if (!result.next()) {
                return null;
            }
            return new LoadProgress(result.getLong(1), result.getLong(2), result.getLong(3), result.getLong(4));
        }
    }

    @Override
    public void close() throws SQLException {
        keepAlive.close();
    }
}
