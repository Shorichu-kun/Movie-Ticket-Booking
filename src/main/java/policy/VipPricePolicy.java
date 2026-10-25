package policy;

import java.math.BigDecimal;

public class VipPricePolicy implements TicketPricePolicy {
    private final BigDecimal discountRate;

    public VipPricePolicy(BigDecimal discountRate) {
        this.discountRate = TicketPricePolicy.validateDiscountRate(discountRate);
    }

    @Override
    public BigDecimal getDiscountRate() {
        return discountRate;
    }
}
