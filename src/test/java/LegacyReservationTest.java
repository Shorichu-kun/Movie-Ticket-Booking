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

  private String readTicketFile() {
    try {
      return Files.readString(directory.resolve("tickets.json"));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
