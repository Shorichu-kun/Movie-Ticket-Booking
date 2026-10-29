package payment;

import java.math.BigDecimal;

public class CashPayment implements PaymentMethod {

    @Override
    public boolean pay(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        System.out.println("Thanh toan tien mat thanh cong: " + amount);
        return true;
    }
}