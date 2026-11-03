package model.commerce;

import java.util.List;

public final class CommerceRequests {
  private CommerceRequests() {}

  public record HoldRequest(String showtimeId, List<String> seatIds) {}

  public record ComboRequest(String productId, int quantity, String variant) {}

  public record Contact(String name, String email, String phone) {}

  public record CheckoutRequest(
      String holdId,
      List<ComboRequest> combos,
      String voucherCode,
      int points,
      String method,
      Contact contact) {}

  public record Preferences(
      String birthDate, String favoriteGenre, boolean notifyShowtime, boolean notifyOffers) {}
}
