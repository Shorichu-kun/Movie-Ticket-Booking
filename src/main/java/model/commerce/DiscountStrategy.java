package model.commerce;

import java.math.BigDecimal;
import java.math.RoundingMode;

public interface DiscountStrategy {
  BigDecimal discount(DiscountContext context);

  record DiscountContext(
      BigDecimal eligible, BigDecimal amount, BigDecimal percent, BigDecimal maximum) {}

  final class FixedAmountDiscount implements DiscountStrategy {
    public BigDecimal discount(DiscountContext context) {
      return context.amount().min(context.eligible()).max(BigDecimal.ZERO);
    }
  }

  final class PercentageDiscount implements DiscountStrategy {
    public BigDecimal discount(DiscountContext context) {
      return context
          .eligible()
          .multiply(context.percent())
          .setScale(0, RoundingMode.DOWN)
          .min(context.maximum())
          .min(context.eligible())
          .max(BigDecimal.ZERO);
    }
  }

  final class FreeComboDiscount implements DiscountStrategy {
    public BigDecimal discount(DiscountContext context) {
      return context.eligible().min(context.maximum()).max(BigDecimal.ZERO);
    }
  }
}
