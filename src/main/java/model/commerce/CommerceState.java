package model.commerce;

import com.fasterxml.jackson.annotation.*;
import handler.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import model.*;
import model.commerce.CommerceRequests.*;

public record CommerceState(
    Map<String, SeatHold> holds,
    Map<String, Order> orders,
    Map<String, PaymentAttempt> attempts,
    Map<String, Member> members,
    Map<String, Idempotency> requests,
    Map<String, Refund> refunds) {
  public CommerceState {
    if (refunds == null) refunds = new LinkedHashMap<>();
  }

  public CommerceState() {
    this(
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>());
  }

  public record Refund(
      String id,
      String orderId,
      String attemptId,
      BigDecimal amount,
      String reason,
      String status,
      Instant createdAt) {}

  public record Idempotency(String fingerprint, String resultId) {}

  public record PriceQuote(
      BigDecimal subtotal,
      BigDecimal ticketSubtotal,
      BigDecimal comboSubtotal,
      BigDecimal discount,
      String discountLabel,
      int pointsUsed,
      BigDecimal pointsValue,
      BigDecimal total,
      int pointsEarned,
      Instant expiresAt,
      BigDecimal earnRate,
      String policyVersion) {}

  public record Event(Instant at, String message) {}

  public record Admission(String id, String seatLabel, String token, String status) {
    public Admission use() {
      if (!status.equals("ISSUED")) throw new ApiException(409, "Vé đã sử dụng hoặc vô hiệu.");
      return new Admission(id, seatLabel, token, "USED");
    }

    public Admission voidTicket() {
      return new Admission(id, seatLabel, token, "VOID");
    }
  }

  public record SeatHold(
      String id,
      String customerId,
      String showtimeId,
      List<String> seatIds,
      Instant createdAt,
      Instant expiresAt,
      String status) {
    public SeatHold {
      seatIds = List.copyOf(seatIds);
    }

    public boolean active(Instant now) {
      return status.equals("ACTIVE") && now.isBefore(expiresAt);
    }

    public SeatHold replace(List<String> seats) {
      if (!status.equals("ACTIVE"))
        throw new ApiException(409, "Lượt giữ ghế không còn hoạt động.");
      return new SeatHold(id, customerId, showtimeId, seats, createdAt, expiresAt, status);
    }

    public SeatHold transition(String next) {
      return new SeatHold(id, customerId, showtimeId, seatIds, createdAt, expiresAt, next);
    }

    public SeatHold deadline(Instant end) {
      return new SeatHold(id, customerId, showtimeId, seatIds, createdAt, end, status);
    }
  }

  @JsonAutoDetect(
      fieldVisibility = JsonAutoDetect.Visibility.ANY,
      getterVisibility = JsonAutoDetect.Visibility.NONE)
  public static final class Order {
    private String id, code, customerId, holdId, status, method, voucherId;
    private Instant createdAt, expiresAt, paidAt;
    private Movie movie;
    private Showtime showtime;
    private CinemaRoom room;
    private List<String> seatIds, seatLabels;
    private List<OrderItem> items;
    private PriceQuote quote;
    private Contact contact;
    private List<Admission> tickets = new ArrayList<>();
    private List<Event> events = new ArrayList<>();
    private boolean comboCollected;

    private Order() {}

    public Order(
        String id,
        String customerId,
        SeatHold hold,
        Movie movie,
        Showtime showtime,
        CinemaRoom room,
        List<String> labels,
        List<OrderItem> items,
        PriceQuote quote,
        Contact contact,
        String method,
        String voucherId,
        Instant now) {
      this.id = id;
      this.code = "N6-" + id.substring(0, 8).toUpperCase(Locale.ROOT);
      this.customerId = customerId;
      this.holdId = hold.id();
      this.createdAt = now;
      this.expiresAt = quote.expiresAt();
      this.status = "PENDING_PAYMENT";
      this.movie = movie;
      this.showtime = showtime;
      this.room = room;
      this.seatIds = hold.seatIds();
      this.seatLabels = List.copyOf(labels);
      this.items = List.copyOf(items);
      this.quote = quote;
      this.contact = contact;
      this.method = method;
      this.voucherId = voucherId;
      event(now, "Đã tạo đơn mô phỏng — không thu tiền thật.");
    }

    public List<OrderItem> items() {
      return List.copyOf(items);
    }

    public String id() {
      return id;
    }

    public String customerId() {
      return customerId;
    }

    public String holdId() {
      return holdId;
    }

    public String status() {
      return status;
    }

    public String method() {
      return method;
    }

    public String voucherId() {
      return voucherId;
    }

    public PriceQuote quote() {
      return quote;
    }

    public Instant expiresAt() {
      return expiresAt;
    }

    public Instant paidAt() {
      return paidAt;
    }

    public Showtime showtime() {
      return showtime;
    }

    public List<String> seatIds() {
      return seatIds;
    }

    public List<Admission> tickets() {
      return List.copyOf(tickets);
    }

    public boolean comboCollected() {
      return comboCollected;
    }

    public void requirePending() {
      if (!status.equals("PENDING_PAYMENT"))
        throw new ApiException(409, "Đơn không còn chờ thanh toán.");
    }

    public void event(Instant at, String message) {
      events.add(new Event(at, message));
    }

    public void expire(Instant now) {
      requirePending();
      status = "EXPIRED";
      event(now, "Đơn hết hạn; đã nhả ghế và quyền lợi.");
    }

    public void cancel(Instant now) {
      requirePending();
      status = "CANCELLED";
      event(now, "Đã hủy đơn và nhả quyền lợi.");
    }

    public void paid(Instant now, List<Admission> admissions) {
      requirePending();
      if (!now.isBefore(expiresAt)) throw new ApiException(409, "Đơn đã hết hạn.");
      status = "PAID";
      paidAt = now;
      tickets = new ArrayList<>(admissions);
      event(now, "Thanh toán thử thành công; đã phát vé.");
    }

    public void switchMethod(String method, Instant deadline, Instant now) {
      requirePending();
      this.method = method;
      this.expiresAt = deadline;
      this.quote =
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
              deadline,
              quote.earnRate(),
              quote.policyVersion());
      event(now, "Đổi phương thức thử: " + method);
    }

    public void requestRefund(Instant now) {
      if (!status.equals("PAID")) throw new ApiException(409, "Chỉ hoàn đơn đã thanh toán.");
      status = "REFUND_PENDING";
      event(now, "Đã yêu cầu hoàn toàn bộ đơn.");
    }

    public void refund(boolean approved, Instant now) {
      if (!status.equals("REFUND_PENDING")) throw new ApiException(409, "Đơn không chờ hoàn.");
      status = approved ? "REFUNDED" : "PAID";
      if (approved) tickets = tickets.stream().map(Admission::voidTicket).toList();
      event(now, approved ? "Đã hoàn tiền thử và đảo quyền lợi." : "Yêu cầu hoàn bị từ chối.");
    }

    public void checkIn(String token, boolean collectCombo, Instant now) {
      if (!status.equals("PAID"))
        throw new ApiException(409, "Đơn chưa thanh toán hoặc đang hoàn.");
      List<Admission> updated = new ArrayList<>();
      boolean found = false;
      for (Admission t : tickets) {
        if (t.token().equals(token)) {
          updated.add(t.use());
          found = true;
        } else updated.add(t);
      }
      if (!found) throw new ApiException(404, "Không tìm thấy vé.");
      if (collectCombo && comboCollected) throw new ApiException(409, "Combo đã được nhận.");
      tickets = updated;
      if (collectCombo) comboCollected = true;
      event(now, "Đã kiểm soát vé" + (collectCombo ? " và giao combo." : "."));
    }
  }

  public record PaymentAttempt(
      String id,
      String orderId,
      String method,
      String status,
      BigDecimal amount,
      Instant expiresAt,
      boolean refunded) {
    public PaymentAttempt transition(String next) {
      return new PaymentAttempt(id, orderId, method, next, amount, expiresAt, refunded);
    }

    public PaymentAttempt refund() {
      return new PaymentAttempt(id, orderId, method, status, amount, expiresAt, true);
    }
  }

  public record PointEntry(
      String id,
      String type,
      int points,
      Instant createdAt,
      String description,
      String orderId,
      Instant expiresAt) {}

  public record PointLot(String id, int remaining, Instant expiresAt, String sourceOrder) {
    public PointLot remaining(int value) {
      return new PointLot(id, value, expiresAt, sourceOrder);
    }
  }

  public record PointUse(String lotId, int points, Instant expiresAt) {}

  public record CustomerVoucher(
      String id, String code, String title, String status, Instant expiresAt, String orderId) {
    public CustomerVoucher status(String value, String order) {
      return new CustomerVoucher(id, code, title, value, expiresAt, order);
    }
  }

  public record Notification(
      String id, String title, String message, Instant createdAt, String orderId, boolean read) {}

  public record Member(
      List<PointLot> lots,
      List<PointEntry> entries,
      List<CustomerVoucher> vouchers,
      List<Notification> notifications,
      Map<String, List<PointUse>> pointUses,
      Preferences preferences,
      Set<String> gifts,
      int debt) {
    public Member() {
      this(
          new ArrayList<>(),
          new ArrayList<>(),
          new ArrayList<>(),
          new ArrayList<>(),
          new LinkedHashMap<>(),
          new Preferences(null, "", true, true),
          new HashSet<>(),
          0);
    }

    public Member preferences(Preferences p) {
      return new Member(lots, entries, vouchers, notifications, pointUses, p, gifts, debt);
    }

    public Member debt(int d) {
      return new Member(lots, entries, vouchers, notifications, pointUses, preferences, gifts, d);
    }
  }
}
