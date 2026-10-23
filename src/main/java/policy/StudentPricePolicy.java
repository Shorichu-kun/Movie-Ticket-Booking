package policy;

import java.math.BigDecimal;

public class StudentPricePolicy implements TicketPricePolicy {
    private final BigDecimal discountRate;

    public StudentPricePolicy(BigDecimal discountRate) {
        this.discountRate = TicketPricePolicy.validateDiscountRate(discountRate);
    }

    @Override
    public BigDecimal getDiscountRate() {
        return discountRate;
    }
}
