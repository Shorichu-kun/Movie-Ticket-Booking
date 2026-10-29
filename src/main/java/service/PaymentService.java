
package service;

import model.Ticket;
import model.PaymentStatus;
import repository.TicketRepository;
import payment.PaymentMethod;

import java.math.BigDecimal;

public class PaymentService {

    private TicketRepository ticketRepository;

    public PaymentService() {
        ticketRepository = new TicketRepository();
    }

    public boolean payTicket(String ticketId, PaymentMethod paymentMethod) {

        // 1. Kiem tra ma ve
        if (ticketId == null || ticketId.trim().isEmpty()) {
            throw new IllegalArgumentException("Ma ve khong hop le.");
        }

        // 2. Tim ve
        Ticket ticket = ticketRepository.findById(ticketId);

        if (ticket == null) {
            throw new IllegalArgumentException("Ve khong ton tai.");
        }

        // 3. Kiem tra trang thai ve
        if (ticket.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Ve khong o trang thai cho thanh toan."
            );
        }

        // 4. Kiem tra phuong thuc thanh toan
        if (paymentMethod == null) {
            throw new IllegalArgumentException(
                    "Phuong thuc thanh toan khong hop le."
            );
        }

        // 5. Kiem tra so tien
        BigDecimal amount = ticket.getPrice();

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Gia ve khong hop le.");
        }

        // 6. Thuc hien thanh toan
        boolean success = paymentMethod.pay(amount);

        if (success) {
            ticket.setPaymentStatus(PaymentStatus.SUCCESS);
        } else {
            ticket.setPaymentStatus(PaymentStatus.FAILED);
        }

        // 7. Cap nhat ve
        ticketRepository.update(ticket);

        return success;
    }
}
