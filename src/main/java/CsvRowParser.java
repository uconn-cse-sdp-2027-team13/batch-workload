import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Checks the CSV header and turns one data line into an AccountPaymentRow.
 * The column layout matches the producer's CSV generator
 * (job-orchestrator, producer module, CsvDataGenerator.CSV_HEADER).
 * Assumes plain unquoted values, which is what that generator writes.
 */
final class CsvRowParser {

    static final String EXPECTED_HEADER = "account_number,email,payment_due_date,payment_amount";

    private static final int FIELD_COUNT = 4;

    // Limits come from the account_payment table, so a row the database would reject
    // is counted as a bad row instead of failing the whole batch.
    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 32;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int AMOUNT_MAX_SCALE = 2;
    private static final int AMOUNT_MAX_INTEGER_DIGITS = 10; // DECIMAL(12,2) leaves 10 digits before the point

    private CsvRowParser() {
    }

    static void requireValidHeader(String headerLine) {
        if (headerLine == null || !headerLine.strip().equals(EXPECTED_HEADER)) {
            throw new WorkerInputException(
                    "CSV header must be '" + EXPECTED_HEADER + "', got: " + headerLine);
        }
    }

    /**
     * Returns the parsed row, or null when the line is a bad row
     * (wrong number of values, an empty value, or a value that cannot be parsed).
     */
    static AccountPaymentRow parse(long lineNumber, String line) {
        // -1 keeps trailing empty values, so "a,b,c," gives 4 values with the last one empty.
        String[] fields = line.split(",", -1);
        if (fields.length != FIELD_COUNT) {
            return null;
        }
        String accountNumber = fields[0].strip();
        String email = fields[1].strip();
        String dueDateText = fields[2].strip();
        String amountText = fields[3].strip();

        if (accountNumber.isEmpty() || email.isEmpty() || dueDateText.isEmpty() || amountText.isEmpty()) {
            return null;
        }
        if (accountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH || email.length() > MAX_EMAIL_LENGTH) {
            return null;
        }

        LocalDate dueDate;
        BigDecimal amount;
        try {
            dueDate = LocalDate.parse(dueDateText); // expects YYYY-MM-DD
            amount = new BigDecimal(amountText);
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
        if (amount.scale() > AMOUNT_MAX_SCALE || amount.precision() - amount.scale() > AMOUNT_MAX_INTEGER_DIGITS) {
            return null;
        }
        return new AccountPaymentRow(lineNumber, accountNumber, email, dueDate, amount);
    }
}
