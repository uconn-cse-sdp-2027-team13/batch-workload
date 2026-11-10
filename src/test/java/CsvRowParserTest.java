import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvRowParserTest {

    @Test
    void parsesAValidRow() {
        AccountPaymentRow row = CsvRowParser.parse(7, "ACC-00000001,a@example.com,2026-12-01,1234.50");

        assertEquals(new AccountPaymentRow(7, "ACC-00000001", "a@example.com",
                LocalDate.of(2026, 12, 1), new BigDecimal("1234.50")), row);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                                              // blank line
            "ACC-1,a@example.com,2026-12-01",                // 3 values
            "ACC-1,a@example.com,2026-12-01,1.00,extra",     // 5 values
            ",a@example.com,2026-12-01,1.00",                // empty account number
            "ACC-1,,2026-12-01,1.00",                        // empty email
            "ACC-1,a@example.com,,1.00",                     // empty due date
            "ACC-1,a@example.com,2026-12-01,",               // empty amount
            "ACC-1,a@example.com,12/01/2026,1.00",           // date not YYYY-MM-DD
            "ACC-1,a@example.com,2026-12-01,ten",            // amount not a number
            "ACC-1,a@example.com,2026-12-01,1.005",          // more than 2 decimal places
            "ACC-1,a@example.com,2026-12-01,12345678901.00", // too big for DECIMAL(12,2)
            "ACC-123456789012345678901234567890,a@example.com,2026-12-01,1.00" // account number over 32 chars
    })
    void badRowsReturnNull(String line) {
        assertNull(CsvRowParser.parse(1, line));
    }

    @Test
    void acceptsExpectedHeader() {
        assertDoesNotThrow(() -> CsvRowParser.requireValidHeader(CsvRowParser.EXPECTED_HEADER));
    }

    @Test
    void rejectsWrongOrMissingHeader() {
        assertThrows(WorkerInputException.class,
                () -> CsvRowParser.requireValidHeader("email,account_number,payment_due_date,payment_amount"));
        assertThrows(WorkerInputException.class, () -> CsvRowParser.requireValidHeader(null)); // empty file
    }
}
