
package model;

import java.math.BigDecimal;

public class Ticket {
    private String id;
    private String customerId;
    private String showtimeId;
    private String seatId;
    private BigDecimal price;
    private PaymentStatus paymentStatus;

    public Ticket() {
    }

    public Ticket(String id, String customerId, String showtimeId,
                  String seatId, BigDecimal price,
                  PaymentStatus paymentStatus) {
        this.id = id;
        this.customerId = customerId;
        this.showtimeId = showtimeId;
        this.seatId = seatId;
        this.price = price;
        this.paymentStatus = paymentStatus;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getShowtimeId() {
        return showtimeId;
    }

    public void setShowtimeId(String showtimeId) {
        this.showtimeId = showtimeId;
    }

    public String getSeatId() {
        return seatId;
    }

    public void setSeatId(String seatId) {
        this.seatId = seatId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
