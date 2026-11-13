import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkerConfigTest {

    @Test
    void batchSizeDefaultsTo1000() {
        WorkerConfig config = WorkerConfig.fromEnv(validEnv());

        assertEquals(1000, config.batchSize());
    }

    @Test
    void relativeFilePathResolvesUnderData() {
        WorkerConfig config = WorkerConfig.fromEnv(validEnv());

        assertEquals(Path.of("/data").resolve("input.csv"), config.file());
    }

    @Test
    void parsesCompleteS3ConfigurationAndKeepsFilePathAsObjectKey() {
        Map<String, String> env = validEnv();
        env.put("FILE_PATH", "jobs/job-1/input.csv");
        env.put("S3_ENDPOINT", "http://rustfs:9000");
        env.put("S3_BUCKET", "uploads");
        env.put("S3_ACCESS_KEY", "access");
        env.put("S3_SECRET_KEY", "secret");
        env.put("S3_REGION", "us-east-1");
        WorkerConfig config = WorkerConfig.fromEnv(env);
        assertEquals(true, config.usesS3());
        assertEquals("jobs/job-1/input.csv", config.objectKey());
        assertEquals("uploads", config.s3().bucket());
    }

    @Test
    void rejectsPartialS3Configuration() {
        Map<String, String> env = validEnv();
        env.put("S3_ENDPOINT", "http://rustfs:9000");
        assertThrows(WorkerInputException.class, () -> WorkerConfig.fromEnv(env));
    }

    @Test
    void rejectsInvalidS3Endpoint() {
        Map<String, String> env = validEnv();
        env.put("S3_ENDPOINT", "file:///tmp/bucket");
        env.put("S3_BUCKET", "uploads");
        env.put("S3_ACCESS_KEY", "access");
        env.put("S3_SECRET_KEY", "secret");
        env.put("S3_REGION", "us-east-1");
        assertThrows(WorkerInputException.class, () -> WorkerConfig.fromEnv(env));
    }

    @Test
    void readsBatchSizeInRange() {
        Map<String, String> env = validEnv();
        env.put("BATCH_SIZE", "5000");

        assertEquals(5000, WorkerConfig.fromEnv(env).batchSize());
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "5001", "abc"})
    void rejectsBadBatchSize(String value) {
        Map<String, String> env = validEnv();
        env.put("BATCH_SIZE", value);

        assertThrows(WorkerInputException.class, () -> WorkerConfig.fromEnv(env));
    }

    @ParameterizedTest
    @ValueSource(strings = {"JOB_ID", "FILE_PATH", "JDBC_URL", "DB_USER", "DB_PASSWORD"})
    void rejectsMissingRequiredVar(String name) {
        Map<String, String> env = validEnv();
        env.remove(name);

        assertThrows(WorkerInputException.class, () -> WorkerConfig.fromEnv(env));
    }

    private static Map<String, String> validEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("JOB_ID", "job-1");
        env.put("FILE_PATH", "input.csv");
        env.put("JDBC_URL", "jdbc:mysql://db:3306/app");
        env.put("DB_USER", "worker");
        env.put("DB_PASSWORD", "secret");
        return env;
    }
}
