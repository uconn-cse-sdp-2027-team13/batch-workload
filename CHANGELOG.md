# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial Java 17 worker pod application that reads `JOB_ID` and `FILE_PATH` environment variables and streams file contents to standard output ([#1](https://github.com/uconn-cse-sdp-2027-team13/batch-workload/issues/1)).
- Maven build configuration (`pom.xml`) targeting Java 17 release and packaging an executable JAR ([#1](https://github.com/uconn-cse-sdp-2027-team13/batch-workload/issues/1)).
- Multi-stage Dockerfile compiling worker with Maven and packaging into a lightweight `eclipse-temurin:17-jre` container ([#1](https://github.com/uconn-cse-sdp-2027-team13/batch-workload/issues/1)).
- Portable Maven wrapper (`mvnw`) and `.mvn` configuration for reproducible builds ([#4](https://github.com/uconn-cse-sdp-2027-team13/batch-workload/issues/4)).
- Comprehensive `.gitignore` covering Java, Maven, IDEs, and OS-generated files ([#4](https://github.com/uconn-cse-sdp-2027-team13/batch-workload/issues/4)).
- Centralized GitHub Actions CI verification workflow calling reusable pipeline from org `.github` ([#4](https://github.com/uconn-cse-sdp-2027-team13/batch-workload/issues/4)).
- Added `mysql-connector-j` 8.4.0, plus JUnit 5.10.2 and H2 2.3.232 for tests; `worker.jar` now bundles the MySQL driver ([#3](https://github.com/uconn-cse-sdp-2027-team13/batch-workload/issues/3)).

### Changed

- Worker now streams the CSV file line by line and loads account records into MySQL `account_payment` with JDBC batched inserts (`BATCH_SIZE`, default 1,000, allowed 1,000–5,000). After each batch it updates the `file_progress` LOAD row in the same transaction, resumes from `checkpoint_offset` on retry, and exits 0 on success, 10 on database errors, or 20 on input errors ([#3](https://github.com/uconn-cse-sdp-2027-team13/batch-workload/issues/3)).

