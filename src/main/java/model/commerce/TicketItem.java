package model.commerce;

import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;

public final class TicketItem extends OrderItem {
  private final String seatId;

  @JsonCreator
  public TicketItem(
      @JsonProperty("title") String title,
      @JsonProperty("unitPrice") BigDecimal price,
      @JsonProperty("seatId") String seatId) {
    super(title, 1, price);
    this.seatId = seatId;
  }

  public String seatId() {
    return seatId;
  }

  public String type() {
    return "TICKET";
  }
}
