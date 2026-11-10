/**
 * The counters stored in the file_progress row for this job's LOAD step.
 * checkpointOffset is the line number of the last data row whose batch was committed,
 * so a retry skips that many data rows and carries on from there.
 */
record LoadProgress(long recordsRead, long recordsInserted, long recordsFailed, long checkpointOffset) {

    static final LoadProgress NONE = new LoadProgress(0, 0, 0, 0);

    /** Returns new totals after one more batch; the old object is left unchanged. */
    LoadProgress plusBatch(long linesRead, long inserted, long failed, long lastLineNumber) {
        return new LoadProgress(
                recordsRead + linesRead,
                recordsInserted + inserted,
                recordsFailed + failed,
                lastLineNumber);
    }
}
