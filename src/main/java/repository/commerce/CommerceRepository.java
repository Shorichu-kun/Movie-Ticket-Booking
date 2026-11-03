package repository.commerce;

import java.util.function.Function;
import model.commerce.CommerceState;

public interface CommerceRepository {
  <T> T transaction(Function<CommerceState, T> operation);
}
