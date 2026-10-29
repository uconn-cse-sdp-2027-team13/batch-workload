# Java 17 worker

Reads `JOB_ID` and `FILE_PATH`, then streams the file to stdout. Relative file
paths resolve under `/common`; absolute paths are used as supplied. Mount the
input directory at `/common` in the container or Kubernetes pod. Missing variables
or unreadable files cause a nonzero exit.

```sh
docker build -t java-worker .
docker run --rm \
  -e JOB_ID=example-job \
  -e FILE_PATH=input.txt \
  -v "$PWD/common:/common:ro" \
  java-worker
```

Place an `input.txt` file in a local `common` directory before running.
