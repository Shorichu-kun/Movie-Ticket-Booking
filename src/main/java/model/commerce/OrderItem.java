package model.commerce;

import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TicketItem.class, name = "TICKET"),
  @JsonSubTypes.Type(value = ComboItem.class, name = "COMBO")
})
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE)
public abstract class OrderItem {
  private final String title;
  private final int quantity;
  private final BigDecimal unitPrice;

  protected OrderItem(String title, int quantity, BigDecimal unitPrice) {
    if (quantity < 1 || unitPrice.signum() < 0)
      throw new IllegalArgumentException("Dòng hàng không hợp lệ.");
    this.title = title;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  public String title() {
    return title;
  }

  public int quantity() {
    return quantity;
  }

  public BigDecimal unitPrice() {
    return unitPrice;
  }

  public BigDecimal total() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }

  public abstract String type();
}
