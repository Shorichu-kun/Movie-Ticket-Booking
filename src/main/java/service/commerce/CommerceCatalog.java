package service.commerce;

import java.math.BigDecimal;
import java.util.List;
import model.*;

public interface CommerceCatalog {
  Showtime showtime(String id);

  Movie movie(String id);

  CinemaRoom room(String id);

  List<Seat> seats(String roomId);

  boolean legacyOccupied(String showtimeId, String seatId);

  BigDecimal ticketPrice(Showtime showtime, Seat seat, Customer customer);
}
