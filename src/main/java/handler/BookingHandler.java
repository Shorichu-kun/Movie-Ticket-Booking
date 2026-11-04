package handler;

import model.*;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import service.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingHandler {
    private final BookingService bookings;
    private final AccountService accounts;

    public BookingHandler(BookingService bookings, AccountService accounts) {
        this.bookings = bookings;
        this.accounts = accounts;
    }

    @GetMapping("/showtimes/{id}/seats")
    public List<BookingService.SeatView> seats(@PathVariable String id, Authentication auth) {
        return bookings.seatMap(id, accounts.customer(auth));
    }

    @PostMapping("/quote")
    public BookingService.Quote quote(
            @RequestBody BookingService.BookingRequest request, Authentication auth) {
        return bookings.quote(request, accounts.customer(auth));
    }

    @PostMapping
    public ResponseEntity<List<Ticket>> book(
            @RequestBody BookingService.BookingRequest request, Authentication auth) {
        return ResponseEntity.status(201).body(bookings.book(request, accounts.customer(auth)));
    }

    @GetMapping("/mine")
    public List<Ticket> mine(Authentication auth) {
        return bookings.mine(accounts.customer(auth));
    }

    @GetMapping
    public List<Ticket> all() {
        return bookings.all();
    }

    @PostMapping("/{id}/cancel")
    public Ticket cancel(@PathVariable String id, Authentication auth) {
        Account a = accounts.require(auth);
        return bookings.cancel(id, a.customerId(), a.role().equals("ADMIN"));
    }

    @PostMapping("/{id}/confirm-payment")
    public Ticket confirm(@PathVariable String id) {
        return bookings.confirmPayment(id);
    }
}
