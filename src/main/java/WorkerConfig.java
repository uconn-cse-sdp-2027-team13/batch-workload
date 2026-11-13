import java.net.URI;
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
        String objectKey,
        String jdbcUrl,
        String dbUser,
        String dbPassword,
        int batchSize,
        S3Config s3) {

    static final int DEFAULT_BATCH_SIZE = 1000;
    static final int MIN_BATCH_SIZE = 1000;
    static final int MAX_BATCH_SIZE = 5000;

    // Relative FILE_PATH values resolve under the mounted input directory; absolute ones are used as given.
    private static final Path DATA_DIR = Path.of("/data");

    static WorkerConfig fromEnv(Map<String, String> env) {
        String objectKey = required(env, "FILE_PATH");
        S3Config s3 = S3Config.fromEnv(env);
        return new WorkerConfig(
                required(env, "JOB_ID"),
                s3 == null ? filePath(objectKey) : null,
                objectKey,
                required(env, "JDBC_URL"),
                required(env, "DB_USER"),
                required(env, "DB_PASSWORD"),
                batchSize(env.get("BATCH_SIZE")),
                s3);
    }

    boolean usesS3() {
        return s3 != null;
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

/** Complete S3 settings. Partial configuration is rejected instead of silently using local storage. */
record S3Config(URI endpoint, String bucket, String accessKey, String secretKey, String region) {

    static S3Config fromEnv(Map<String, String> env) {
        String[] names = {"S3_ENDPOINT", "S3_BUCKET", "S3_ACCESS_KEY", "S3_SECRET_KEY", "S3_REGION"};
        boolean any = false;
        for (String name : names) {
            any |= env.containsKey(name) && !env.get(name).isBlank();
        }
        if (!any) {
            return null;
        }
        for (String name : names) {
            if (env.get(name) == null || env.get(name).isBlank()) {
                throw new WorkerInputException(name + " is required when S3 is configured");
            }
        }
        URI endpoint;
        try {
            endpoint = URI.create(env.get("S3_ENDPOINT"));
        } catch (IllegalArgumentException e) {
            throw new WorkerInputException("S3_ENDPOINT must be a valid HTTP(S) URL");
        }
        if (!("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null) {
            throw new WorkerInputException("S3_ENDPOINT must be a valid HTTP(S) URL");
        }
        return new S3Config(endpoint, env.get("S3_BUCKET"), env.get("S3_ACCESS_KEY"),
                env.get("S3_SECRET_KEY"), env.get("S3_REGION"));
    }
}
