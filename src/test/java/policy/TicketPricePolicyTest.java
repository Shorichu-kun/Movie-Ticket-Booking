package policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketPricePolicyTest {
    @Test
    void roundsMoneyWithBigDecimalAndHalfUp() {
        TicketPricePolicy policy = new StudentPricePolicy(new BigDecimal("0.10"));
        assertThat(policy.calculatePrice(new BigDecimal("100.05"), BigDecimal.ZERO))
                .isEqualByComparingTo("90.05");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1", "1.5"})
    void rejectsDiscountRatesThatCouldProduceInvalidPrices(String rate) {
        assertThatThrownBy(() -> new StudentPricePolicy(new BigDecimal(rate)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VipPricePolicy(new BigDecimal(rate)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverReturnsAZeroOrNegativeFinalPrice() {
        TicketPricePolicy policy = new VipPricePolicy(new BigDecimal("0.99"));
        assertThatThrownBy(() -> policy.calculatePrice(new BigDecimal("0.01"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.calculatePrice(BigDecimal.ZERO, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.calculatePrice(BigDecimal.TEN, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
