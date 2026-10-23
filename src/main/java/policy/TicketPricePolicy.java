package policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

public interface TicketPricePolicy {
    BigDecimal getDiscountRate();

    default BigDecimal calculatePrice(BigDecimal basePrice, BigDecimal seatSurcharge) {
        if (basePrice == null || basePrice.signum() <= 0) {
            throw new IllegalArgumentException("Giá vé cơ bản phải lớn hơn 0.");
        }
        if (seatSurcharge == null || seatSurcharge.signum() < 0) {
            throw new IllegalArgumentException("Phụ phí ghế không được âm.");
        }
        BigDecimal price = basePrice.add(seatSurcharge)
                .multiply(BigDecimal.ONE.subtract(getDiscountRate()))
                .setScale(2, RoundingMode.HALF_UP);
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Giá vé sau ưu đãi phải lớn hơn 0.");
        }
        return price;
    }

    static BigDecimal validateDiscountRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Tỷ lệ ưu đãi phải từ 0 đến nhỏ hơn 1.");
        }
        return rate;
    }
}
