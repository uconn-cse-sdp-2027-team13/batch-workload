import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

/**
 * Worker entry point: loads the job's CSV file into MySQL, then exits with a code
 * the status tracker can read. Exit code meanings were set by the ticket owner.
 */
public class Main {

    public static void main(String[] args) {
        System.exit(run(System.getenv()));
    }

    /** Does the whole job and returns the exit code; separate from main so tests can call it. */
    static int run(Map<String, String> env) {
        try {
            WorkerConfig config = WorkerConfig.fromEnv(env);
            LoadProgress progress = load(config);
            System.out.println("Job " + config.jobId() + ": read " + progress.recordsRead()
                    + ", inserted " + progress.recordsInserted()
                    + ", failed " + progress.recordsFailed());
            return ExitCode.SUCCESS.code();
        } catch (WorkerInputException e) {
            System.err.println("Input error: " + e.getMessage());
            return ExitCode.PERMANENT_FAILURE.code();
        } catch (IOException e) {
            System.err.println("Input error: could not read input: " + e);
            return e instanceof RetryableStorageException
                    ? ExitCode.RETRYABLE_FAILURE.code() : ExitCode.PERMANENT_FAILURE.code();
        } catch (SQLException e) {
            System.err.println("Database error: " + e);
            return ExitCode.RETRYABLE_FAILURE.code();
        }
    }

    private static LoadProgress load(WorkerConfig config) throws IOException, SQLException {
        // Open the file and check the header before connecting, so bad input never touches the database.
        try (InputStream input = config.usesS3()
                ? S3Storage.open(config.s3(), config.objectKey())
                : Files.newInputStream(config.file());
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            CsvRowParser.requireValidHeader(reader.readLine());
            try (Connection connection = DriverManager.getConnection(
                    config.jdbcUrl(), config.dbUser(), config.dbPassword())) {
                CsvLoader loader = new CsvLoader(
                        connection, new AccountPaymentStore(), config.jobId(), config.batchSize());
                return loader.load(reader);
            }
        }
    }
}
