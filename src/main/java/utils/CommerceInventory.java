package utils;

import handler.ApiException;
import java.nio.file.Path;
import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import model.*;
import repository.*;

public final class CommerceInventory {
  private static final Map<Path, Policy> POLICIES = new ConcurrentHashMap<>();
  private static final ZoneId CINEMA_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private CommerceInventory() {}

  public static void register(
      Path directory,
      BiPredicate<String, String> occupied,
      Predicate<String> activeShow,
      Predicate<String> activeSeat) {
    POLICIES.put(
        directory.toAbsolutePath().normalize(), new Policy(occupied, activeShow, activeSeat));
  }

  private static Policy policy() {
    return POLICIES.get(
        JsonFileUtil.resolve("data/tickets.json").toAbsolutePath().normalize().getParent());
  }

  public static boolean isOccupied(String showtimeId, String seatId) {
    Policy policy = policy();
    return policy != null && policy.occupied().test(showtimeId, seatId);
  }

  public static void requireSeatAvailable(String showtimeId, String seatId) {
    if (isOccupied(showtimeId, seatId)) {
      throw new ApiException(409, "Ghế đang được giữ hoặc đã bán trong một đơn hàng.");
    }
  }

  public static void requireShowtimeMutable(String id) {
    synchronized (TicketRepository.LOCK) {
      Showtime show = new ShowtimeRepository().findById(id);
      String canonical = show == null ? id : show.getId();
      Policy policy = policy();
      boolean commerce = policy != null && policy.activeShow().test(canonical);
      boolean legacy = hasLegacyTicket(ticket -> sameId(ticket.getShowtimeId(), canonical));
      if (commerce || legacy)
        throw new ApiException(
            409, "Suất đang có ghế được giữ hoặc vé chưa sử dụng; không thể sửa/xóa.");
    }
  }

  public static void requireSeatMutable(String id) {
    synchronized (TicketRepository.LOCK) {
      Seat seat = new SeatRepository().findById(id);
      String canonical = seat == null ? id : seat.getId();
      Policy policy = policy();
      boolean commerce = policy != null && policy.activeSeat().test(canonical);
      boolean legacy = hasLegacyTicket(ticket -> sameId(ticket.getSeatId(), canonical));
      if (commerce || legacy)
        throw new ApiException(
            409, "Ghế đang thuộc lượt giữ hoặc vé chưa sử dụng; không thể sửa/xóa.");
    }
  }

  private static boolean hasLegacyTicket(Predicate<Ticket> matches) {
    Instant now = Instant.now();
    return new TicketRepository()
        .findAll().stream()
            .filter(matches)
            .anyMatch(ticket -> legacyTicketProtectsInventory(ticket, now));
  }

  private static boolean legacyTicketProtectsInventory(Ticket ticket, Instant now) {
    if (ticket.getPaymentStatus() != PaymentStatus.SUCCESS
        && ticket.getPaymentStatus() != PaymentStatus.PENDING) return false;
    Showtime show = new ShowtimeRepository().findById(ticket.getShowtimeId());
    if (show == null || !showEndsAfter(show, now)) return false;
    if (ticket.getPaymentStatus() == PaymentStatus.SUCCESS) return true;
    try {
      return ticket.getReservationExpiresAt() != null
          && now.isBefore(Instant.parse(ticket.getReservationExpiresAt()));
    } catch (DateTimeException invalid) {
      return false;
    }
  }

  private static boolean showEndsAfter(Showtime show, Instant now) {
    try {
      return now.isBefore(LocalDateTime.parse(show.getEndTime()).atZone(CINEMA_ZONE).toInstant());
    } catch (DateTimeException | NullPointerException invalid) {
      return false;
    }
  }

  private static boolean sameId(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  private record Policy(
      BiPredicate<String, String> occupied,
      Predicate<String> activeShow,
      Predicate<String> activeSeat) {}
}
