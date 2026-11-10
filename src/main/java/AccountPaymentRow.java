import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One parsed CSV data row, ready to insert into account_payment.
 * lineNumber counts data rows only: the first row after the header is 1.
 */
record AccountPaymentRow(
        long lineNumber,
        String accountNumber,
        String email,
        LocalDate paymentDueDate,
        BigDecimal paymentAmount) {
}
