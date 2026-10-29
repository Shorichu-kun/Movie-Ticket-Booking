package payment;

import java.math.BigDecimal;

public class EWalletPayment implements PaymentMethod {

    @Override
    public boolean pay(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        System.out.println("Thanh toan vi dien tu thanh cong: " + amount);
        return true;
    }
}