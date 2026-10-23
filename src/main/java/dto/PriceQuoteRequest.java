package dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PriceQuoteRequest(
        @NotNull(message = "Vui lòng nhập giá vé cơ bản.")
        @DecimalMin(value = "0.01", message = "Giá vé cơ bản phải lớn hơn 0.")
        @Digits(integer = 12, fraction = 2, message = "Giá vé cơ bản có tối đa 12 chữ số và 2 số thập phân.")
        BigDecimal basePrice,
        @NotNull(message = "Vui lòng nhập phụ phí ghế; nhập 0 nếu không có.")
        @DecimalMin(value = "0", message = "Phụ phí ghế không được âm.")
        @Digits(integer = 12, fraction = 2, message = "Phụ phí có tối đa 12 chữ số và 2 số thập phân.")
        BigDecimal seatSurcharge
) {
}
