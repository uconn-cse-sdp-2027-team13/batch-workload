/**
 * Exit codes this worker returns when it finishes.
 * Mirrors ExitCode in job-orchestrator's common module
 * (branch feature/CORE-2-common-contracts, common/model/ExitCode.java).
 * Keep the names and numbers in sync with that file; this worker does not depend on common yet.
 */
enum ExitCode {
    SUCCESS(0),
    RETRYABLE_FAILURE(10),
    PERMANENT_FAILURE(20);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    int code() {
        return code;
    }
}
