import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end tests: real CSV file on disk, in-memory database, check the exit code and the tables. */
class MainTest {

    private static final String JOB_ID = "job-1";

    @TempDir
    Path tempDir;

    private TestDatabase db;

    @BeforeEach
    void setUp() throws Exception {
        db = new TestDatabase();
        db.execute("INSERT INTO job (job_id) VALUES ('" + JOB_ID + "')");
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    // Spec test case 1
    @Test
    void loadsAllRowsAndRecordsProgress() throws Exception {
        Path csv = writeCsv(CsvRowParser.EXPECTED_HEADER, validRows(1, 2500));

        int exitCode = Main.run(env(csv, db.url));

        assertEquals(ExitCode.SUCCESS.code(), exitCode);
        assertEquals(2500, db.count("SELECT COUNT(*) FROM account_payment"));
        assertEquals(new LoadProgress(2500, 2500, 0, 2500), db.progress(JOB_ID));
    }

    // Spec test case 2
    @Test
    void wrongHeaderLoadsNothingAndExits20() throws Exception {
        Path csv = writeCsv("email,account_number,payment_due_date,payment_amount", validRows(1, 10));

        int exitCode = Main.run(env(csv, db.url));

        assertEquals(ExitCode.PERMANENT_FAILURE.code(), exitCode);
        assertEquals(0, db.count("SELECT COUNT(*) FROM account_payment"));
        assertNull(db.progress(JOB_ID));
    }

    // Spec test case 3
    @Test
    void retryResumesFromCheckpointWithoutDuplicates() throws Exception {
        Path csv = writeCsv(CsvRowParser.EXPECTED_HEADER, validRows(1, 2500));
        // An earlier attempt committed the first batch (rows 1 to 1,000) and then died.
        Path firstThousand = writeCsv(CsvRowParser.EXPECTED_HEADER, validRows(1, 1000));
        assertEquals(ExitCode.SUCCESS.code(), Main.run(env(firstThousand, db.url)));
        assertEquals(new LoadProgress(1000, 1000, 0, 1000), db.progress(JOB_ID));

        int exitCode = Main.run(env(csv, db.url));

        assertEquals(ExitCode.SUCCESS.code(), exitCode);
        assertEquals(2500, db.count("SELECT COUNT(*) FROM account_payment"));
        assertEquals(new LoadProgress(2500, 2500, 0, 2500), db.progress(JOB_ID));
    }

    @Test
    void badRowsAreSkippedAndCounted() throws Exception {
        List<String> rows = validRows(1, 5);
        rows.set(1, "ACC-2,,2026-12-01,10.00");          // empty email
        rows.set(3, "ACC-4,a@example.com,not-a-date,5.00"); // unparseable date
        Path csv = writeCsv(CsvRowParser.EXPECTED_HEADER, rows);

        int exitCode = Main.run(env(csv, db.url));

        assertEquals(ExitCode.SUCCESS.code(), exitCode);
        assertEquals(3, db.count("SELECT COUNT(*) FROM account_payment"));
        // line_number keeps the row's position in the file, even when rows before it were bad.
        assertEquals(1, db.count("SELECT COUNT(*) FROM account_payment WHERE line_number = 5"));
        assertEquals(new LoadProgress(5, 3, 2, 5), db.progress(JOB_ID));
    }

    @Test
    void failedBatchRollsBackRowsAndProgressTogether() throws Exception {
        // Plant a row at line 2,100 so batch 3 (lines 2,001 to 2,500) hits a duplicate key and fails.
        db.execute("INSERT INTO account_payment (job_id, line_number, account_number, payment_amount)"
                + " VALUES ('" + JOB_ID + "', 2100, 'PLANTED', 1.00)");
        Path csv = writeCsv(CsvRowParser.EXPECTED_HEADER, validRows(1, 2500));

        int exitCode = Main.run(env(csv, db.url));

        assertEquals(ExitCode.RETRYABLE_FAILURE.code(), exitCode);
        // Batches 1 and 2 stayed committed; none of batch 3's rows or its progress update survived.
        assertEquals(2000 + 1, db.count("SELECT COUNT(*) FROM account_payment"));
        assertEquals(new LoadProgress(2000, 2000, 0, 2000), db.progress(JOB_ID));
    }

    @Test
    void missingFileExits20() {
        int exitCode = Main.run(env(tempDir.resolve("does-not-exist.csv"), db.url));

        assertEquals(ExitCode.PERMANENT_FAILURE.code(), exitCode);
    }

    @Test
    void missingEnvVarExits20() throws Exception {
        Map<String, String> env = env(writeCsv(CsvRowParser.EXPECTED_HEADER, validRows(1, 1)), db.url);
        env.remove("JDBC_URL");

        assertEquals(ExitCode.PERMANENT_FAILURE.code(), Main.run(env));
    }

    @Test
    void unreachableDatabaseExits10() throws Exception {
        Path csv = writeCsv(CsvRowParser.EXPECTED_HEADER, validRows(1, 1));
        // Nothing listens on port 1, so the connection is refused.
        String unreachable = "jdbc:h2:tcp://localhost:1/nothing-here";

        assertEquals(ExitCode.RETRYABLE_FAILURE.code(), Main.run(env(csv, unreachable)));
    }

    private Map<String, String> env(Path csv, String jdbcUrl) {
        Map<String, String> env = new HashMap<>();
        env.put("JOB_ID", JOB_ID);
        env.put("FILE_PATH", csv.toString());
        env.put("JDBC_URL", jdbcUrl);
        env.put("DB_USER", TestDatabase.USER);
        env.put("DB_PASSWORD", TestDatabase.PASSWORD);
        env.put("BATCH_SIZE", "1000");
        return env;
    }

    private Path writeCsv(String header, List<String> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        lines.addAll(rows);
        Path file = Files.createTempFile(tempDir, "input", ".csv");
        Files.write(file, lines);
        return file;
    }

    private static List<String> validRows(int first, int last) {
        List<String> rows = new ArrayList<>();
        for (int i = first; i <= last; i++) {
            rows.add("ACC-" + i + ",user" + i + "@example.com,2026-12-01,12.34");
        }
        return rows;
    }
}
