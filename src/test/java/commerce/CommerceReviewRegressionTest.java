package commerce;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import model.commerce.CommerceRequests.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CommerceReviewRegressionTest {
  @TempDir Path directory;
  CommerceServiceTest fixture;

  @BeforeEach
  void setup() {
    fixture = new CommerceServiceTest();
    fixture.directory = directory;
    fixture.setup();
  }

  String paid(String seat, String key, int points) {
    String order =
        fixture.id(
            fixture.service.createOrder(
                fixture.customer,
                fixture.request(fixture.hold("c1", seat), null, points, "MOMO"),
                key));
    fixture.service.confirmPayment("c1", false, fixture.id(fixture.attempt(order, key)), "SUCCESS");
    return order;
  }

  void refund(String order) {
    fixture.service.requestRefund("c1", order);
    fixture.service.approveRefund(order, true);
  }

  Object points() {
    return ((Map<?, ?>) fixture.service.me("c1").get("membership")).get("points");
  }

  String earnAndSpend() {
    fixture.catalog.price = 1000000;
    String first = paid("s1", "earn", 0);
    fixture.catalog.price = 100000;
    paid("s2", "spend", 20);
    return first;
  }

  @Test
  void refundOfSpendingThenSourceOrderDoesNotMintPoints() {
    String first = earnAndSpend();
    String spending = fixture.id(fixture.service.orders("c1").get(1));
    refund(spending);
    fixture.service = fixture.createService();
    refund(first);
    assertEquals(0, points());
  }

  @Test
  void refundOfCreditThatRepaidDebtRestoresOriginalDebt() {
    refund(earnAndSpend());
    assertEquals(-18, points());
    fixture.catalog.price = 1000000;
    String debtCredit = paid("s3", "credit", 0);
    assertEquals(12, points());
    fixture.service = fixture.createService();
    refund(debtCredit);
    assertEquals(-18, points());
  }

  @Test
  void refundRestoresExpiredSpentPointsWithAtLeastThirtyDaysValidity() {
    fixture.catalog.startTime = "2028-11-04T20:00:00";
    earnAndSpend();
    String spending = fixture.id(fixture.service.orders("c1").get(1));
    fixture.clock.advance(Duration.ofDays(370));
    refund(spending);
    assertEquals(20, points());
    fixture.clock.advance(Duration.ofDays(29));
    assertEquals(20, points());
    fixture.clock.advance(Duration.ofDays(2));
    assertEquals(0, points());
  }

  @Test
  void refundInvalidatesUnfundedReservationAndLateChargeIsRecordedAndRefunded() {
    fixture.catalog.price = 1000000;
    String source = paid("s1", "earn", 0);
    fixture.catalog.price = 100000;
    String pending =
        fixture.id(
            fixture.service.createOrder(
                fixture.customer,
                fixture.request(fixture.hold("c1", "s2"), null, 20, "MOMO"),
                "pending"));
    String attempt = fixture.id(fixture.attempt(pending, "pay"));
    fixture.service.confirmPayment("c1", false, attempt, "PROCESSING");
    refund(source);
    assertEquals("CANCELLED", fixture.service.order("c1", pending).get("status"));
    Map<String, Object> cancelled = fixture.service.confirmPayment("c1", false, attempt, "SUCCESS");
    assertTrue(((List<?>) cancelled.get("tickets")).isEmpty());
    assertEquals("SUCCEEDED", fixture.service.payment("c1", false, attempt).get("status"));
    assertEquals(true, fixture.service.payment("c1", false, attempt).get("refunded"));
  }

  @Test
  void switchedOutAttemptChargeIsRefundedWithoutAffectingCurrentAttempt() {
    String order = fixture.id(fixture.order("s1", "one"));
    String old = fixture.id(fixture.attempt(order, "old"));
    String current = fixture.id(fixture.service.startPayment("c1", order, "CARD", "new"));
    Map<String, Object> waiting = fixture.service.confirmPayment("c1", false, old, "LATE_SUCCESS");
    assertEquals("PENDING_PAYMENT", waiting.get("status"));
    assertTrue(((List<?>) waiting.get("tickets")).isEmpty());
    assertEquals(true, fixture.service.payment("c1", false, old).get("refunded"));
    assertEquals("CREATED", fixture.service.payment("c1", false, current).get("status"));
    fixture.service.confirmPayment("c1", false, old, "LATE_SUCCESS");
    assertEquals(
        "PAID", fixture.service.confirmPayment("c1", false, current, "SUCCESS").get("status"));
  }

  @Test
  void comboPickupAfterAdmissionAndRepeatedScansAreIdempotent() {
    CheckoutRequest request =
        new CheckoutRequest(
            fixture.hold("c1", "s1"),
            List.of(new ComboRequest("SINGLE", 1, null)),
            null,
            0,
            "MOMO",
            new Contact("Customer", "c@example.test", "0901234567"));
    String order = fixture.id(fixture.service.createOrder(fixture.customer, request, "one"));
    Map<String, Object> paid =
        fixture.service.confirmPayment(
            "c1", false, fixture.id(fixture.attempt(order, "pay")), "SUCCESS");
    String token = (String) ((Map<?, ?>) ((List<?>) paid.get("tickets")).get(0)).get("token");
    fixture.clock.advance(Duration.ofHours(34));
    fixture.service.checkIn(token, false);
    assertEquals(false, fixture.service.checkIn(token, false).get("comboCollected"));
    assertEquals(true, fixture.service.checkIn(token, true).get("comboCollected"));
    int events = ((List<?>) fixture.service.order("c1", order).get("events")).size();
    assertEquals(true, fixture.service.checkIn(token, true).get("comboCollected"));
    assertEquals(events, ((List<?>) fixture.service.order("c1", order).get("events")).size());
  }

  @Test
  void pointExpiryDuringProcessingCancelsReservationAndRefundsLaterSuccess() {
    fixture.catalog.startTime = "2028-11-04T20:00:00";
    fixture.catalog.price = 1000000;
    paid("s1", "earn", 0);
    fixture.clock.advance(Duration.ofDays(365).minusMinutes(3));
    fixture.catalog.price = 100000;
    String pending =
        fixture.id(
            fixture.service.createOrder(
                fixture.customer,
                fixture.request(fixture.hold("c1", "s2"), null, 20, "MOMO"),
                "pending"));
    String attempt = fixture.id(fixture.attempt(pending, "pay"));
    fixture.service.confirmPayment("c1", false, attempt, "PROCESSING");
    fixture.clock.advance(Duration.ofMinutes(4));
    fixture.service.confirmPayment("c1", false, attempt, "SUCCESS");
    assertEquals("CANCELLED", fixture.service.order("c1", pending).get("status"));
    assertEquals(true, fixture.service.payment("c1", false, attempt).get("refunded"));
    assertEquals(0, points());
  }

  @Test
  void ordinarySuccessFromLocallyReplacedAttemptAlsoRefundsWithoutClosingCurrentOrder()
      throws Exception {
    String order = fixture.id(fixture.order("s1", "one"));
    String old = fixture.id(fixture.attempt(order, "old"));
    fixture.service.startPayment("c1", order, "CARD", "current");
    Map<String, Object> waiting = fixture.service.confirmPayment("c1", false, old, "SUCCESS");
    assertEquals("PENDING_PAYMENT", waiting.get("status"));
    assertEquals(true, fixture.service.payment("c1", false, old).get("refunded"));
    fixture.service.confirmPayment("c1", false, old, "SUCCESS");
    assertEquals(
        1,
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(directory.resolve("commerce.json").toFile())
            .get("refunds")
            .size());
  }

  @Test
  void returningSpentPointsReleasesTheirCreditOffsetAndLaterRefundRemovesIt() {
    String source = earnAndSpend();
    String spending = fixture.id(fixture.service.orders("c1").get(1));
    refund(source);
    fixture.catalog.price = 1000000;
    String credit = paid("s3", "credit", 0);
    refund(spending);
    assertEquals(30, points());
    refund(credit);
    assertEquals(0, points());
  }
}
