import java.io.IOException;
import java.io.InputStream;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Opens an S3 object as a streaming response; callers must close the returned stream. */
final class S3Storage {
    private S3Storage() {}

    static InputStream open(S3Config config, String key) throws IOException {
        if (key.isBlank() || key.startsWith("/") || key.contains("\\"))
            throw new WorkerInputException("FILE_PATH must be a non-empty S3 object key");
        S3Client client;
        try {
            client = S3Client.builder()
                    .endpointOverride(config.endpoint())
                    .region(Region.of(config.region()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(config.accessKey(), config.secretKey())))
                    .serviceConfiguration(b -> b.pathStyleAccessEnabled(true))
                    .build();
        } catch (RuntimeException e) {
            throw new WorkerInputException("Invalid S3 configuration: " + e.getMessage());
        }
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(GetObjectRequest.builder()
                    .bucket(config.bucket()).key(key).build());
            return new ClosingS3Stream(stream, client);
        } catch (S3Exception e) {
            client.close();
            if (e.statusCode() == 404 || "NoSuchKey".equals(e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode()))
                throw new WorkerInputException("S3 object not found: " + key);
            if (e.statusCode() == 408 || e.statusCode() == 429 || e.statusCode() >= 500 || e.statusCode() == 0)
                throw new RetryableStorageException("S3 service error", e);
            throw new WorkerInputException("S3 rejected the request (HTTP " + e.statusCode() + ")");
        } catch (SdkClientException e) {
            client.close();
            throw new RetryableStorageException("Could not connect to S3", e);
        } catch (RuntimeException e) {
            client.close();
            throw new WorkerInputException("Invalid S3 request: " + e.getMessage());
        }
    }

    private static final class ClosingS3Stream extends java.io.FilterInputStream {
        private final S3Client client;
        ClosingS3Stream(InputStream stream, S3Client client) { super(stream); this.client = client; }
        @Override public int read() throws IOException {
            try { return super.read(); } catch (IOException e) { throw new RetryableStorageException("S3 stream failed", e); }
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            try { return super.read(bytes, offset, length); } catch (IOException e) { throw new RetryableStorageException("S3 stream failed", e); }
        }
        @Override public void close() throws IOException {
            try { super.close(); } finally { client.close(); }
        }
    }
}

class RetryableStorageException extends IOException {
    RetryableStorageException(String message, Throwable cause) { super(message, cause); }
}
