# Java 17 worker

The worker loads a CSV into MySQL and returns the exit code consumed by the
orchestrator. It reads `JOB_ID`, `FILE_PATH`, `JDBC_URL`, `DB_USER`, and
`DB_PASSWORD`; `BATCH_SIZE` is optional and defaults to 1000.

When all five S3 variables are set, `FILE_PATH` is an S3 object key and the
worker streams that object directly into the CSV loader:

- `S3_ENDPOINT` (for example, `http://host.docker.internal:9000` for RustFS)
- `S3_BUCKET`
- `S3_ACCESS_KEY`
- `S3_SECRET_KEY`
- `S3_REGION` (for example, `us-east-1`)

The S3 client uses path-style access for S3-compatible storage. Partial S3
configuration is rejected. Without S3 variables, `FILE_PATH` uses the local
file fallback: relative paths resolve under `/data`, and absolute paths are
used as supplied. Mount `/data` only for this standalone local-file mode.

Exit codes are `0` for success, `10` for retryable S3 or database failures, and
`20` for invalid configuration, missing input objects, or invalid CSV headers.

Build and run locally with the Maven wrapper:

```sh
./mvnw clean package
JOB_ID=example-job FILE_PATH="$PWD/data/input.csv" JDBC_URL=jdbc:mysql://localhost:3306/app \
  DB_USER=worker DB_PASSWORD=secret java -jar target/worker.jar
```

For a container workload, pass the database and S3 settings as environment
variables. For example, `S3_ENDPOINT=http://host.docker.internal:9000` reaches
RustFS from Docker Desktop Kubernetes when that endpoint is reachable from the
pod. The Docker image builds the executable shaded JAR with Maven and runs it
on Java 17.
