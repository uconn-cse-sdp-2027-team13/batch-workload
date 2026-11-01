# Java 17 worker

Reads `JOB_ID` and `FILE_PATH`, then streams the file to stdout. Relative file
paths resolve under `/data`; absolute paths are used as supplied. Mount the
input directory at `/data` in the container or Kubernetes pod. Missing variables
or unreadable files cause a nonzero exit.

The orchestrator's `common` module contains shared Java contracts and models; it
is separate from the input directory. This standalone worker does not yet depend
on that module. Shared-module integration and S3 input are future work; local
file streaming remains the initial worker behavior.

Build and run from the repository root (the sample file is in `data/input.txt`):

```sh
docker build -t java-worker .
docker run --rm \
  -e JOB_ID=example-job \
  -e FILE_PATH=input.txt \
  --mount "type=bind,source=$PWD/data,target=/data,readonly" \
  java-worker
```

The Docker build uses Maven and Java 17 to produce an executable JAR, then copies
it into a Java 17 JRE image. With Maven and JDK 17+ installed, you can also build
and run locally using an absolute input path:

```sh
mvn package
JOB_ID=example-job FILE_PATH="$PWD/data/input.txt" java -jar target/worker.jar
```
