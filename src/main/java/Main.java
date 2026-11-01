import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        String jobId = requiredEnv("JOB_ID");
        Path file = Path.of("/data").resolve(requiredEnv("FILE_PATH"));

        System.out.println("Job " + jobId + ": reading " + file);
        Files.copy(file, System.out);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
