import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Streams CSV data rows into account_payment in batches.
 * Only one batch of rows is held in memory at a time, never the whole file.
 * Each batch's inserts and its file_progress update are committed together,
 * so after a crash the checkpoint always matches the rows that were really saved.
 */
final class CsvLoader {

    private final Connection connection;
    private final AccountPaymentStore store;
    private final String jobId;
    private final int batchSize;

    CsvLoader(Connection connection, AccountPaymentStore store, String jobId, int batchSize) {
        this.connection = connection;
        this.store = store;
        this.jobId = jobId;
        this.batchSize = batchSize;
    }

    /**
     * Loads every data row after the header. The caller has already read and checked the header line.
     * On a retry, rows up to the saved checkpoint are skipped and counting continues from the saved totals.
     */
    LoadProgress load(BufferedReader dataLines) throws IOException, SQLException {
        connection.setAutoCommit(false);
        LoadProgress progress = store.readProgress(connection, jobId);
        long lineNumber = skipCommittedLines(dataLines, progress.checkpointOffset());

        List<AccountPaymentRow> batch = new ArrayList<>(batchSize);
        int linesInBatch = 0;
        String line;
        while ((line = dataLines.readLine()) != null) {
            lineNumber++;
            linesInBatch++;
            AccountPaymentRow row = CsvRowParser.parse(lineNumber, line);
            if (row != null) {
                batch.add(row);
            }
            if (linesInBatch == batchSize) {
                progress = commitBatch(progress, batch, linesInBatch, lineNumber);
                batch.clear();
                linesInBatch = 0;
            }
        }
        if (linesInBatch > 0) {
            progress = commitBatch(progress, batch, linesInBatch, lineNumber);
        }
        return progress;
    }

    /** Reads past rows an earlier attempt already committed. Returns the last skipped line number. */
    private long skipCommittedLines(BufferedReader dataLines, long checkpoint) throws IOException {
        long skipped = 0;
        while (skipped < checkpoint && dataLines.readLine() != null) {
            skipped++;
        }
        return skipped;
    }

    private LoadProgress commitBatch(LoadProgress progress, List<AccountPaymentRow> rows,
                                     int linesInBatch, long lastLineNumber) throws SQLException {
        int failed = linesInBatch - rows.size(); // lines that did not parse into a row
        LoadProgress updated = progress.plusBatch(linesInBatch, rows.size(), failed, lastLineNumber);
        try {
            store.insertRows(connection, jobId, rows);
            store.saveProgress(connection, jobId, updated);
            connection.commit();
        } catch (SQLException e) {
            rollbackAfter(e);
            throw e;
        }
        return updated;
    }

    // Undo the half-finished batch so its rows and its progress update are both discarded.
    private void rollbackAfter(SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            // The database is probably unreachable; keep the first error as the main one.
            original.addSuppressed(rollbackError);
        }
    }
}
