package service.commerce;

import handler.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public interface PaymentMethod {
  PaymentInitiation initiate(PaymentRequest request);

  VerifiedPaymentResult verify(PaymentEvent event);

  record PaymentRequest(String orderId, BigDecimal amount, Instant expiresAt) {}

  record PaymentInitiation(String status, BigDecimal amount, Instant expiresAt) {}

  record PaymentEvent(String scenario, BigDecimal amount) {}

  record VerifiedPaymentResult(String status, BigDecimal amount, boolean late) {}

  abstract class DemoMethod implements PaymentMethod {
    public PaymentInitiation initiate(PaymentRequest r) {
      return new PaymentInitiation("CREATED", r.amount(), r.expiresAt());
    }

    public VerifiedPaymentResult verify(PaymentEvent event) {
      if (event.scenario() == null
          || !Set.of("SUCCESS", "FAILED", "CANCELLED", "PROCESSING", "LATE_SUCCESS")
              .contains(event.scenario()))
        throw new ApiException(400, "Kịch bản thanh toán không hợp lệ.");
      return new VerifiedPaymentResult(
          switch (event.scenario()) {
            case "SUCCESS", "LATE_SUCCESS" -> "SUCCEEDED";
            default -> event.scenario();
          },
          event.amount(),
          event.scenario().equals("LATE_SUCCESS"));
    }
  }

  final class EWalletPayment extends DemoMethod {}

  final class BankCardPayment extends DemoMethod {}

  final class BankTransferPayment extends DemoMethod {}

  final class CashPayment extends DemoMethod {}

  final class LoyaltyPayment extends DemoMethod {}
}
