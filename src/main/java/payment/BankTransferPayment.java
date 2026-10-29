package payment;

import java.math.BigDecimal;

public class BankTransferPayment implements PaymentMethod {

    @Override
    public boolean pay(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        System.out.println("Chuyen khoan thanh cong: " + amount);
        return true;
    }
}