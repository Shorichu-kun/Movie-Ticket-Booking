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

final class SeatInventoryService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private final CommerceCatalog catalog;
  private final Clock clock;

  SeatInventoryService(CommerceCatalog catalog, Clock clock) {
    this.catalog = catalog;
    this.clock = clock;
  }

  List<String> selection(CommerceState s, Showtime show, List<String> ids, String ignoredHold) {
    if (ids == null
        || ids.isEmpty()
        || ids.size() > 8
        || ids.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ids).size() != ids.size())
      throw error(400, "Chọn từ 1 đến 8 ghế khác nhau.");
    List<String> canonical = new ArrayList<>();
    for (String id : ids) {
      Seat seat = seat(show, id);
      if (seat.getStatus() != SeatStatus.ACTIVE || seat.getType() == null)
        throw error(409, "Ghế không hoạt động.");
      if (occupied(s, show.getId(), seat.getId(), ignoredHold))
        throw error(409, "Ghế đã được giữ hoặc bán.");
      canonical.add(seat.getId());
    }
    if (new HashSet<>(canonical).size() != canonical.size())
      throw error(400, "Không được chọn trùng ghế.");
    return List.copyOf(canonical);
  }

  boolean occupied(CommerceState s, String show, String seat, String ignoredHold) {
    return catalog.legacyOccupied(show, seat)
        || s.holds().values().stream()
            .anyMatch(
                h ->
                    h.active(now())
                        && !h.id().equals(ignoredHold)
                        && h.showtimeId().equals(show)
                        && h.seatIds().contains(seat))
        || sold(s, show, seat);
  }

  boolean sold(CommerceState s, String show, String seat) {
    return s.orders().values().stream()
        .anyMatch(
            o ->
                o.showtime().getId().equals(show)
                    && o.seatIds().contains(seat)
                    && Set.of("PAID", "REFUND_PENDING").contains(o.status()));
  }

  Instant deadline(
      CommerceState s, SeatHold hold, String method, String owner, String ignoredOrder) {
    Instant deadline;
    if (method.equals("CASH")) {
      Showtime show = show(hold.showtimeId(), true);
      if (start(show).isBefore(now().plusSeconds(3600)))
        throw error(409, "Thanh toán quầy chỉ mở khi suất còn ít nhất 60 phút.");
      if (s.orders().values().stream()
          .anyMatch(
              o ->
                  o.customerId().equals(owner)
                      && o.method().equals("CASH")
                      && o.status().equals("PENDING_PAYMENT")
                      && !o.id().equals(ignoredOrder)))
        throw error(409, "Mỗi khách chỉ có một đơn chờ thanh toán tại quầy.");
      deadline = minimum(hold.createdAt().plusSeconds(900), start(show).minusSeconds(1800));
    } else
      deadline =
          minimum(
              hold.createdAt().plusSeconds(420),
              start(show(hold.showtimeId(), true)).minusSeconds(300));
    if (!now().isBefore(deadline)) throw error(409, "Hạn của phương thức này đã kết thúc.");
    return deadline;
  }

  Showtime show(String id, boolean booking) {
    Showtime show = catalog.showtime(id);
    if (show == null) throw error(404, "Không tìm thấy suất chiếu.");
    if (catalog.room(show.getRoomId()) == null
        || !Double.isFinite(show.getPrice())
        || show.getPrice() <= 0) throw error(409, "Suất chiếu không hợp lệ.");
    if (booking && !bookable(show)) throw error(409, "Suất đã ngừng bán hoặc phim đã ngừng chiếu.");
    return show;
  }

  Seat seat(Showtime show, String id) {
    return catalog.seats(show.getRoomId()).stream()
        .filter(seat -> seat.getId().equalsIgnoreCase(id))
        .findFirst()
        .orElseThrow(() -> error(400, "Ghế không thuộc phòng của suất chiếu."));
  }

  void refundEligible(Order order) {
    if (order.tickets().stream().anyMatch(t -> t.status().equals("USED")) || order.comboCollected())
      throw error(409, "Vé hoặc combo đã sử dụng; không thể hoàn.");
    if (!now().isBefore(start(order.showtime()).minusSeconds(3600)))
      throw error(409, "Chỉ nhận yêu cầu hoàn trước suất ít nhất 60 phút.");
  }

  Instant end(Showtime show) {
    try {
      return LocalDateTime.parse(show.getEndTime()).atZone(ZONE).toInstant();
    } catch (DateTimeException | NullPointerException e) {
      return start(show).plusSeconds(10800);
    }
  }

  boolean bookable(Showtime show) {
    Movie movie = catalog.movie(show.getMovieId());
    try {
      return movie != null
          && movie.getStatus() != null
          && movie.getStatus() != MovieStatus.ENDED
          && now().isBefore(start(show).minusSeconds(300));
    } catch (DateTimeException | NullPointerException e) {
      return false;
    }
  }

  Instant start(Showtime show) {
    return LocalDateTime.parse(show.getStartTime()).atZone(ZONE).toInstant();
  }

  private Instant minimum(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
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
