package service.commerce;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import model.commerce.*;
import model.commerce.CommerceState.*;

final class CommerceViews {
  private final Clock clock;
  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  CommerceViews(Clock clock) {
    this.clock = clock;
  }

  Map<String, Object> holdView(SeatHold hold) {
    return map(
        "id",
        hold.id(),
        "showtimeId",
        hold.showtimeId(),
        "seatIds",
        hold.seatIds(),
        "createdAt",
        hold.createdAt(),
        "expiresAt",
        hold.expiresAt(),
        "status",
        hold.status(),
        "serverTime",
        now());
  }

  Map<String, Object> attemptView(PaymentAttempt attempt) {
    return map(
        "id",
        attempt.id(),
        "orderId",
        attempt.orderId(),
        "method",
        attempt.method(),
        "status",
        attempt.status(),
        "amount",
        attempt.amount(),
        "expiresAt",
        attempt.expiresAt(),
        "refunded",
        attempt.refunded());
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> orderView(CommerceState state, Order order) {
    Map<String, Object> view = mapper.convertValue(order, LinkedHashMap.class);
    view.remove("customerId");
    view.remove("holdId");
    view.remove("voucherId");
    view.put(
        "items",
        order.items().stream()
            .map(
                item ->
                    map(
                        "type",
                        item.type(),
                        "title",
                        item.title(),
                        "quantity",
                        item.quantity(),
                        "unitPrice",
                        item.unitPrice(),
                        "total",
                        item.total()))
            .toList());
    view.put(
        "attempts",
        state.attempts().values().stream()
            .filter(attempt -> attempt.orderId().equals(order.id()))
            .map(this::attemptView)
            .toList());
    return view;
  }

  private Instant now() {
    return clock.instant();
  }

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
    return result;
  }
}
