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

final class PricingService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
  final Map<String, Product> products;
  private final CommerceCatalog catalog;
  private final Clock clock;
  private final SeatInventoryService inventory;
  private final LoyaltyService loyalty;
  private final VoucherService vouchers;

  PricingService(
      CommerceCatalog catalog,
      Clock clock,
      SeatInventoryService inventory,
      LoyaltyService loyalty,
      VoucherService vouchers) {
    this.catalog = catalog;
    this.clock = clock;
    this.inventory = inventory;
    this.loyalty = loyalty;
    this.vouchers = vouchers;
    products = new LinkedHashMap<>();
    products.put(
        "SINGLE",
        new Product(
            "SINGLE",
            "Combo một người",
            "Bắp và một nước; nhận tại quầy.",
            money(59000),
            List.of("Bắp ngọt · Coca", "Bắp phô mai · Sprite")));
    products.put(
        "DUO",
        new Product(
            "DUO",
            "Combo hai người",
            "Bắp lớn và hai nước.",
            money(89000),
            List.of("Bắp ngọt · Coca", "Bắp phô mai · Sprite")));
    products.put(
        "FAMILY",
        new Product(
            "FAMILY",
            "Combo gia đình",
            "Hai bắp lớn và ba nước.",
            money(149000),
            List.of("Bắp ngọt · Coca", "Bắp phô mai · Sprite")));
  }

  Pricing price(CommerceState s, Customer customer, SeatHold hold, CheckoutRequest r) {
    Showtime show = inventory.show(hold.showtimeId(), true);
    List<OrderItem> items = new ArrayList<>();
    BigDecimal tickets = BigDecimal.ZERO, legacyTickets = BigDecimal.ZERO, combos = BigDecimal.ZERO;
    for (String seatId : hold.seatIds()) {
      Seat seat = inventory.seat(show, seatId);
      BigDecimal raw =
          money(show.getPrice())
              .add(
                  money(
                      switch (seat.getType()) {
                        case NORMAL -> 0;
                        case VIP -> 20000;
                        case COUPLE -> 50000;
                      }));
      tickets = tickets.add(raw);
      legacyTickets = legacyTickets.add(catalog.ticketPrice(show, seat, customer));
      items.add(new TicketItem("Vé " + seat.getSeatLabel(), raw, seatId));
    }
    int quantity = 0;
    for (ComboRequest combo : r.combos() == null ? List.<ComboRequest>of() : r.combos()) {
      if (combo == null) throw error(400, "Combo không hợp lệ.");
      Product p = products.get(combo.productId());
      if (p == null || combo.quantity() < 1 || combo.quantity() > 5)
        throw error(400, "Combo hoặc số lượng không hợp lệ.");
      String variant = combo.variant() == null ? p.variants().get(0) : combo.variant();
      if (!p.variants().contains(variant)) throw error(400, "Biến thể combo không hợp lệ.");
      quantity += combo.quantity();
      if (quantity > 5) throw error(400, "Mỗi đơn tối đa 5 combo.");
      ComboItem item =
          new ComboItem(p.name() + " · " + variant, combo.quantity(), p.price(), p.id(), variant);
      items.add(item);
      combos = combos.add(item.total());
    }
    BigDecimal subtotal = tickets.add(combos), discount = tickets.subtract(legacyTickets);
    String label = discount.signum() > 0 ? "Ưu đãi " + customer.getCustomerType() : "Không áp dụng";
    String voucherId = null;
    normalizeMethod(r.method());
    if (r.voucherCode() != null && !r.voucherCode().isBlank()) {
      VoucherService.Campaign campaign = vouchers.campaign(r.voucherCode());
      Member m = member(s, customer.getId());
      CustomerVoucher voucher =
          m.vouchers().stream()
              .filter(
                  v ->
                      v.code().equals(campaign.code())
                          && v.status().equals("AVAILABLE")
                          && now().isBefore(v.expiresAt()))
              .findFirst()
              .orElseThrow(
                  () -> error(409, "Voucher chưa có trong ví, đã hết hạn hoặc đang được dùng."));
      if (subtotal.compareTo(campaign.minimumAmount()) < 0)
        throw error(409, "Đơn chưa đạt giá trị tối thiểu của voucher.");
      if (campaign.code().equals("N6WELCOME30") && m.gifts().contains("first-paid"))
        throw error(409, "Voucher chỉ áp dụng lần thanh toán đầu tiên.");
      if (campaign.weekday() && inventory.start(show).atZone(ZONE).getDayOfWeek().getValue() > 5)
        throw error(409, "Voucher chỉ áp dụng suất từ thứ 2 đến thứ 6.");
      if (campaign.combo() && combos.signum() == 0) throw error(409, "Voucher yêu cầu có combo.");
      BigDecimal eligible =
          campaign.weekday()
              ? tickets
              : campaign.combo()
                  ? items.stream()
                      .filter(i -> i instanceof ComboItem)
                      .map(OrderItem::unitPrice)
                      .max(Comparator.naturalOrder())
                      .orElse(BigDecimal.ZERO)
                  : subtotal;
      BigDecimal voucherDiscount =
          campaign
              .discountStrategy()
              .discount(
                  new DiscountStrategy.DiscountContext(
                      eligible,
                      campaign.discountAmount(),
                      campaign.percent(),
                      campaign.maxDiscount()));
      if (voucherDiscount.compareTo(discount) >= 0) {
        discount = voucherDiscount;
        label = campaign.title();
        voucherId = voucher.id();
      }
    }
    BigDecimal after = subtotal.subtract(discount).max(BigDecimal.ZERO);
    int points = r.points();
    if (points < 0 || (points > 0 && points < 10))
      throw error(400, "Dùng tối thiểu 10 điểm và không dùng điểm âm.");
    if (points > loyalty.availablePoints(s, customer.getId()))
      throw error(409, "Điểm khả dụng không đủ.");
    BigDecimal pointsValue = THOUSAND.multiply(BigDecimal.valueOf(points));
    if (pointsValue.compareTo(after) > 0) throw error(400, "Điểm vượt quá giá trị đơn.");
    BigDecimal total = after.subtract(pointsValue);
    LoyaltyService.Tier tier = loyalty.tier(loyalty.yearlySpend(s, customer.getId()));
    int earned = total.multiply(tier.rate()).divide(THOUSAND, 0, RoundingMode.DOWN).intValueExact();
    return new Pricing(
        items,
        new PriceQuote(
            subtotal,
            tickets,
            combos,
            discount,
            label,
            points,
            pointsValue,
            total,
            earned,
            hold.expiresAt(),
            tier.rate(),
            "N6-2026-1"),
        voucherId);
  }

  private String normalizeMethod(String code) {
    String method = code == null || code.isBlank() ? "MOMO" : code.toUpperCase(Locale.ROOT);
    new PaymentMethodRegistry().require(method);
    return method;
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

  record Pricing(List<OrderItem> items, PriceQuote quote, String voucherId) {
    Pricing deadline(Instant expires) {
      return new Pricing(
          items,
          new PriceQuote(
              quote.subtotal(),
              quote.ticketSubtotal(),
              quote.comboSubtotal(),
              quote.discount(),
              quote.discountLabel(),
              quote.pointsUsed(),
              quote.pointsValue(),
              quote.total(),
              quote.pointsEarned(),
              expires,
              quote.earnRate(),
              quote.policyVersion()),
          voucherId);
    }
  }

  record Product(
      String id, String name, String description, BigDecimal price, List<String> variants) {}
}
