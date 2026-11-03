package repository.commerce;

import com.fasterxml.jackson.databind.*;
import handler.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.function.Function;
import model.commerce.CommerceState;
import repository.TicketRepository;

public final class JsonCommerceRepository implements CommerceRepository {
  private final Path path;
  private final ObjectMapper mapper;

  public JsonCommerceRepository(Path path, ObjectMapper mapper) {
    this.path = path.toAbsolutePath();
    this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public <T> T transaction(Function<CommerceState, T> operation) {
    synchronized (TicketRepository.LOCK) {
      CommerceState state = read();
      JsonNode before = mapper.valueToTree(state);
      T result = operation.apply(state);
      if (!before.equals(mapper.valueToTree(state))) write(state);
      return result;
    }
  }

  private CommerceState read() {
    if (Files.notExists(path)) return new CommerceState();
    try {
      CommerceState state =
          mapper
              .readerFor(CommerceState.class)
              .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
              .readValue(path.toFile());
      if (state == null
          || state.holds() == null
          || state.orders() == null
          || state.attempts() == null
          || state.members() == null
          || state.requests() == null) throw new IOException("Invalid commerce state");
      return state;
    } catch (IOException e) {
      throw new ApiException(500, "Không thể đọc dữ liệu giao dịch.");
    }
  }

  private void write(CommerceState state) {
    Path temp = null;
    try {
      Files.createDirectories(path.getParent());
      temp = Files.createTempFile(path.getParent(), ".commerce-", ".tmp");
      mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), state);
      Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new ApiException(500, "Không thể lưu nguyên tử dữ liệu giao dịch.");
    } finally {
      if (temp != null)
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
        }
    }
  }
}
