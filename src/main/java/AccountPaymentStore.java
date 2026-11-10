import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * All SQL the worker runs. Tables are defined in platform-infra
 * (migrations/V1__initial_schema.sql). None of these methods commit;
 * CsvLoader decides when a batch is committed.
 */
class AccountPaymentStore { // not final, so a test can watch each saveProgress call

    // This worker only ever writes the LOAD step of file_progress.
    private static final String STEP = "LOAD";

    private static final String SELECT_PROGRESS = """
            SELECT records_read, records_inserted, records_failed, checkpoint_offset
            FROM file_progress
            WHERE job_id = ? AND step = ?""";

    private static final String INSERT_ROW = """
            INSERT INTO account_payment
                (job_id, line_number, account_number, email, payment_due_date, payment_amount)
            VALUES (?, ?, ?, ?, ?, ?)""";

    private static final String UPDATE_PROGRESS = """
            UPDATE file_progress
            SET records_read = ?, records_inserted = ?, records_failed = ?,
                checkpoint_offset = ?, updated_at = CURRENT_TIMESTAMP(3)
            WHERE job_id = ? AND step = ?""";

    private static final String INSERT_PROGRESS = """
            INSERT INTO file_progress
                (records_read, records_inserted, records_failed, checkpoint_offset, job_id, step)
            VALUES (?, ?, ?, ?, ?, ?)""";

    /** Saved progress from an earlier attempt, or NONE if this is the first attempt. */
    LoadProgress readProgress(Connection connection, String jobId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_PROGRESS)) {
            statement.setString(1, jobId);
            statement.setString(2, STEP);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return LoadProgress.NONE;
                }
                return new LoadProgress(
                        result.getLong("records_read"),
                        result.getLong("records_inserted"),
                        result.getLong("records_failed"),
                        result.getLong("checkpoint_offset"));
            }
        }
    }

    /** Sends all rows to the database in one JDBC batch instead of one round trip per row. */
    void insertRows(Connection connection, String jobId, List<AccountPaymentRow> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ROW)) {
            for (AccountPaymentRow row : rows) {
                statement.setString(1, jobId);
                statement.setLong(2, row.lineNumber());
                statement.setString(3, row.accountNumber());
                statement.setString(4, row.email());
                statement.setDate(5, Date.valueOf(row.paymentDueDate()));
                statement.setBigDecimal(6, row.paymentAmount());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** Updates this job's LOAD progress row, creating it on the first batch. */
    void saveProgress(Connection connection, String jobId, LoadProgress progress) throws SQLException {
        // Both statements take the same parameters in the same order.
        if (runProgressStatement(connection, UPDATE_PROGRESS, jobId, progress) == 0) {
            runProgressStatement(connection, INSERT_PROGRESS, jobId, progress);
        }
    }

    private int runProgressStatement(Connection connection, String sql, String jobId, LoadProgress progress)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, progress.recordsRead());
            statement.setLong(2, progress.recordsInserted());
            statement.setLong(3, progress.recordsFailed());
            statement.setLong(4, progress.checkpointOffset());
            statement.setString(5, jobId);
            statement.setString(6, STEP);
            return statement.executeUpdate();
        }
    }
}
