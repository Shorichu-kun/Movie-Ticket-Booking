package repository.commerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;
import model.commerce.CommerceState;
import repository.TicketRepository;

public final class InMemoryCommerceRepository implements CommerceRepository {
  private final ObjectMapper mapper;
  private CommerceState state = new CommerceState();

  public InMemoryCommerceRepository(ObjectMapper mapper) {
    this.mapper = mapper.copy();
  }

  public <T> T transaction(Function<CommerceState, T> operation) {
    synchronized (TicketRepository.LOCK) {
      CommerceState next = mapper.convertValue(state, CommerceState.class);
      T result = operation.apply(next);
      state = mapper.convertValue(next, CommerceState.class);
      return result;
    }
  }
}
