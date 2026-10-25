package dto;

import model.CustomerType;

import java.math.BigDecimal;

public record PriceQuoteResponse(String customerId, CustomerType customerType, BigDecimal basePrice,
                                 BigDecimal seatSurcharge, BigDecimal subtotal, BigDecimal discountRate,
                                 BigDecimal discountAmount, BigDecimal finalPrice, String currency) {
}
