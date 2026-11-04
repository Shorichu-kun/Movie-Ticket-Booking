package commerce;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import handler.ApiException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import model.commerce.CommerceState.SeatHold;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.commerce.*;

class CommerceRepositoryTest {
  @TempDir Path directory;
  ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  SeatHold hold =
      new SeatHold(
          "hold",
          "customer",
          "show",
          List.of("seat"),
          Instant.now(),
          Instant.now().plusSeconds(420),
          "ACTIVE");

  @Test
  void jsonTransactionRollsBackAllChangesWhenOperationFails() {
    CommerceRepository repository =
        new JsonCommerceRepository(directory.resolve("state.json"), mapper);
    assertThrows(
        IllegalStateException.class,
        () ->
            repository.transaction(
                state -> {
                  state.holds().put(hold.id(), hold);
                  throw new IllegalStateException("abort");
                }));
    assertEquals(Boolean.TRUE, repository.transaction(state -> state.holds().isEmpty()));
  }

  @Test
  void failedPersistenceDoesNotReportSuccessOrPublishHold() throws Exception {
    Path parent = directory.resolve("blocked");
    Files.writeString(parent, "file");
    CommerceRepository repository =
        new JsonCommerceRepository(parent.resolve("state.json"), mapper);
    assertThrows(
        ApiException.class,
        () ->
            repository.transaction(
                state -> {
                  state.holds().put(hold.id(), hold);
                  return hold;
                }));
    Files.delete(parent);
    assertEquals(Boolean.TRUE, repository.transaction(state -> state.holds().isEmpty()));
  }

  @Test
  void memoryRepositoryUsesSameRollbackSemantics() {
    CommerceRepository repository = new InMemoryCommerceRepository(mapper);
    assertThrows(
        IllegalStateException.class,
        () ->
            repository.transaction(
                state -> {
                  state.holds().put(hold.id(), hold);
                  throw new IllegalStateException("abort");
                }));
    assertEquals(Boolean.TRUE, repository.transaction(state -> state.holds().isEmpty()));
  }
}
