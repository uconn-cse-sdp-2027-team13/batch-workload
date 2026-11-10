import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Settings the worker reads from environment variables.
 * Reading from a Map (instead of System.getenv directly) lets tests pass in their own values.
 */
record WorkerConfig(
        String jobId,
        Path file,
        String jdbcUrl,
        String dbUser,
        String dbPassword,
        int batchSize) {

    static final int DEFAULT_BATCH_SIZE = 1000;
    static final int MIN_BATCH_SIZE = 1000;
    static final int MAX_BATCH_SIZE = 5000;

    // Relative FILE_PATH values resolve under the mounted input directory; absolute ones are used as given.
    private static final Path DATA_DIR = Path.of("/data");

    static WorkerConfig fromEnv(Map<String, String> env) {
        return new WorkerConfig(
                required(env, "JOB_ID"),
                filePath(required(env, "FILE_PATH")),
                required(env, "JDBC_URL"),
                required(env, "DB_USER"),
                required(env, "DB_PASSWORD"),
                batchSize(env.get("BATCH_SIZE")));
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new WorkerInputException(name + " is required");
        }
        return value;
    }

    private static Path filePath(String value) {
        try {
            return DATA_DIR.resolve(value);
        } catch (InvalidPathException e) {
            // A path the OS can't represent is bad input, same as a missing FILE_PATH.
            throw new WorkerInputException("FILE_PATH is not a valid path: " + value);
        }
    }

    private static int batchSize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_BATCH_SIZE;
        }
        int size;
        try {
            size = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new WorkerInputException("BATCH_SIZE must be a whole number, got: " + value);
        }
        if (size < MIN_BATCH_SIZE || size > MAX_BATCH_SIZE) {
            throw new WorkerInputException(
                    "BATCH_SIZE must be between " + MIN_BATCH_SIZE + " and " + MAX_BATCH_SIZE + ", got: " + size);
        }
        return size;
    }
}
