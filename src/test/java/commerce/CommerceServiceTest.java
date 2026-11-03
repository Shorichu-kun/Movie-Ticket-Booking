package commerce;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import handler.ApiException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import model.*;
import model.commerce.CommerceRequests.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import repository.commerce.JsonCommerceRepository;
import service.commerce.*;

class CommerceServiceTest {
  @TempDir Path directory;
  MutableClock clock;
  CommerceService service;
  TestCatalog catalog;
  Customer customer =
      new Customer("c1", "Customer", "0901234567", "c@example.test", CustomerType.NORMAL, null);

  @BeforeEach
  void setup() {
    clock = new MutableClock();
    catalog = new TestCatalog();
    service = createService();
  }

  CommerceService createService() {
    return new CommerceService(
        new JsonCommerceRepository(
            directory.resolve("commerce.json"), new ObjectMapper().findAndRegisterModules()),
        catalog,
        clock,
        true);
  }

  String hold(String owner, String seat) {
    return (String) service.createHold(owner, new HoldRequest("show", List.of(seat))).get("id");
  }

  CheckoutRequest request(String hold, String voucher, int points, String method) {
    return new CheckoutRequest(
        hold,
        List.of(),
        voucher,
        points,
        method,
        new Contact("Customer", "c@example.test", "0901234567"));
  }

  Map<String, Object> order(String seat, String key) {
    return service.createOrder(customer, request(hold("c1", seat), null, 0, "MOMO"), key);
  }

  String id(Map<String, Object> value) {
    return (String) value.get("id");
  }

  Map<String, Object> attempt(String order, String key) {
    return service.startPayment("c1", order, "MOMO", key);
  }

  @Test
  void concurrentHoldsAreExclusiveAndAtomic() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Boolean>> results = new ArrayList<>();
      for (String c : List.of("c1", "c2"))
        results.add(
            pool.submit(
                () -> {
                  go.await();
                  try {
                    hold(c, "s1");
                    return true;
                  } catch (ApiException e) {
                    return false;
                  }
                }));
      go.countDown();
      assertEquals(
          1,
          results.stream()
              .filter(
                  f -> {
                    try {
                      return f.get();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .count());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void expiredHoldCannotBeRevivedAndSeatIsReleased() {
    String h = hold("c1", "s1");
    clock.advance(Duration.ofMinutes(8));
    assertThrows(
        ApiException.class,
        () -> service.createOrder(customer, request(h, null, 0, "CASH"), "late"));
    assertNotNull(hold("c2", "s1"));
  }

  @Test
  void orderIdempotencySurvivesRestartAndRejectsChangedPayload() {
    String h = hold("c1", "s1");
    CheckoutRequest req = request(h, null, 0, "MOMO");
    Map<String, Object> first = service.createOrder(customer, req, "same");
    service = createService();
    assertEquals(id(first), id(service.createOrder(customer, req, "same")));
    assertThrows(
        ApiException.class,
        () -> service.createOrder(customer, request(h, null, 0, "CARD"), "same"));
  }

  @Test
  void failedPaymentCanRetryButDuplicateSuccessIssuesBenefitsOnce() {
    String o = id(order("s1", "one"));
    String a = id(attempt(o, "a"));
    assertEquals("PENDING_PAYMENT", service.confirmPayment("c1", false, a, "FAILED").get("status"));
    String b = id(attempt(o, "b"));
    Map<String, Object> paid = service.confirmPayment("c1", false, b, "SUCCESS");
    assertEquals("PAID", paid.get("status"));
    assertEquals(1, ((List<?>) paid.get("tickets")).size());
    service.confirmPayment("c1", false, b, "SUCCESS");
    Map<?, ?> member = (Map<?, ?>) service.me("c1").get("membership");
    assertEquals(3, member.get("points"));
    assertThrows(ApiException.class, () -> service.confirmPayment("c1", false, a, "SUCCESS"));
  }

  @Test
  void lateSuccessRefundsWithoutTakingReleasedSeat() {
    String o = id(order("s1", "one"));
    String a = id(attempt(o, "a"));
    clock.advance(Duration.ofMinutes(8));
    hold("c2", "s1");
    Map<String, Object> late = service.confirmPayment("c1", false, a, "SUCCESS");
    assertEquals("EXPIRED", late.get("status"));
    assertTrue(((List<?>) late.get("tickets")).isEmpty());
    assertTrue(((List<?>) late.get("events")).toString().contains("hoàn"));
  }

  @Test
  void voucherReservationReleasesOnCancelAndRefundRestoresBenefits() {
    service.claimVoucher("c1", "N6WEEKDAY10");
    String o =
        id(
            service.createOrder(
                customer, request(hold("c1", "s1"), "N6WEEKDAY10", 0, "MOMO"), "one"));
    assertThrows(
        ApiException.class,
        () ->
            service.createOrder(
                customer, request(hold("c1", "s2"), "N6WEEKDAY10", 0, "MOMO"), "two"));
    service.cancel("c1", o);
    assertTrue(service.me("c1").get("vouchers").toString().contains("AVAILABLE"));
  }

  @Test
  void fullRefundVoidsTicketsAndReversesEarnedPoints() {
    String o = id(order("s1", "one"));
    String a = id(attempt(o, "a"));
    service.confirmPayment("c1", false, a, "SUCCESS");
    assertEquals("REFUND_PENDING", service.requestRefund("c1", o).get("status"));
    Map<String, Object> refunded = service.approveRefund(o, true);
    assertEquals("REFUNDED", refunded.get("status"));
    assertEquals(0, ((Map<?, ?>) service.me("c1").get("membership")).get("points"));
    assertFalse(service.isSeatOccupied("show", "s1"));
  }

  @Test
  void processingPaymentCannotOpenAnotherAttemptOrCancel() {
    String o = id(order("s1", "one"));
    String a = id(attempt(o, "a"));
    service.confirmPayment("c1", false, a, "PROCESSING");
    assertThrows(ApiException.class, () -> attempt(o, "b"));
    assertThrows(ApiException.class, () -> service.cancel("c1", o));
    assertEquals("PAID", service.confirmPayment("c1", false, a, "SUCCESS").get("status"));
  }

  @Test
  void reopeningSameSeatSelectionReturnsSameHoldWithoutExtendingDeadline() {
    String original = hold("c1", "s1");
    Object expires = service.hold("c1", original).get("expiresAt");
    clock.advance(Duration.ofMinutes(1));
    assertEquals(original, hold("c1", "s1"));
    assertEquals(expires, service.hold("c1", original).get("expiresAt"));
  }

  @Test
  void orderTimesAreIsoStringsForBrowserParsing() {
    Map<String, Object> order = order("s1", "one");
    assertInstanceOf(String.class, order.get("createdAt"));
    assertDoesNotThrow(() -> Instant.parse((String) order.get("createdAt")));
  }

  @Test
  void methodDeadlinesUseOriginalHoldTime() {
    String o = id(order("s1", "one"));
    clock.advance(Duration.ofMinutes(6));
    service.startPayment("c1", o, "CASH", "cash");
    clock.advance(Duration.ofMinutes(2));
    assertThrows(ApiException.class, () -> service.startPayment("c1", o, "MOMO", "online"));
    assertEquals("CASH", service.order("c1", o).get("method"));
    assertEquals("PAID", service.confirmCounter(o).get("status"));
  }

  @Test
  void pointsAreReservedAcrossTabsReleasedOnCancelAndZeroPayDoesNotEarn() {
    catalog.price = 1000000;
    String earned = id(order("s1", "earn"));
    service.confirmPayment("c1", false, id(attempt(earned, "pay")), "SUCCESS");
    catalog.price = 10000;
    String h = hold("c1", "s2");
    Map<String, Object> free = service.createOrder(customer, request(h, null, 10, "MOMO"), "free");
    assertEquals("LOYALTY", free.get("method"));
    assertEquals("PAID", free.get("status"));
    assertEquals(20, ((Map<?, ?>) service.me("c1").get("membership")).get("availablePoints"));
    service.requestRefund("c1", id(free));
    service.approveRefund(id(free), true);
    assertEquals(30, ((Map<?, ?>) service.me("c1").get("membership")).get("availablePoints"));
    catalog.price = 100000;
    Map<String, Object> reserved =
        service.createOrder(customer, request(hold("c1", "s2"), null, 20, "MOMO"), "reserve");
    assertEquals(10, ((Map<?, ?>) service.me("c1").get("membership")).get("availablePoints"));
    assertThrows(
        ApiException.class,
        () -> service.createOrder(customer, request(hold("c1", "s3"), null, 20, "MOMO"), "other"));
    service.cancel("c1", id(reserved));
    assertEquals(30, ((Map<?, ?>) service.me("c1").get("membership")).get("availablePoints"));
  }

  @Test
  void ownershipIsCheckedForHoldOrderAttemptAndTickets() {
    String h = hold("c1", "s1");
    assertThrows(ApiException.class, () -> service.hold("c2", h));
    String o = id(service.createOrder(customer, request(h, null, 0, "MOMO"), "one"));
    assertThrows(ApiException.class, () -> service.order("c2", o));
    String a = id(attempt(o, "pay"));
    assertThrows(ApiException.class, () -> service.confirmPayment("c2", false, a, "SUCCESS"));
    Map<String, Object> paid = service.confirmPayment("c1", false, a, "SUCCESS");
    String ticket = (String) ((Map<?, ?>) ((List<?>) paid.get("tickets")).get(0)).get("id");
    assertThrows(ApiException.class, () -> service.ownedTicket(ticket, "c2", false));
  }

  @Test
  void birthdayIsLockedAndAnnualGiftCannotBeClaimedTwice() {
    service.preferences("c1", new Preferences("2000-11-01", "Action", true, true));
    assertEquals(1, ((List<?>) service.me("c1").get("vouchers")).size());
    service.preferences("c1", new Preferences("2000-11-01", "Comedy", false, true));
    assertEquals(1, ((List<?>) service.me("c1").get("vouchers")).size());
    assertThrows(
        ApiException.class,
        () -> service.preferences("c1", new Preferences("2000-12-01", "", true, true)));
  }

  @Test
  void ticketCanOnlyBeCheckedInOnceAndUsedTicketCannotRefund() {
    String o = id(order("s1", "one"));
    Map<String, Object> paid =
        service.confirmPayment("c1", false, id(attempt(o, "pay")), "SUCCESS");
    String token = (String) ((Map<?, ?>) ((List<?>) paid.get("tickets")).get(0)).get("token");
    clock.advance(Duration.ofHours(34));
    assertEquals("PAID", service.checkIn(token, false).get("status"));
    assertThrows(ApiException.class, () -> service.checkIn(token, false));
    assertThrows(ApiException.class, () -> service.requestRefund("c1", o));
  }

  @Test
  void priceAndCatalogSnapshotsSurviveLaterPriceChanges() {
    Map<String, Object> first = order("s1", "one");
    catalog.price = 999999;
    assertEquals(
        ((Map<?, ?>) first.get("quote")).get("total"),
        ((Map<?, ?>) service.order("c1", id(first)).get("quote")).get("total"));
  }

  @Test
  void disabledSimulationRejectsCustomerAndAdminCallbacks() {
    String o = id(order("s1", "one"));
    String a = id(attempt(o, "pay"));
    CommerceService disabled =
        new CommerceService(
            new JsonCommerceRepository(
                directory.resolve("commerce.json"), new ObjectMapper().findAndRegisterModules()),
            catalog,
            clock,
            false);
    assertThrows(ApiException.class, () -> disabled.confirmPayment("c1", false, a, "SUCCESS"));
    assertThrows(ApiException.class, () -> disabled.confirmPayment(null, true, a, "SUCCESS"));
  }

  @Test
  void rewardRedemptionIsAtomicAndIdempotent() {
    catalog.price = 1000000;
    for (String seat : List.of("s1", "s2")) {
      String o = id(order(seat, seat));
      service.confirmPayment("c1", false, id(attempt(o, "pay")), "SUCCESS");
    }
    int before = ((List<?>) service.me("c1").get("vouchers")).size();
    Map<String, Object> redeemed = service.redeem("c1", "COMBO60", "redeem");
    assertEquals(0, ((Map<?, ?>) redeemed.get("membership")).get("availablePoints"));
    assertEquals(
        before + 1, ((List<?>) service.redeem("c1", "COMBO60", "redeem").get("vouchers")).size());
    assertThrows(ApiException.class, () -> service.redeem("c1", "COMBO60", "new"));
  }

  @Test
  void expiredEarnedPointsDoNotCreateDebtWhenOrderIsRefunded() {
    catalog.startTime = "2028-11-04T20:00:00";
    catalog.price = 1000000;
    String o = id(order("s1", "one"));
    service.confirmPayment("c1", false, id(attempt(o, "pay")), "SUCCESS");
    clock.advance(Duration.ofDays(370));
    assertEquals(0, ((Map<?, ?>) service.me("c1").get("membership")).get("availablePoints"));
    service.requestRefund("c1", o);
    service.approveRefund(o, true);
    assertEquals(0, ((Map<?, ?>) service.me("c1").get("membership")).get("points"));
  }

  @Test
  void refundAfterRewardPointsWereSpentRecordsDebtAndRetainsHistory() {
    catalog.price = 1000000;
    String original = id(order("s1", "one"));
    service.confirmPayment("c1", false, id(attempt(original, "pay")), "SUCCESS");
    catalog.price = 100000;
    String second =
        id(service.createOrder(customer, request(hold("c1", "s2"), null, 20, "MOMO"), "spend"));
    service.confirmPayment("c1", false, id(attempt(second, "pay")), "SUCCESS");
    service.requestRefund("c1", original);
    service.approveRefund(original, true);
    Map<String, Object> me = service.me("c1");
    assertEquals(-18, ((Map<?, ?>) me.get("membership")).get("points"));
    assertTrue(me.get("pointEntries").toString().contains("REVERSAL"));
  }

  @Test
  void paymentAttemptLookupChecksOwnership() {
    String order = id(order("s1", "one"));
    String payment = id(attempt(order, "pay"));
    assertEquals(order, service.payment("c1", false, payment).get("orderId"));
    assertThrows(ApiException.class, () -> service.payment("c2", false, payment));
  }

  @Test
  void catalogGuardsIgnoreHistoricalOrdersAndLegacySeatBridgeExcludesLegacy() {
    String order = id(order("s1", "one"));
    service.confirmPayment("c1", false, id(attempt(order, "pay")), "SUCCESS");
    assertTrue(service.hasActiveShowtime("show"));
    clock.advance(Duration.ofDays(3));
    assertFalse(service.hasActiveShowtime("show"));
    assertFalse(service.hasActiveSeat("s1"));
  }

  @Test
  void aChargeArrivingAfterCustomerCancellationIsRefundedWithoutTickets() {
    String order = id(order("s1", "one"));
    String attempt = id(attempt(order, "pay"));
    service.cancel("c1", order);
    Map<String, Object> cancelled = service.confirmPayment("c1", false, attempt, "SUCCESS");
    assertEquals("CANCELLED", cancelled.get("status"));
    assertTrue(((List<?>) cancelled.get("tickets")).isEmpty());
    assertEquals(Boolean.TRUE, service.payment("c1", false, attempt).get("refunded"));
  }

  @Test
  void simultaneousCheckInAndRefundCannotLeaveTicketBothUsedAndRefunded() throws Exception {
    String order = id(order("s1", "one"));
    Map<String, Object> paid =
        service.confirmPayment("c1", false, id(attempt(order, "pay")), "SUCCESS");
    String token = (String) ((Map<?, ?>) ((List<?>) paid.get("tickets")).get(0)).get("token");
    clock.advance(Duration.ofHours(34));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    try {
      Future<Boolean> checkIn =
          pool.submit(
              () -> {
                go.await();
                try {
                  service.checkIn(token, false);
                  return true;
                } catch (ApiException e) {
                  return false;
                }
              });
      Future<Boolean> refund =
          pool.submit(
              () -> {
                go.await();
                try {
                  service.requestRefund("c1", order);
                  return true;
                } catch (ApiException e) {
                  return false;
                }
              });
      go.countDown();
      assertTrue(checkIn.get());
      assertFalse(refund.get());
      assertEquals("PAID", service.order("c1", order).get("status"));
    } finally {
      pool.shutdownNow();
    }
  }

  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-11-03T02:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    public ZoneId getZone() {
      return ZoneId.of("Asia/Ho_Chi_Minh");
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  static class TestCatalog implements CommerceCatalog {
    double price = 100000;
    String startTime = "2026-11-04T20:00:00";

    public Showtime showtime(String id) {
      return "show".equals(id)
          ? new Showtime(
              id,
              "movie",
              "room",
              startTime,
              LocalDateTime.parse(startTime).plusHours(2).toString(),
              price)
          : null;
    }

    public Movie movie(String id) {
      Movie m = new Movie();
      m.setId(id);
      m.setTitle("Movie");
      m.setStatus(MovieStatus.NOW_SHOWING);
      return m;
    }

    public CinemaRoom room(String id) {
      return new CinemaRoom();
    }

    public List<Seat> seats(String room) {
      return List.of(
          new Seat("s1", room, "A", 1, SeatType.NORMAL, SeatStatus.ACTIVE),
          new Seat("s2", room, "A", 2, SeatType.NORMAL, SeatStatus.ACTIVE),
          new Seat("s3", room, "A", 3, SeatType.NORMAL, SeatStatus.ACTIVE));
    }

    public boolean legacyOccupied(String show, String seat) {
      return false;
    }

    public java.math.BigDecimal ticketPrice(Showtime show, Seat seat, Customer c) {
      return java.math.BigDecimal.valueOf(show.getPrice());
    }
  }
}
