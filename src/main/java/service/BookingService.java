package service;

import dto.PriceQuoteRequest;

import handler.ApiException;

import model.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import policy.*;

import repository.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class BookingService {
    private final TicketRepository tickets = new TicketRepository();
    private final ShowtimeRepository showtimes = new ShowtimeRepository();
    private final MovieRepository movies = new MovieRepository();
    private final SeatRepository seats = new SeatRepository();
    private final CinemaRoomRepository rooms = new CinemaRoomRepository();
    private final CustomerService customers;

    public BookingService() {
        this.customers = null;
    }

    @Autowired
    public BookingService(CustomerService customers) {
        this.customers = customers;
    }

    public List<Ticket> all() {
        return tickets.findAll();
    }

    public List<Ticket> mine(Customer c) {
        return all().stream().filter(t -> c.getId().equals(t.getCustomerId())).toList();
    }

    public List<SeatView> seatMap(String showtimeId, Customer c) {
        synchronized (TicketRepository.LOCK) {
            Showtime s = requireShowtime(showtimeId, false);
            boolean bookable = isFuture(s) && movieBookable(s);
            return seats.findByRoomId(s.getRoomId()).stream()
                    .map(
                            seat ->
                                    new SeatView(
                                            seat.getId(),
                                            seat.getRoomId(),
                                            seat.getRowLabel(),
                                            seat.getSeatNumber(),
                                            seat.getType(),
                                            seat.getStatus(),
                                            bookable
                                                    && seat.getStatus() == SeatStatus.ACTIVE
                                                    && tickets.findByShowtimeAndSeat(
                                                                    s.getId(), seat.getId())
                                                            == null,
                                            price(s, seat, c)))
                    .toList();
        }
    }

    public Quote quote(BookingRequest r, Customer c) {
        synchronized (TicketRepository.LOCK) {
            Selection selection = selection(r);
            BigDecimal subtotal = BigDecimal.ZERO, total = BigDecimal.ZERO;
            for (Seat seat : selection.seats()) {
                subtotal =
                        subtotal.add(
                                BigDecimal.valueOf(selection.showtime().getPrice())
                                        .add(surcharge(seat)));
                total = total.add(price(selection.showtime(), seat, c));
            }
            BigDecimal rate =
                    c == null
                            ? BigDecimal.ZERO
                            : customers.getDiscountPolicies().get(c.getCustomerType());
            return new Quote(subtotal, subtotal.subtract(total), rate, total);
        }
    }

    public List<Ticket> book(BookingRequest r, Customer c) {
        synchronized (TicketRepository.LOCK) {
            if (c == null) throw new ApiException(403, "Chỉ khách hàng có thể đặt vé.");
            Selection selection = selection(r);
            List<Ticket> booked =
                    selection.seats().stream()
                            .map(
                                    seat ->
                                            new Ticket(
                                                    UUID.randomUUID().toString(),
                                                    c.getId(),
                                                    selection.showtime().getId(),
                                                    seat.getId(),
                                                    price(selection.showtime(), seat, c),
                                                    PaymentStatus.PENDING))
                            .toList();
            tickets.saveAll(booked);
            return booked;
        }
    }

    public Ticket cancel(String id, String customerId, boolean admin) {
        synchronized (TicketRepository.LOCK) {
            Ticket t = requireTicket(id);
            if (!admin && !Objects.equals(customerId, t.getCustomerId()))
                throw new ApiException(403, "Bạn không có quyền hủy vé này.");
            if (t.getPaymentStatus() != PaymentStatus.PENDING)
                throw new ApiException(409, "Chỉ có thể hủy vé đang chờ thanh toán.");
            t.setPaymentStatus(PaymentStatus.FAILED);
            tickets.update(t);
            return t;
        }
    }

    public Ticket confirmPayment(String id) {
        synchronized (TicketRepository.LOCK) {
            Ticket t = requireTicket(id);
            if (t.getPaymentStatus() != PaymentStatus.PENDING)
                throw new ApiException(409, "Vé không ở trạng thái chờ thanh toán.");
            t.setPaymentStatus(PaymentStatus.SUCCESS);
            tickets.update(t);
            return t;
        }
    }

    private Ticket requireTicket(String id) {
        Ticket t = tickets.findById(id);
        if (t == null) throw new ApiException(404, "Không tìm thấy vé.");
        return t;
    }

    private Selection selection(BookingRequest r) {
        if (r == null
                || r.showtimeId() == null
                || r.seatIds() == null
                || r.seatIds().isEmpty()
                || r.seatIds().size() > 20
                || new HashSet<>(r.seatIds()).size() != r.seatIds().size())
            throw new ApiException(400, "Chọn từ 1 đến 20 ghế khác nhau.");
        Showtime s = requireShowtime(r.showtimeId(), true);
        List<Seat> selected = new ArrayList<>();
        for (String id : r.seatIds()) {
            Seat seat = seats.findById(id);
            if (seat == null
                    || !s.getRoomId().equalsIgnoreCase(seat.getRoomId())
                    || seat.getStatus() != SeatStatus.ACTIVE
                    || seat.getType() == null)
                throw new ApiException(
                        400, "Ghế không tồn tại hoặc không hoạt động trong phòng này.");
            if (selected.stream()
                    .anyMatch(existing -> existing.getId().equalsIgnoreCase(seat.getId())))
                throw new ApiException(400, "Không thể chọn trùng ghế.");
            if (tickets.findByShowtimeAndSeat(s.getId(), seat.getId()) != null)
                throw new ApiException(409, "Ghế đã được đặt. Vui lòng chọn ghế khác.");
            selected.add(seat);
        }
        return new Selection(s, selected);
    }

    private Showtime requireShowtime(String id, boolean booking) {
        Showtime s = showtimes.findById(id);
        if (s == null) throw new ApiException(404, "Không tìm thấy suất chiếu.");
        if (rooms.findById(s.getRoomId()) == null)
            throw new ApiException(400, "Phòng chiếu không còn tồn tại.");
        if (!Double.isFinite(s.getPrice()) || s.getPrice() <= 0)
            throw new ApiException(400, "Giá suất chiếu không hợp lệ.");
        if (booking && (!isFuture(s) || !movieBookable(s)))
            throw new ApiException(409, "Suất chiếu đã bắt đầu hoặc phim đã ngừng chiếu.");
        return s;
    }

    private boolean movieBookable(Showtime s) {
        Movie m = movies.findById(s.getMovieId());
        return m != null && m.getStatus() != null && m.getStatus() != MovieStatus.ENDED;
    }

    private boolean isFuture(Showtime s) {
        try {
            return LocalDateTime.parse(s.getStartTime())
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .toInstant()
                    .isAfter(Instant.now());
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }
    }

    private BigDecimal surcharge(Seat seat) {
        return switch (seat.getType()) {
            case NORMAL -> BigDecimal.ZERO;
            case VIP -> new BigDecimal("20000");
            case COUPLE -> new BigDecimal("50000");
        };
    }

    private BigDecimal price(Showtime s, Seat seat, Customer c) {
        BigDecimal base = BigDecimal.valueOf(s.getPrice()), extra = surcharge(seat);
        return c == null
                ? new NormalCustomerPricePolicy().calculatePrice(base, extra)
                : customers.quotePrice(c.getId(), new PriceQuoteRequest(base, extra)).finalPrice();
    }

    /**
     * Original OOP entry point retained for non-HTTP callers; repository enforces collision safety.
     */
    public Ticket bookTicket(
            Customer customer,
            Showtime showtime,
            String seatId,
            BigDecimal seatSurcharge,
            TicketPricePolicy policy) {
        synchronized (TicketRepository.LOCK) {
            if (customer == null
                    || customer.getId() == null
                    || showtime == null
                    || showtime.getId() == null
                    || seatId == null
                    || seatId.isBlank()
                    || policy == null)
                throw new IllegalArgumentException("Thông tin đặt vé không hợp lệ.");
            Ticket ticket =
                    new Ticket(
                            UUID.randomUUID().toString(),
                            customer.getId(),
                            showtime.getId(),
                            seatId,
                            policy.calculatePrice(
                                    BigDecimal.valueOf(showtime.getPrice()), seatSurcharge),
                            PaymentStatus.PENDING);
            tickets.save(ticket);
            return ticket;
        }
    }

    public record BookingRequest(String showtimeId, List<String> seatIds) {}

    public record Quote(
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal discountRate,
            BigDecimal total) {}

    public record SeatView(
            String id,
            String roomId,
            String rowLabel,
            int seatNumber,
            SeatType type,
            SeatStatus status,
            boolean available,
            BigDecimal price) {}

    private record Selection(Showtime showtime, List<Seat> seats) {}
}
