/**
 * Thrown when the job's input is unusable: a missing or invalid env var,
 * or a CSV file with the wrong header. Main turns this into exit code 20.
 */
class WorkerInputException extends RuntimeException {
    WorkerInputException(String message) {
        super(message);
    }
}
