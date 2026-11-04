package service.commerce;

import handler.ApiException;
import java.math.*;
import java.time.*;
import java.util.*;
import model.*;
import model.commerce.*;
import model.commerce.CommerceRequests.*;
import model.commerce.CommerceState.*;
import repository.commerce.*;

final class VoucherService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  final Map<String, Campaign> campaigns;
  private final Clock clock;

  VoucherService(Clock clock) {
    this.clock = clock;
    campaigns = new LinkedHashMap<>();
    campaigns.put(
        "N6WELCOME30",
        new Campaign(
            "N6WELCOME30",
            "Chào mừng N6",
            "Giảm 30.000đ cho đơn đầu từ 250.000đ.",
            money(250000),
            money(30000),
            BigDecimal.ZERO,
            money(30000),
            false,
            false));
    campaigns.put(
        "N6WEEKDAY10",
        new Campaign(
            "N6WEEKDAY10",
            "Thứ thường vui hơn",
            "Giảm 10% vé từ thứ 2 đến thứ 6, tối đa 30.000đ.",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.10"),
            money(30000),
            true,
            false));
    campaigns.put(
        "N6COMBO",
        new Campaign(
            "N6COMBO",
            "Quà combo N6",
            "Giảm giá một combo tối đa 59.000đ.",
            BigDecimal.ZERO,
            money(59000),
            BigDecimal.ZERO,
            money(59000),
            false,
            true));
  }

  void reserveVoucher(CommerceState s, Order o) {
    if (o.voucherId() == null) return;
    Member m = member(s, o.customerId());
    for (int i = 0; i < m.vouchers().size(); i++) {
      CustomerVoucher v = m.vouchers().get(i);
      if (v.id().equals(o.voucherId())) {
        if (!v.status().equals("AVAILABLE")) throw error(409, "Voucher đang được dùng.");
        m.vouchers().set(i, v.status("RESERVED", o.id()));
        return;
      }
    }
    throw error(409, "Voucher không tồn tại.");
  }

  void consumeVoucher(CommerceState s, Order o) {
    if (o.voucherId() == null) return;
    Member m = member(s, o.customerId());
    for (int i = 0; i < m.vouchers().size(); i++) {
      CustomerVoucher v = m.vouchers().get(i);
      if (v.id().equals(o.voucherId())) {
        if (!v.status().equals("RESERVED") || !Objects.equals(v.orderId(), o.id()))
          throw error(409, "Lượt voucher không được giữ cho đơn.");
        m.vouchers().set(i, v.status("USED", o.id()));
        return;
      }
    }
  }

  void grantVoucher(Member m, Campaign c, Instant expiration) {
    m.vouchers()
        .add(new CustomerVoucher(uuid(), c.code(), c.title(), "AVAILABLE", expiration, null));
  }

  void birthdayGift(CommerceState s, String owner) {
    Member m = member(s, owner);
    String birth = m.preferences().birthDate();
    LocalDate today = LocalDate.now(clock.withZone(ZONE));
    if (birth != null
        && LocalDate.parse(birth).getMonth() == today.getMonth()
        && m.gifts().add("birthday:" + today.getYear())) {
      grantVoucher(
          m,
          campaigns.get("N6COMBO"),
          today.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZONE).toInstant());
      m.notifications()
          .add(
              new Notification(
                  uuid(),
                  "Chúc mừng sinh nhật",
                  "N6 tặng bạn voucher combo mô phỏng.",
                  now(),
                  null,
                  false));
    }
  }

  Campaign campaign(String code) {
    Campaign c = campaigns.get(code == null ? "" : code.strip().toUpperCase(Locale.ROOT));
    if (c == null) throw error(404, "Mã ưu đãi không tồn tại.");
    return c;
  }

  Instant campaignEnd() {
    return LocalDate.of(2030, 1, 1).atStartOfDay(ZONE).toInstant();
  }

  void releaseVoucher(CommerceState s, Order o, boolean refund) {
    if (o.voucherId() == null) return;
    Member m = member(s, o.customerId());
    for (int i = 0; i < m.vouchers().size(); i++) {
      CustomerVoucher v = m.vouchers().get(i);
      if (v.id().equals(o.voucherId())
          && Objects.equals(v.orderId(), o.id())
          && (v.status().equals("RESERVED") || (refund && v.status().equals("USED"))))
        m.vouchers()
            .set(i, v.status(now().isBefore(v.expiresAt()) ? "AVAILABLE" : "EXPIRED", null));
    }
  }

  private Instant now() {
    return clock.instant();
  }

  private String uuid() {
    return UUID.randomUUID().toString();
  }

  private static BigDecimal money(double amount) {
    return BigDecimal.valueOf(amount).setScale(0, RoundingMode.HALF_UP);
  }

  private ApiException error(int status, String message) {
    return new ApiException(status, message);
  }

  private Member member(CommerceState s, String owner) {
    return s.members().computeIfAbsent(owner, k -> new Member());
  }

  record Campaign(
      String code,
      String title,
      String description,
      BigDecimal minimumAmount,
      BigDecimal discountAmount,
      BigDecimal percent,
      BigDecimal maxDiscount,
      boolean weekday,
      boolean combo) {
    DiscountStrategy discountStrategy() {
      if (combo) return new DiscountStrategy.FreeComboDiscount();
      if (percent.signum() > 0) return new DiscountStrategy.PercentageDiscount();
      return new DiscountStrategy.FixedAmountDiscount();
    }
  }
}
