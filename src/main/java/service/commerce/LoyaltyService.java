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

  void awardPoints(CommerceState state, Order order) {
    int points = order.quote().pointsEarned();
    if (points == 0) return;
    Member member = member(state, order.customerId());
    Instant expiry = now().atZone(ZONE).plusMonths(12).toInstant();
    creditPoints(member, order.id(), points, expiry);
    member
        .entries()
        .add(
            new PointEntry(
                uuid(), "EARN", points, now(), "Tích điểm thanh toán thử", order.id(), expiry));
  }

  private void creditPoints(Member member, String origin, int points, Instant expiry) {
    int remaining = points;
    for (Map.Entry<String, Integer> debt : new ArrayList<>(member.pointDebts().entrySet())) {
      int paid = Math.min(debt.getValue(), remaining);
      if (paid > 0) {
        changeDebt(member, debt.getKey(), -paid);
        member.pointOffsets().add(new PointOffset(origin, debt.getKey(), paid, expiry));
        remaining -= paid;
      }
      if (remaining == 0) break;
    }
    if (remaining > 0) member.lots().add(new PointLot(uuid(), remaining, expiry, origin));
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

  void reversePoints(CommerceState state, Order order) {
    Member member = member(state, order.customerId());
    if (!member.gifts().add("points-reversed:" + order.id())) return;
    reverseAward(member, order);
    restoreSpending(member, order);
  }

  private void reverseAward(Member member, Order order) {
    int available = 0;
    for (int index = 0; index < member.lots().size(); index++) {
      PointLot lot = member.lots().get(index);
      if (Objects.equals(lot.sourceOrder(), order.id())) {
        available += lot.remaining();
        member.lots().set(index, lot.remaining(0));
      }
    }
    int spent =
        member.pointUses().entrySet().stream()
            .filter(entry -> !member.gifts().contains("points-restored:" + entry.getKey()))
            .flatMap(entry -> entry.getValue().stream())
            .filter(use -> Objects.equals(origin(member, use), order.id()))
            .mapToInt(PointUse::points)
            .sum();
    changeDebt(member, order.id(), spent);
    int offsets = 0;
    for (int index = 0; index < member.pointOffsets().size(); index++) {
      PointOffset offset = member.pointOffsets().get(index);
      if (offset.creditSourceOrder().equals(order.id()) && offset.points() > 0) {
        offsets += offset.points();
        changeDebt(member, offset.debtSourceOrder(), offset.points());
        member.pointOffsets().set(index, offset.points(0));
      }
    }
    if (order.quote().pointsEarned() > 0)
      member
          .entries()
          .add(
              new PointEntry(
                  uuid(),
                  "REVERSAL",
                  -available - spent - offsets,
                  now(),
                  "Đảo điểm đã tích và các khoản điểm bù nợ; điểm cần bù: " + member.debt(),
                  order.id(),
                  null));
  }

  private void restoreSpending(Member member, Order order) {
    if (!member.gifts().add("points-restored:" + order.id())) return;
    for (PointUse use : member.pointUses().getOrDefault(order.id(), List.of())) {
      Instant expiry =
          use.expiresAt().isAfter(now()) ? use.expiresAt() : now().plus(Duration.ofDays(30));
      String source = origin(member, use);
      if (member.gifts().contains("points-reversed:" + source))
        restoreReversedOrigin(member, source, use.points());
      else creditPoints(member, source, use.points(), expiry);
      member
          .entries()
          .add(
              new PointEntry(
                  uuid(),
                  "RESTORE",
                  use.points(),
                  now(),
                  "Trả điểm đã dùng theo nguồn cấp; hiệu lực tối thiểu 30 ngày khi lô cũ đã hết"
                      + " hạn",
                  order.id(),
                  expiry));
    }
  }

  private void restoreReversedOrigin(Member member, String origin, int points) {
    int owed = member.pointDebts().getOrDefault(origin, 0);
    int restored = Math.min(points, owed);
    changeDebt(member, origin, -restored);
    int remaining = points - restored;
    List<PointOffset> offsets = new ArrayList<>(member.pointOffsets());
    for (int index = 0; index < offsets.size() && remaining > 0; index++) {
      PointOffset offset = offsets.get(index);
      if (offset.debtSourceOrder().equals(origin) && offset.points() > 0) {
        int released = Math.min(remaining, offset.points());
        member.pointOffsets().set(index, offset.points(offset.points() - released));
        remaining -= released;
        Instant expiry =
            offset.expiresAt().isAfter(now())
                ? offset.expiresAt()
                : now().plus(Duration.ofDays(30));
        creditPoints(member, offset.creditSourceOrder(), released, expiry);
      }
    }
  }

  private String origin(Member member, PointUse use) {
    return member.lots().stream()
        .filter(lot -> lot.id().equals(use.lotId()))
        .findFirst()
        .orElseThrow(() -> error(500, "Không tìm thấy nguồn của điểm đã dùng."))
        .sourceOrder();
  }

  private void changeDebt(Member member, String origin, int adjustment) {
    int next = member.pointDebts().getOrDefault(origin, 0) + adjustment;
    if (next < 0) throw error(500, "Sổ điểm cần bù không hợp lệ.");
    if (next == 0) member.pointDebts().remove(origin);
    else member.pointDebts().put(origin, next);
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
