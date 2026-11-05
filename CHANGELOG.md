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

