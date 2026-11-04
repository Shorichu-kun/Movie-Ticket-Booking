package model.commerce;

import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;

public final class ComboItem extends OrderItem {
  private final String productId;
  private final String variant;

  @JsonCreator
  public ComboItem(
      @JsonProperty("title") String title,
      @JsonProperty("quantity") int quantity,
      @JsonProperty("unitPrice") BigDecimal price,
      @JsonProperty("productId") String productId,
      @JsonProperty("variant") String variant) {
    super(title, quantity, price);
    this.productId = productId;
    this.variant = variant;
  }

  public String type() {
    return "COMBO";
  }
}
