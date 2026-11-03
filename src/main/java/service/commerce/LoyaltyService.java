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

final class LoyaltyService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
  private final Clock clock;

  LoyaltyService(Clock clock) {
    this.clock = clock;
  }

  void awardPoints(CommerceState s, Order o) {
    int points = o.quote().pointsEarned();
    if (points == 0) return;
    Member m = member(s, o.customerId());
    int debtPayment = Math.min(m.debt(), points);
    if (debtPayment > 0) {
      m = m.debt(m.debt() - debtPayment);
      s.members().put(o.customerId(), m);
    }
    Instant expiry = now().atZone(ZONE).plusMonths(12).toInstant();
    m.lots().add(new PointLot(uuid(), points - debtPayment, expiry, o.id()));
    m.entries()
        .add(
            new PointEntry(
                uuid(), "EARN", points, now(), "Tích điểm thanh toán thử", o.id(), expiry));
  }

  void consumePoints(CommerceState s, String owner, int points, String source) {
    if (points == 0) return;
    Member m = member(s, owner);
    if (balance(m) < points) throw error(409, "Không đủ điểm để hoàn tất.");
    int left = points;
    List<PointUse> uses = new ArrayList<>();
    m.lots().sort(Comparator.comparing(PointLot::expiresAt));
    for (int i = 0; i < m.lots().size() && left > 0; i++) {
      PointLot lot = m.lots().get(i);
      int spent = Math.min(lot.remaining(), left);
      if (spent > 0) {
        m.lots().set(i, lot.remaining(lot.remaining() - spent));
        uses.add(new PointUse(lot.id(), spent, lot.expiresAt()));
        left -= spent;
      }
    }
    if (left > 0) throw error(409, "Điểm đã hết hạn.");
    m.pointUses().put(source, uses);
    m.entries()
        .add(
            new PointEntry(
                uuid(),
                source.startsWith("reward:") ? "REDEEM" : "SPEND",
                -points,
                now(),
                source.startsWith("reward:") ? "Đổi voucher" : "Dùng điểm thanh toán",
                source,
                null));
  }

  void reversePoints(CommerceState s, Order o) {
    Member m = member(s, o.customerId());
    Set<String> sourceLots = new HashSet<>();
    int unspent = 0;
    for (int i = 0; i < m.lots().size(); i++) {
      PointLot lot = m.lots().get(i);
      if (Objects.equals(lot.sourceOrder(), o.id())) {
        sourceLots.add(lot.id());
        unspent += lot.remaining();
        m.lots().set(i, lot.remaining(0));
      }
    }
    int spent =
        m.pointUses().values().stream()
            .flatMap(Collection::stream)
            .filter(use -> sourceLots.contains(use.lotId()))
            .mapToInt(PointUse::points)
            .sum();
    if (o.quote().pointsEarned() > 0) {
      m = m.debt(m.debt() + spent);
      s.members().put(o.customerId(), m);
      m.entries()
          .add(
              new PointEntry(
                  uuid(),
                  "REVERSAL",
                  -unspent - spent,
                  now(),
                  spent > 0
                      ? "Đảo điểm đơn hoàn; điểm đã dùng trở thành khoản cần bù: " + spent
                      : "Đảo điểm đơn hoàn (không tính lại điểm đã hết hạn)",
                  o.id(),
                  null));
    }
    List<PointUse> uses = m.pointUses().remove(o.id());
    int restored = 0;
    for (PointUse use : uses == null ? List.<PointUse>of() : uses)
      if (now().isBefore(use.expiresAt())) {
        m.lots().add(new PointLot(uuid(), use.points(), use.expiresAt(), "refund:" + o.id()));
        restored += use.points();
      }
    if (restored > 0)
      m.entries()
          .add(
              new PointEntry(
                  uuid(), "RESTORE", restored, now(), "Trả điểm đã dùng còn hạn", o.id(), null));
  }

  int reservedPoints(CommerceState s, String owner) {
    return s.orders().values().stream()
        .filter(o -> o.customerId().equals(owner) && o.status().equals("PENDING_PAYMENT"))
        .mapToInt(o -> o.quote().pointsUsed())
        .sum();
  }

  int availablePoints(CommerceState s, String owner) {
    return Math.max(0, balance(member(s, owner)) - reservedPoints(s, owner));
  }

  BigDecimal yearlySpend(CommerceState s, String owner) {
    return s.orders().values().stream()
        .filter(
            o ->
                o.customerId().equals(owner)
                    && Set.of("PAID", "REFUND_PENDING").contains(o.status())
                    && o.paidAt() != null
                    && o.paidAt().isAfter(now().minus(Duration.ofDays(365))))
        .map(o -> o.quote().total())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  Tier tier(BigDecimal spend) {
    if (spend.compareTo(money(6000000)) >= 0)
      return new Tier("Platinum", new BigDecimal("0.07"), null, null);
    if (spend.compareTo(money(3000000)) >= 0)
      return new Tier("Gold", new BigDecimal("0.05"), "Platinum", money(6000000));
    if (spend.compareTo(money(1500000)) >= 0)
      return new Tier("Silver", new BigDecimal("0.04"), "Gold", money(3000000));
    return new Tier("Member", new BigDecimal("0.03"), "Silver", money(1500000));
  }

  record Tier(String name, BigDecimal rate, String next, BigDecimal threshold) {}

  int balance(Member member) {
    return member.lots().stream().mapToInt(PointLot::remaining).sum() - member.debt();
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
}
