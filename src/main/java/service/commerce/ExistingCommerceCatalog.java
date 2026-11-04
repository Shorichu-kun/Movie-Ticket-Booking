package service.commerce;

import dto.PriceQuoteRequest;
import java.math.BigDecimal;
import java.util.List;
import model.*;
import repository.*;
import service.CustomerService;

public final class ExistingCommerceCatalog implements CommerceCatalog {
  private final CustomerService customers;
  private final ShowtimeRepository showtimes = new ShowtimeRepository();
  private final MovieRepository movies = new MovieRepository();
  private final CinemaRoomRepository rooms = new CinemaRoomRepository();
  private final SeatRepository seats = new SeatRepository();
  private final TicketRepository tickets = new TicketRepository();

  public ExistingCommerceCatalog(CustomerService customers) {
    this.customers = customers;
  }

  public Showtime showtime(String id) {
    return showtimes.findById(id);
  }

  public Movie movie(String id) {
    return movies.findById(id);
  }

  public CinemaRoom room(String id) {
    return rooms.findById(id);
  }

  public List<Seat> seats(String id) {
    return seats.findByRoomId(id);
  }

  public boolean legacyOccupied(String show, String seat) {
    return tickets.findByShowtimeAndSeat(show, seat) != null;
  }

  public BigDecimal ticketPrice(Showtime show, Seat seat, Customer customer) {
    BigDecimal base = BigDecimal.valueOf(show.getPrice());
    BigDecimal extra =
        BigDecimal.valueOf(
            switch (seat.getType()) {
              case NORMAL -> 0;
              case VIP -> 20000;
              case COUPLE -> 50000;
            });
    return customer == null
        ? base.add(extra)
        : customers.quotePrice(customer.getId(), new PriceQuoteRequest(base, extra)).finalPrice();
  }
}
