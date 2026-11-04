import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LegacyReservationTest {
  @TempDir Path directory;
  private String previous;

  @BeforeEach
  void setDirectory() {
    previous =
        utils.JsonFileUtil.resolve("data/tickets.json").toAbsolutePath().getParent().toString();
    utils.JsonFileUtil.setDataDirectory(directory.toString());
  }

  @AfterEach
  void restore() {
    utils.JsonFileUtil.setDataDirectory(previous);
  }

  @Test
  void expiredLegacyReservationStopsBlockingSeat() throws Exception {
    Files.writeString(
        directory.resolve("tickets.json"),
        "[{\"id\":\"T1\",\"showtimeId\":\"S1\",\"seatId\":\"A1\",\"paymentStatus\":\"PENDING\",\"reservationExpiresAt\":\"2000-01-01T00:00:00Z\"}]");
    assertNull(new repository.TicketRepository().findByShowtimeAndSeat("S1", "A1"));
    assertEquals(
        model.PaymentStatus.FAILED,
        new repository.TicketRepository().findAll().get(0).getPaymentStatus());
  }

  @Test
  void givesNewLegacyReservationsFiniteHold() {
    var ticket =
        new model.Ticket(
            "T1",
            "C1",
            "S1",
            "A1",
            java.math.BigDecimal.valueOf(100000),
            model.PaymentStatus.PENDING);
    new repository.TicketRepository().save(ticket);
    assertTrue(Files.exists(directory.resolve("tickets.json")));
    assertNotNull(new repository.TicketRepository().findByShowtimeAndSeat("S1", "A1"));
    assertTrue(readTicketFile().contains("reservationExpiresAt"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(
      value = model.PaymentStatus.class,
      names = {"SUCCESS", "PENDING"})
  void futureLegacyTicketsProtectShowSeatMovieAndRoomAcrossIdCase(model.PaymentStatus status) {
    seedInventory(false);
    writeTicket(status, java.time.Instant.now().plusSeconds(600).toString());
    assertEquals(
        409,
        assertThrows(
                handler.ApiException.class,
                () -> new repository.ShowtimeRepository().deleteById("show"))
            .status);
    assertEquals(
        409,
        assertThrows(
                handler.ApiException.class,
                () -> new repository.SeatRepository().deleteById("seat"))
            .status);
    assertEquals(
        409,
        assertThrows(
                handler.ApiException.class,
                () -> new repository.MovieRepository().deleteById("mOvIe"))
            .status);
    assertEquals(
        409,
        assertThrows(
                handler.ApiException.class,
                () -> new repository.CinemaRoomRepository().deleteById("rOoM"))
            .status);
    assertNotNull(new repository.ShowtimeRepository().findById("SHOW"));
    assertNotNull(new repository.SeatRepository().findById("SEAT"));
  }

  @Test
  void commerceGuardsReceiveCanonicalIdsForMixedCaseMutations() {
    seedInventory(false);
    utils.CommerceInventory.register(
        directory, (show, seat) -> false, "SHOW"::equals, "SEAT"::equals);
    assertThrows(
        handler.ApiException.class, () -> new repository.ShowtimeRepository().deleteById("show"));
    assertThrows(
        handler.ApiException.class, () -> new repository.SeatRepository().deleteById("seat"));
    assertThrows(
        handler.ApiException.class, () -> new repository.MovieRepository().deleteById("mOvIe"));
    assertThrows(
        handler.ApiException.class, () -> new repository.CinemaRoomRepository().deleteById("rOoM"));
    assertThrows(
        handler.ApiException.class,
        () ->
            new repository.MovieRepository()
                .update(
                    new model.Movie(
                        "movie", "Updated", 120, "Drama", model.MovieStatus.NOW_SHOWING)));
    assertThrows(
        handler.ApiException.class,
        () ->
            new repository.CinemaRoomRepository()
                .update(new model.CinemaRoom("room", "Updated", 1, 1)));
  }

  @Test
  void expiredInvalidAndUnboundedReservationsDoNotProtectInventory() {
    for (String expiry : java.util.Arrays.asList("2000-01-01T00:00:00Z", "invalid", null)) {
      seedInventory(false);
      writeTicket(model.PaymentStatus.PENDING, expiry);
      assertDoesNotThrow(() -> new repository.ShowtimeRepository().deleteById("show"));
      assertDoesNotThrow(() -> new repository.SeatRepository().deleteById("seat"));
    }
  }

  @Test
  void failedRefundedAndHistoricalTicketsAllowInventoryChanges() {
    for (model.PaymentStatus status :
        java.util.List.of(model.PaymentStatus.FAILED, model.PaymentStatus.REFUNDED)) {
      seedInventory(false);
      writeTicket(status, null);
      assertDoesNotThrow(() -> new repository.ShowtimeRepository().deleteById("show"));
      assertDoesNotThrow(() -> new repository.SeatRepository().deleteById("seat"));
    }
    seedInventory(true);
    writeTicket(model.PaymentStatus.SUCCESS, null);
    assertDoesNotThrow(() -> new repository.ShowtimeRepository().deleteById("show"));
    assertDoesNotThrow(() -> new repository.SeatRepository().deleteById("seat"));
  }

  @Test
  void safeCaseInsensitiveUpdatesPreserveStoredIdentity() {
    seedInventory(false);
    new repository.MovieRepository()
        .update(new model.Movie("movie", "Updated", 120, "Drama", model.MovieStatus.NOW_SHOWING));
    new repository.CinemaRoomRepository().update(new model.CinemaRoom("room", "Updated", 1, 1));
    new repository.SeatRepository()
        .update(
            new model.Seat("seat", "room", "A", 1, model.SeatType.NORMAL, model.SeatStatus.ACTIVE));
    assertEquals("MOVIE", new repository.MovieRepository().findById("movie").getId());
    assertEquals("ROOM", new repository.CinemaRoomRepository().findById("room").getId());
    assertEquals("SEAT", new repository.SeatRepository().findById("seat").getId());
    assertEquals("ROOM", new repository.SeatRepository().findById("seat").getRoomId());
    new repository.SeatRepository()
        .saveAll(
            java.util.List.of(
                new model.Seat(
                    "seat", "room", "A", 1, model.SeatType.NORMAL, model.SeatStatus.ACTIVE)));
    assertEquals("SEAT", new repository.SeatRepository().findById("seat").getId());
  }

  private void seedInventory(boolean historical) {
    java.time.LocalDateTime start =
        java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
            .plusDays(historical ? -2 : 2);
    utils.JsonFileUtil.writeJsonToFile(
        "data/movies.json",
        java.util.List.of(
            new model.Movie("MOVIE", "Movie", 120, "Drama", model.MovieStatus.NOW_SHOWING)));
    utils.JsonFileUtil.writeJsonToFile(
        "data/cinemaRooms.json", java.util.List.of(new model.CinemaRoom("ROOM", "Room", 1, 1)));
    utils.JsonFileUtil.writeJsonToFile(
        "data/showtimes.json",
        java.util.List.of(
            new model.Showtime(
                "SHOW", "mOvIe", "rOoM", start.toString(), start.plusHours(2).toString(), 100000)));
    utils.JsonFileUtil.writeJsonToFile(
        "data/seats.json",
        java.util.List.of(
            new model.Seat(
                "SEAT", "rOoM", "A", 1, model.SeatType.NORMAL, model.SeatStatus.ACTIVE)));
    utils.JsonFileUtil.writeJsonToFile("data/tickets.json", java.util.List.of());
  }

  private void writeTicket(model.PaymentStatus status, String expiry) {
    model.Ticket ticket =
        new model.Ticket(
            "ticket", "customer", "sHoW", "sEaT", java.math.BigDecimal.valueOf(100000), status);
    ticket.setReservationExpiresAt(expiry);
    utils.JsonFileUtil.writeJsonToFile("data/tickets.json", java.util.List.of(ticket));
  }

  private String readTicketFile() {
    try {
      return Files.readString(directory.resolve("tickets.json"));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
