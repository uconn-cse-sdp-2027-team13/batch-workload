import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvLoaderTest {

    /** A real store that also remembers the checkpoint saved by each batch. */
    private static class RecordingStore extends AccountPaymentStore {
        final List<Long> savedCheckpoints = new ArrayList<>();

        @Override
        void saveProgress(Connection connection, String jobId, LoadProgress progress) throws SQLException {
            savedCheckpoints.add(progress.checkpointOffset());
            super.saveProgress(connection, jobId, progress);
        }
    }

    // Spec test case 1: 2,500 lines with batch size 1,000 is 3 batches.
    @Test
    void commitsOneBatchPerBatchSizeLines() throws Exception {
        StringBuilder csvData = new StringBuilder(); // data rows only; the header was already read
        for (int i = 1; i <= 2500; i++) {
            csvData.append("ACC-").append(i).append(",u").append(i).append("@example.com,2026-12-01,1.00\n");
        }
        RecordingStore store = new RecordingStore();

        try (TestDatabase db = new TestDatabase();
             Connection connection = DriverManager.getConnection(db.url, TestDatabase.USER, TestDatabase.PASSWORD)) {
            db.execute("INSERT INTO job (job_id) VALUES ('job-1')");
            new CsvLoader(connection, store, "job-1", 1000)
                    .load(new BufferedReader(new StringReader(csvData.toString())));
        }

        assertEquals(List.of(1000L, 2000L, 2500L), store.savedCheckpoints);
    }
}
