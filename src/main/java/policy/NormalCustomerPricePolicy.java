package policy;

import java.math.BigDecimal;

public class NormalCustomerPricePolicy implements TicketPricePolicy {
    @Override
    public BigDecimal getDiscountRate() {
        return BigDecimal.ZERO;
    }
}
