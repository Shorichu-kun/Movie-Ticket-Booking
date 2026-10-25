package dto;

import model.CustomerType;

import java.math.BigDecimal;

public record CustomerResponse(String id, String fullName, String phone, String email,
                               CustomerType customerType, String studentId, BigDecimal discountRate) {
}
