package service.commerce;

import handler.ApiException;
import java.util.*;

public final class PaymentMethodRegistry {
  private final Map<String, PaymentMethod> methods;

  public PaymentMethodRegistry() {
    PaymentMethod wallet = new PaymentMethod.EWalletPayment(),
        card = new PaymentMethod.BankCardPayment(),
        bank = new PaymentMethod.BankTransferPayment();
    methods =
        Map.of(
            "MOMO",
            wallet,
            "ZALOPAY",
            wallet,
            "SHOPEEPAY",
            wallet,
            "VNPAY",
            bank,
            "ATM",
            card,
            "CARD",
            card,
            "TRANSFER",
            bank,
            "CASH",
            new PaymentMethod.CashPayment(),
            "LOYALTY",
            new PaymentMethod.LoyaltyPayment());
  }

  public PaymentMethod require(String code) {
    PaymentMethod method = methods.get(code == null ? "" : code);
    if (method == null) throw new ApiException(400, "Phương thức thanh toán không hợp lệ.");
    return method;
  }

  public List<Map<String, Object>> views() {
    return List.of(
        view("MOMO", "MoMo", "WALLET"),
        view("ZALOPAY", "ZaloPay", "WALLET"),
        view("SHOPEEPAY", "ShopeePay", "WALLET"),
        view("VNPAY", "VNPAY QR", "QR"),
        view("ATM", "Thẻ ATM", "CARD"),
        view("CARD", "Visa / Mastercard / JCB", "CARD"),
        view("TRANSFER", "Chuyển khoản", "TRANSFER"),
        view("CASH", "Tại quầy", "CASH"),
        view("LOYALTY", "Điểm / ưu đãi N6", "LOYALTY"));
  }

  private Map<String, Object> view(String code, String label, String family) {
    return Map.of("code", code, "label", label + " — mô phỏng", "family", family);
  }
}
