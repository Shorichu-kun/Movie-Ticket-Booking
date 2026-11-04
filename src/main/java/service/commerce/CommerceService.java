package service.commerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import handler.ApiException;
import java.math.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import model.*;
import model.commerce.*;
import model.commerce.CommerceRequests.*;
import model.commerce.CommerceState.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import repository.commerce.*;
import service.CustomerService;

@Service
public class CommerceService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private final CommerceRepository repository;
  private final CommerceCatalog catalog;
  private final Clock clock;
  private final boolean demo;
  private final PaymentMethodRegistry methods = new PaymentMethodRegistry();
  private final SeatInventoryService inventory;
  private final LoyaltyService loyalty;
  private final VoucherService vouchers;
  private final PricingService pricing;
  private final CommerceViews views;

  @Autowired
  public CommerceService(
      CustomerService customers,
      ObjectMapper mapper,
      @Value("${cinema.data-dir:data}") String directory,
      @Value("${commerce.demo-enabled:true}") boolean demo) {
    this(
        new JsonCommerceRepository(Path.of(directory, "commerce-state.json"), mapper),
        new ExistingCommerceCatalog(customers),
        Clock.systemUTC(),
        demo);
  }

  public CommerceService(
      CommerceRepository repository, CommerceCatalog catalog, Clock clock, boolean demo) {
    this.repository = repository;
    this.catalog = catalog;
    this.clock = clock;
    this.demo = demo;
    this.inventory = new SeatInventoryService(catalog, clock);
    this.loyalty = new LoyaltyService(clock);
    this.vouchers = new VoucherService(clock);
    this.pricing = new PricingService(catalog, clock, inventory, loyalty, vouchers);
    this.views = new CommerceViews(clock);
  }

  private <T> T tx(Function<CommerceState, T> operation) {
    return repository.transaction(
        s -> {
          expire(s);
          return operation.apply(s);
        });
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000)
  public void expirePendingTransactions() {
    repository.transaction(
        state -> {
          expire(state);
          return null;
        });
  }

  public Map<String, Object> catalog() {
    return map(
        "products",
        new ArrayList<>(pricing.products.values()),
        "methods",
        methods.views(),
        "offers",
        vouchers.campaigns.values().stream()
            .filter(c -> !c.code().equals("N6COMBO"))
            .map(
                c ->
                    map(
                        "code",
                        c.code(),
                        "title",
                        c.title(),
                        "description",
                        c.description(),
                        "minimumAmount",
                        c.minimumAmount(),
                        "discountAmount",
                        c.discountAmount(),
                        "percent",
                        c.percent(),
                        "maxDiscount",
                        c.maxDiscount(),
                        "expiresAt",
                        vouchers.campaignEnd()))
            .toList(),
        "rewards",
        List.of(
            map(
                "id",
                "COMBO60",
                "title",
                "Voucher combo 59.000đ",
                "points",
                60,
                "campaignCode",
                "N6COMBO")));
  }

  public Map<String, Object> seats(String showId, Customer customer) {
    return tx(
        s -> {
          Showtime show = inventory.show(showId, false);
          String owner = customer == null ? null : customer.getId();
          boolean open = inventory.bookable(show);
          List<Map<String, Object>> views = new ArrayList<>();
          for (Seat seat : catalog.seats(show.getRoomId())) {
            boolean mine =
                s.holds().values().stream()
                    .anyMatch(
                        h ->
                            h.active(now())
                                && Objects.equals(owner, h.customerId())
                                && h.showtimeId().equals(showId)
                                && h.seatIds().contains(seat.getId()));
            boolean occupied = inventory.occupied(s, showId, seat.getId(), null);
            String status =
                seat.getStatus() != SeatStatus.ACTIVE
                    ? "MAINTENANCE"
                    : occupied
                        ? (mine
                            ? "HELD_BY_ME"
                            : inventory.sold(s, showId, seat.getId()) ? "SOLD" : "HELD")
                        : "AVAILABLE";
            views.add(
                map(
                    "id",
                    seat.getId(),
                    "rowLabel",
                    seat.getRowLabel(),
                    "seatNumber",
                    seat.getSeatNumber(),
                    "type",
                    seat.getType(),
                    "status",
                    status,
                    "available",
                    open && seat.getStatus() == SeatStatus.ACTIVE && !occupied,
                    "heldByMe",
                    mine,
                    "price",
                    catalog.ticketPrice(show, seat, customer)));
          }
          return map(
              "showtime",
              show,
              "movie",
              catalog.movie(show.getMovieId()),
              "room",
              catalog.room(show.getRoomId()),
              "seats",
              views,
              "serverTime",
              now());
        });
  }

  public Map<String, Object> createHold(String owner, HoldRequest request) {
    requireOwner(owner);
    return tx(
        s -> {
          if (request == null) throw error(400, "Thiếu yêu cầu giữ ghế.");
          Showtime show = inventory.show(request.showtimeId(), true);
          SeatHold existing =
              s.holds().values().stream()
                  .filter(
                      h ->
                          h.active(now())
                              && h.customerId().equals(owner)
                              && h.showtimeId().equals(show.getId())
                              && request.seatIds() != null
                              && new HashSet<>(h.seatIds())
                                  .equals(new HashSet<>(request.seatIds())))
                  .findFirst()
                  .orElse(null);
          if (existing != null) return views.holdView(existing);
          List<String> seatIds = inventory.selection(s, show, request.seatIds(), null);
          SeatHold hold =
              new SeatHold(
                  uuid(),
                  owner,
                  show.getId(),
                  seatIds,
                  now(),
                  minimum(now().plusSeconds(420), inventory.start(show).minusSeconds(300)),
                  "ACTIVE");
          s.holds().put(hold.id(), hold);
          return views.holdView(hold);
        });
  }

  public Map<String, Object> hold(String owner, String id) {
    return tx(s -> views.holdView(ownedHold(s, owner, id)));
  }

  public Map<String, Object> updateHold(String owner, String id, List<String> seats) {
    return tx(
        s -> {
          SeatHold h = ownedHold(s, owner, id);
          requireActive(h);
          if (s.orders().values().stream().anyMatch(o -> o.holdId().equals(id)))
            throw error(409, "Không thể đổi ghế sau khi đã tạo đơn.");
          List<String> ids =
              inventory.selection(s, inventory.show(h.showtimeId(), true), seats, id);
          SeatHold updated = h.replace(ids);
          s.holds().put(id, updated);
          return views.holdView(updated);
        });
  }

  public Map<String, Object> releaseHold(String owner, String id) {
    return tx(
        s -> {
          SeatHold h = ownedHold(s, owner, id);
          Order order =
              s.orders().values().stream()
                  .filter(o -> o.holdId().equals(id) && o.status().equals("PENDING_PAYMENT"))
                  .findFirst()
                  .orElse(null);
          if (order != null) {
            ensureNoProcessing(s, order);
            cancelOrder(s, order);
          } else if (h.status().equals("ACTIVE")) s.holds().put(id, h.transition("RELEASED"));
          return views.holdView(s.holds().get(id));
        });
  }

  public PriceQuote quote(Customer customer, CheckoutRequest request) {
    requireCustomer(customer);
    return tx(
        s -> {
          SeatHold h = ownedHold(s, customer.getId(), request.holdId());
          requireActive(h);
          return pricing.price(s, customer, h, request).quote();
        });
  }

  public Map<String, Object> createOrder(Customer customer, CheckoutRequest request, String key) {
    requireCustomer(customer);
    if (request == null) throw error(400, "Thiếu yêu cầu tạo đơn.");
    return tx(
        s -> {
          String scope = requestKey(customer.getId(), "order", key);
          String fingerprint = request.toString();
          String previous = replay(s, scope, fingerprint);
          if (previous != null) return views.orderView(s, s.orders().get(previous));
          SeatHold h = ownedHold(s, customer.getId(), request.holdId());
          requireActive(h);
          if (s.orders().values().stream().anyMatch(o -> o.holdId().equals(request.holdId())))
            throw error(409, "Lượt giữ ghế đã có đơn.");
          inventory.selection(s, inventory.show(h.showtimeId(), true), h.seatIds(), h.id());
          validateContact(request.contact());
          PricingService.Pricing priced = pricing.price(s, customer, h, request);
          String method =
              priced.quote().total().signum() == 0 ? "LOYALTY" : normalizeMethod(request.method());
          if (method.equals("LOYALTY") && priced.quote().total().signum() > 0)
            throw error(400, "Điểm/ưu đãi chưa phủ hết đơn.");
          Instant end = inventory.deadline(s, h, method, customer.getId(), null);
          if (!end.equals(priced.quote().expiresAt())) priced = priced.deadline(end);
          h = h.deadline(end);
          s.holds().put(h.id(), h);
          Showtime show = inventory.show(h.showtimeId(), true);
          List<String> labels =
              h.seatIds().stream().map(id -> inventory.seat(show, id).getSeatLabel()).toList();
          Order o =
              new Order(
                  uuid(),
                  customer.getId(),
                  h,
                  catalog.movie(show.getMovieId()),
                  show,
                  catalog.room(show.getRoomId()),
                  labels,
                  priced.items(),
                  priced.quote(),
                  request.contact(),
                  method,
                  priced.voucherId(),
                  now());
          s.orders().put(o.id(), o);
          vouchers.reserveVoucher(s, o);
          s.requests().put(scope, new Idempotency(fingerprint, o.id()));
          if (method.equals("LOYALTY")) {
            PaymentAttempt a =
                new PaymentAttempt(uuid(), o.id(), method, "CREATED", BigDecimal.ZERO, end, false);
            s.attempts().put(a.id(), a);
            settle(s, o, a, false);
          } else if (method.equals("CASH")) {
            PaymentAttempt a =
                new PaymentAttempt(
                    uuid(), o.id(), method, "CREATED", o.quote().total(), end, false);
            s.attempts().put(a.id(), a);
          }
          return views.orderView(s, o);
        });
  }

  public List<Map<String, Object>> orders(String owner) {
    return tx(
        s ->
            s.orders().values().stream()
                .filter(o -> o.customerId().equals(owner))
                .map(o -> views.orderView(s, o))
                .toList());
  }

  public List<Map<String, Object>> adminOrders() {
    return tx(s -> s.orders().values().stream().map(o -> views.orderView(s, o)).toList());
  }

  public Map<String, Object> order(String owner, String id) {
    return tx(s -> views.orderView(s, ownedOrder(s, owner, id)));
  }

  public Map<String, Object> startPayment(String owner, String id, String methodCode, String key) {
    return tx(
        s -> {
          Order o = ownedOrder(s, owner, id);
          String method = normalizeMethod(methodCode);
          String scope = requestKey(owner, "pay:" + id, key);
          String previous = replay(s, scope, method);
          if (previous != null) return views.attemptView(s.attempts().get(previous));
          o.requirePending();
          ensureNoProcessing(s, o);
          Instant end = inventory.deadline(s, s.holds().get(o.holdId()), method, owner, o.id());
          if (method.equals("LOYALTY") && o.quote().total().signum() > 0)
            throw error(400, "Đơn chưa được phủ hết bằng điểm.");
          for (PaymentAttempt old : new ArrayList<>(s.attempts().values()))
            if (old.orderId().equals(id) && old.status().equals("CREATED"))
              s.attempts().put(old.id(), old.abandon("SWITCHED"));
          o.switchMethod(method, end, now());
          s.holds().put(o.holdId(), s.holds().get(o.holdId()).deadline(end));
          PaymentMethod.PaymentInitiation init =
              methods
                  .require(method)
                  .initiate(new PaymentMethod.PaymentRequest(id, o.quote().total(), end));
          PaymentAttempt attempt =
              new PaymentAttempt(
                  uuid(), id, method, init.status(), init.amount(), init.expiresAt(), false);
          s.attempts().put(attempt.id(), attempt);
          s.requests().put(scope, new Idempotency(method, attempt.id()));
          return views.attemptView(attempt);
        });
  }

  public Map<String, Object> confirmPayment(
      String owner, boolean admin, String attemptId, String scenario) {
    if (!demo) throw error(403, "Mô phỏng thanh toán đang tắt.");
    return tx(
        s -> {
          PaymentAttempt a = requireAttempt(s, attemptId);
          Order o = requireOrder(s, a.orderId());
          if (!admin && !Objects.equals(owner, o.customerId()))
            throw error(403, "Bạn không sở hữu giao dịch này.");
          if (a.method().equals("CASH"))
            throw error(403, "Thanh toán tại quầy cần admin xác nhận.");
          PaymentMethod.VerifiedPaymentResult result =
              methods
                  .require(a.method())
                  .verify(new PaymentMethod.PaymentEvent(scenario, a.amount()));
          if (a.status().equals("SUCCEEDED")) {
            if (result.status().equals("SUCCEEDED")) return views.orderView(s, o);
            throw error(409, "Giao dịch đã thành công.");
          }
          if (a.status().equals("CANCELLED")
              && result.status().equals("SUCCEEDED")
              && (result.late()
                  || a.cancellationReason() != null
                  || o.status().equals("CANCELLED"))) {
            refundAttempt(
                s,
                a.transition("SUCCEEDED"),
                "Khoản thu thử từ lần thanh toán đã hủy hoặc đổi phương thức");
            o.event(
                now(),
                "Đã tự động hoàn khoản thu thử của lần thanh toán đã hủy hoặc đổi phương thức.");
            return views.orderView(s, o);
          }
          if (Set.of("FAILED", "CANCELLED").contains(a.status())) {
            if (a.status().equals(result.status())) return views.orderView(s, o);
            throw error(409, "Không thể đổi kết quả giao dịch đã kết thúc.");
          }
          if (result.status().equals("SUCCEEDED")) settle(s, o, a, result.late());
          else if (a.status().equals("EXPIRED")) throw error(409, "Giao dịch đã hết hạn.");
          else {
            s.attempts().put(a.id(), a.transition(result.status()));
            o.event(now(), "Thanh toán thử: " + result.status());
          }
          return views.orderView(s, o);
        });
  }

  public Map<String, Object> confirmCounter(String id) {
    return tx(
        s -> {
          Order o = requireOrder(s, id);
          o.requirePending();
          if (!o.method().equals("CASH")) throw error(409, "Đơn không thanh toán tại quầy.");
          PaymentAttempt a =
              s.attempts().values().stream()
                  .filter(
                      p ->
                          p.orderId().equals(id)
                              && p.method().equals("CASH")
                              && p.status().equals("CREATED"))
                  .findFirst()
                  .orElseThrow(() -> error(409, "Không tìm thấy lần thanh toán tại quầy."));
          settle(s, o, a, false);
          return views.orderView(s, o);
        });
  }

  public Map<String, Object> cancel(String owner, String id) {
    return tx(
        s -> {
          Order o = ownedOrder(s, owner, id);
          ensureNoProcessing(s, o);
          cancelOrder(s, o);
          return views.orderView(s, o);
        });
  }

  public Map<String, Object> requestRefund(String owner, String id) {
    return tx(
        s -> {
          Order o = ownedOrder(s, owner, id);
          if (o.status().equals("REFUND_PENDING")) return views.orderView(s, o);
          inventory.refundEligible(o);
          o.requestRefund(now());
          return views.orderView(s, o);
        });
  }

  public Map<String, Object> approveRefund(String id, boolean approved) {
    return tx(
        s -> {
          Order o = requireOrder(s, id);
          if (o.status().equals("REFUNDED") && approved) return views.orderView(s, o);
          if (approved) inventory.refundEligible(o);
          o.refund(approved, now());
          if (approved) {
            vouchers.releaseVoucher(s, o, true);
            loyalty.reversePoints(s, o);
            invalidateUnfundedReservations(s, o.customerId());
            for (PaymentAttempt a : new ArrayList<>(s.attempts().values()))
              if (a.orderId().equals(id) && a.status().equals("SUCCEEDED"))
                refundAttempt(s, a, "Hoàn toàn bộ đơn được duyệt");
            notify(s, o.customerId(), "Hoàn tiền thử", "Đã hoàn toàn bộ đơn " + id, id);
          }
          return views.orderView(s, o);
        });
  }

  public Map<String, Object> checkIn(String token, boolean collectCombo) {
    if (token == null || token.isBlank()) throw error(400, "Thiếu token vé.");
    return tx(
        s -> {
          Order o =
              s.orders().values().stream()
                  .filter(v -> v.tickets().stream().anyMatch(t -> t.token().equals(token)))
                  .findFirst()
                  .orElseThrow(() -> error(404, "Token không hợp lệ."));
          if (now().isBefore(inventory.start(o.showtime()).minusSeconds(3600))
              || now().isAfter(inventory.end(o.showtime())))
            throw error(409, "Chỉ kiểm soát vé từ 60 phút trước suất đến khi kết thúc.");
          o.checkIn(token, collectCombo, now());
          return views.orderView(s, o);
        });
  }

  public Map<String, Object> ownedTicket(String ticketId, String owner, boolean admin) {
    return tx(
        s -> {
          Order o =
              s.orders().values().stream()
                  .filter(v -> v.tickets().stream().anyMatch(t -> t.id().equals(ticketId)))
                  .findFirst()
                  .orElseThrow(() -> error(404, "Không tìm thấy vé."));
          if (!admin && !o.customerId().equals(owner)) throw error(403, "Bạn không sở hữu vé.");
          Admission ticket =
              o.tickets().stream().filter(t -> t.id().equals(ticketId)).findFirst().orElseThrow();
          if (!o.status().equals("PAID") || !ticket.status().equals("ISSUED"))
            throw error(409, "Vé không còn hiệu lực.");
          return map(
              "id",
              ticket.id(),
              "token",
              ticket.token(),
              "seatLabel",
              ticket.seatLabel(),
              "status",
              ticket.status(),
              "orderId",
              o.id());
        });
  }

  public Map<String, Object> payment(String owner, boolean admin, String id) {
    return tx(
        state -> {
          PaymentAttempt attempt = requireAttempt(state, id);
          Order order = requireOrder(state, attempt.orderId());
          if (!admin && !Objects.equals(owner, order.customerId()))
            throw error(403, "Bạn không sở hữu giao dịch.");
          return views.attemptView(attempt);
        });
  }

  public boolean isCommerceSeatOccupied(String showtimeId, String seatId) {
    return tx(
        state ->
            state.holds().values().stream()
                    .anyMatch(
                        hold ->
                            hold.active(now())
                                && hold.showtimeId().equals(showtimeId)
                                && hold.seatIds().contains(seatId))
                || inventory.sold(state, showtimeId, seatId));
  }

  public boolean isSeatOccupied(String show, String seat) {
    return tx(s -> inventory.occupied(s, show, seat, null));
  }

  public boolean hasActiveShowtime(String id) {
    return tx(
        s ->
            s.holds().values().stream().anyMatch(h -> h.showtimeId().equals(id) && h.active(now()))
                || s.orders().values().stream()
                    .anyMatch(o -> o.showtime().getId().equals(id) && hasLiveAdmission(o)));
  }

  public boolean hasActiveSeat(String id) {
    return tx(
        s ->
            s.holds().values().stream().anyMatch(h -> h.seatIds().contains(id) && h.active(now()))
                || s.orders().values().stream()
                    .anyMatch(o -> o.seatIds().contains(id) && hasLiveAdmission(o)));
  }

  private boolean hasLiveAdmission(Order order) {
    return Set.of("PAID", "REFUND_PENDING").contains(order.status())
        && now().isBefore(inventory.end(order.showtime()))
        && order.tickets().stream().anyMatch(ticket -> ticket.status().equals("ISSUED"));
  }

  public Map<String, Object> me(String owner) {
    requireOwner(owner);
    return tx(s -> meView(s, owner));
  }

  public Map<String, Object> preferences(String owner, Preferences preferences) {
    requireOwner(owner);
    return tx(
        s -> {
          if (preferences == null) throw error(400, "Thiếu tùy chọn tài khoản.");
          Member member = member(s, owner);
          String birthday = preferences.birthDate();
          if (birthday != null && !birthday.isBlank()) {
            LocalDate date;
            try {
              date = LocalDate.parse(birthday);
            } catch (DateTimeException e) {
              throw error(400, "Ngày sinh không hợp lệ.");
            }
            if (date.isAfter(LocalDate.now(clock.withZone(ZONE)))
                || date.isBefore(LocalDate.now(clock.withZone(ZONE)).minusYears(120)))
              throw error(400, "Ngày sinh không hợp lệ.");
          } else birthday = null;
          if (member.preferences().birthDate() != null
              && !Objects.equals(member.preferences().birthDate(), birthday))
            throw error(409, "Ngày sinh đã xác nhận; liên hệ hỗ trợ để thay đổi.");
          String genre =
              preferences.favoriteGenre() == null ? "" : preferences.favoriteGenre().strip();
          if (genre.length() > 100) throw error(400, "Thể loại yêu thích quá dài.");
          Preferences safe =
              new Preferences(
                  birthday, genre, preferences.notifyShowtime(), preferences.notifyOffers());
          s.members().put(owner, member.preferences(safe));
          vouchers.birthdayGift(s, owner);
          return map(
              "birthDate",
              safe.birthDate(),
              "favoriteGenre",
              safe.favoriteGenre(),
              "notifyShowtime",
              safe.notifyShowtime(),
              "notifyOffers",
              safe.notifyOffers());
        });
  }

  public Map<String, Object> claimVoucher(String owner, String code) {
    requireOwner(owner);
    return tx(
        s -> {
          VoucherService.Campaign campaign = vouchers.campaign(code);
          if (campaign.code().equals("N6COMBO"))
            throw error(409, "Voucher này chỉ được tặng hoặc đổi điểm.");
          Member member = member(s, owner);
          if (!member.gifts().add("claim:" + campaign.code()))
            throw error(409, "Bạn đã nhận chương trình này.");
          vouchers.grantVoucher(member, campaign, vouchers.campaignEnd());
          notify(s, owner, "Voucher mới", campaign.title(), null);
          return meView(s, owner);
        });
  }

  public Map<String, Object> redeem(String owner, String rewardId, String key) {
    requireOwner(owner);
    return tx(
        s -> {
          String scope = requestKey(owner, "reward", key);
          String prior = replay(s, scope, rewardId);
          if (prior != null) return meView(s, owner);
          if (!"COMBO60".equals(rewardId)) throw error(404, "Không tìm thấy quà đổi điểm.");
          Member m = member(s, owner);
          if (loyalty.availablePoints(s, owner) < 60)
            throw error(409, "Bạn chưa đủ 60 điểm khả dụng.");
          String source = "reward:" + uuid();
          loyalty.consumePoints(s, owner, 60, source);
          vouchers.grantVoucher(
              m, vouchers.campaigns.get("N6COMBO"), now().plus(Duration.ofDays(90)));
          s.requests().put(scope, new Idempotency(rewardId, source));
          notify(s, owner, "Đổi quà thành công", "Đã đổi 60 điểm lấy voucher combo.", null);
          return meView(s, owner);
        });
  }

  private Map<String, Object> meView(CommerceState s, String owner) {
    Member m = member(s, owner);
    vouchers.birthdayGift(s, owner);
    BigDecimal spend = loyalty.yearlySpend(s, owner);
    LoyaltyService.Tier tier = loyalty.tier(spend);
    int points = loyalty.balance(m), reserved = loyalty.reservedPoints(s, owner);
    int expiring =
        m.lots().stream()
            .filter(l -> !l.expiresAt().isAfter(now().plus(Duration.ofDays(30))))
            .mapToInt(PointLot::remaining)
            .sum();
    return map(
        "membership",
        map(
            "tier",
            tier.name(),
            "points",
            points,
            "availablePoints",
            Math.max(0, points - reserved),
            "reservedPoints",
            reserved,
            "expiringPoints",
            expiring,
            "yearlySpend",
            spend,
            "nextTier",
            tier.next(),
            "nextThreshold",
            tier.threshold(),
            "remainingToNext",
            tier.threshold() == null
                ? BigDecimal.ZERO
                : tier.threshold().subtract(spend).max(BigDecimal.ZERO),
            "earnRate",
            tier.rate()),
        "vouchers",
        m.vouchers(),
        "pointEntries",
        m.entries(),
        "orders",
        s.orders().values().stream()
            .filter(o -> o.customerId().equals(owner))
            .map(o -> views.orderView(s, o))
            .toList(),
        "notifications",
        m.notifications(),
        "preferences",
        m.preferences());
  }

  private void settle(CommerceState s, Order o, PaymentAttempt a, boolean late) {
    if (a.status().equals("SUCCEEDED")) return;
    PaymentAttempt success = a.transition("SUCCEEDED");
    s.attempts().put(a.id(), success);
    if (late && o.status().equals("PENDING_PAYMENT")) {
      o.expire(now());
      releaseOrder(s, o, "EXPIRED");
    }
    if (o.status().equals("PENDING_PAYMENT")
        && o.quote().pointsUsed() > Math.max(0, loyalty.balance(member(s, o.customerId())))) {
      cancelOrder(s, o);
      o.event(now(), "Điểm đã giữ không còn đủ; hủy đơn và hoàn khoản thu thử.");
    }
    if (!o.status().equals("PENDING_PAYMENT") || !now().isBefore(o.expiresAt())) {
      refundAttempt(s, success, "Khoản thu thử đến muộn hoặc thu dư");
      o.event(now(), "Đã tự động hoàn khoản thu thử đến muộn hoặc thu dư; không phát vé.");
      notify(
          s,
          o.customerId(),
          "Hoàn khoản thu muộn",
          "Khoản thu thử đã được hoàn; không cấp lại ghế.",
          o.id());
      return;
    }
    List<Admission> admissions = new ArrayList<>();
    for (int i = 0; i < o.seatIds().size(); i++) {
      String label = inventory.seat(o.showtime(), o.seatIds().get(i)).getSeatLabel();
      admissions.add(new Admission(uuid(), label, uuid() + uuid(), "ISSUED"));
    }
    o.paid(now(), admissions);
    s.holds().put(o.holdId(), s.holds().get(o.holdId()).transition("CONSUMED"));
    vouchers.consumeVoucher(s, o);
    loyalty.consumePoints(s, o.customerId(), o.quote().pointsUsed(), o.id());
    loyalty.awardPoints(s, o);
    Member m = member(s, o.customerId());
    m.gifts().add("first-paid");
    String tier = loyalty.tier(loyalty.yearlySpend(s, o.customerId())).name();
    if (!tier.equals("Member")
        && m.gifts().add("tier:" + tier + ":" + LocalDate.now(clock.withZone(ZONE)).getYear()))
      vouchers.grantVoucher(m, vouchers.campaigns.get("N6COMBO"), now().plus(Duration.ofDays(90)));
    notify(
        s,
        o.customerId(),
        "Thanh toán thử thành công",
        "Vé của bạn đã sẵn sàng. Không gửi email/SMS thật.",
        o.id());
  }

  private void refundAttempt(CommerceState state, PaymentAttempt attempt, String reason) {
    state.attempts().put(attempt.id(), attempt.refund());
    if (attempt.amount().signum() > 0) {
      String id = "refund:" + attempt.id();
      state
          .refunds()
          .putIfAbsent(
              id,
              new Refund(
                  id,
                  attempt.orderId(),
                  attempt.id(),
                  attempt.amount(),
                  reason,
                  "SUCCEEDED",
                  now()));
    }
  }

  private void cancelOrder(CommerceState s, Order o) {
    o.cancel(now());
    releaseOrder(s, o, "RELEASED");
  }

  private void releaseOrder(CommerceState s, Order o, String holdStatus) {
    SeatHold h = s.holds().get(o.holdId());
    if (h != null) s.holds().put(h.id(), h.transition(holdStatus));
    vouchers.releaseVoucher(s, o, false);
    for (PaymentAttempt a : new ArrayList<>(s.attempts().values()))
      if (a.orderId().equals(o.id()) && Set.of("CREATED", "PROCESSING").contains(a.status()))
        s.attempts()
            .put(
                a.id(),
                holdStatus.equals("EXPIRED")
                    ? a.transition("EXPIRED")
                    : a.abandon("ORDER_" + holdStatus));
  }

  private void expire(CommerceState s) {
    Instant now = now();
    for (SeatHold h : new ArrayList<>(s.holds().values()))
      if (h.status().equals("ACTIVE") && !now.isBefore(h.expiresAt()))
        s.holds().put(h.id(), h.transition("EXPIRED"));
    for (Order o : s.orders().values())
      if (o.status().equals("PENDING_PAYMENT") && !now.isBefore(o.expiresAt())) {
        o.expire(now);
        releaseOrder(s, o, "EXPIRED");
      }
    for (Map.Entry<String, Member> entry : s.members().entrySet()) {
      Member m = entry.getValue();
      for (int i = 0; i < m.lots().size(); i++) {
        PointLot lot = m.lots().get(i);
        if (lot.remaining() > 0 && !now.isBefore(lot.expiresAt())) {
          m.entries()
              .add(
                  new PointEntry(
                      uuid(),
                      "EXPIRED",
                      -lot.remaining(),
                      now,
                      "Điểm đã hết hạn",
                      lot.sourceOrder(),
                      null));
          m.lots().set(i, lot.remaining(0));
        }
      }
      invalidateUnfundedReservations(s, entry.getKey());
      for (int i = 0; i < m.vouchers().size(); i++) {
        CustomerVoucher v = m.vouchers().get(i);
        if (v.status().equals("AVAILABLE") && !now.isBefore(v.expiresAt()))
          m.vouchers().set(i, v.status("EXPIRED", null));
      }
    }
  }

  private void invalidateUnfundedReservations(CommerceState state, String owner) {
    int budget = Math.max(0, loyalty.balance(member(state, owner)));
    for (Order order : state.orders().values()) {
      if (!order.customerId().equals(owner) || !order.status().equals("PENDING_PAYMENT")) continue;
      int reserved = order.quote().pointsUsed();
      if (reserved <= budget) {
        budget -= reserved;
        continue;
      }
      cancelOrder(state, order);
      order.event(now(), "Điểm đang giữ bị đảo hoặc hết hạn; đơn đã hủy để bảo toàn quyền lợi.");
      notify(
          state,
          owner,
          "Đơn đã hủy do điểm thay đổi",
          "Điểm giữ cho đơn không còn đủ. Khoản thu thử đến muộn sẽ được hoàn tự động.",
          order.id());
    }
  }

  private void ensureNoProcessing(CommerceState s, Order o) {
    if (s.attempts().values().stream()
        .anyMatch(a -> a.orderId().equals(o.id()) && a.status().equals("PROCESSING")))
      throw error(409, "Đang xác minh giao dịch; chờ kết quả trước khi thử lại hoặc hủy.");
  }

  private SeatHold ownedHold(CommerceState s, String owner, String id) {
    requireOwner(owner);
    SeatHold h = s.holds().get(id);
    if (h == null) throw error(404, "Không tìm thấy lượt giữ ghế.");
    if (!h.customerId().equals(owner)) throw error(403, "Bạn không sở hữu lượt giữ ghế.");
    return h;
  }

  private void requireActive(SeatHold hold) {
    if (!hold.active(now())) throw error(409, "Lượt giữ ghế đã hết hạn hoặc được dùng.");
  }

  private Order ownedOrder(CommerceState s, String owner, String id) {
    requireOwner(owner);
    Order o = requireOrder(s, id);
    if (!o.customerId().equals(owner)) throw error(403, "Bạn không sở hữu đơn này.");
    return o;
  }

  private Order requireOrder(CommerceState s, String id) {
    Order o = s.orders().get(id);
    if (o == null) throw error(404, "Không tìm thấy đơn.");
    return o;
  }

  private PaymentAttempt requireAttempt(CommerceState s, String id) {
    PaymentAttempt a = s.attempts().get(id);
    if (a == null) throw error(404, "Không tìm thấy lần thanh toán.");
    return a;
  }

  private Member member(CommerceState s, String owner) {
    return s.members().computeIfAbsent(owner, k -> new Member());
  }

  private void notify(CommerceState s, String owner, String title, String message, String order) {
    member(s, owner)
        .notifications()
        .add(new Notification(uuid(), title, message, now(), order, false));
  }

  private String replay(CommerceState s, String scope, String fingerprint) {
    Idempotency prior = s.requests().get(scope);
    if (prior == null) return null;
    if (!prior.fingerprint().equals(fingerprint))
      throw error(409, "Idempotency-Key đã được dùng với nội dung khác.");
    return prior.resultId();
  }

  private String requestKey(String owner, String operation, String key) {
    if (key == null || key.isBlank() || key.length() > 128)
      throw error(400, "Cần Idempotency-Key từ 1 đến 128 ký tự.");
    return owner + ":" + operation + ":" + key;
  }

  private void validateContact(Contact c) {
    if (c == null
        || c.name() == null
        || c.name().isBlank()
        || c.name().length() > 100
        || c.email() == null
        || !c.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || c.email().length() > 200
        || c.phone() == null
        || !c.phone().matches("[+0-9 ()-]{8,20}"))
      throw error(400, "Thông tin liên hệ không hợp lệ.");
  }

  private void requireCustomer(Customer c) {
    if (c == null) throw error(403, "Chỉ khách hàng có thể đặt vé.");
  }

  private void requireOwner(String owner) {
    if (owner == null || owner.isBlank())
      throw error(403, "Chỉ khách hàng có thể sử dụng chức năng này.");
  }

  private String normalizeMethod(String method) {
    String code = method == null || method.isBlank() ? "MOMO" : method.toUpperCase(Locale.ROOT);
    methods.require(code);
    return code;
  }

  private Instant now() {
    return clock.instant();
  }

  private Instant minimum(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
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

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
    return result;
  }
}
