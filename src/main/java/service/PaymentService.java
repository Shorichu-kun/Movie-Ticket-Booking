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

        if (ticketId == null || ticketId.trim().isEmpty()) {
            throw new IllegalArgumentException("Ma ve khong hop le.");
        }

        Ticket ticket = ticketRepository.findById(ticketId);

        if (ticket == null) {
            throw new IllegalArgumentException("Ve khong ton tai.");
        }

        if (ticket.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Ve khong o trang thai cho thanh toan."
            );
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException(
                    "Phuong thuc thanh toan khong hop le."
            );
        }

        BigDecimal amount = ticket.getPrice();

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Gia ve khong hop le.");
        }

        boolean success = paymentMethod.pay(amount);

        if (success) {
            ticket.setPaymentStatus(PaymentStatus.SUCCESS);
        } else {
            ticket.setPaymentStatus(PaymentStatus.FAILED);
        }

        boolean updated = ticketRepository.update(ticket);

        if (!updated) {
            throw new IllegalStateException(
                    "Khong the cap nhat trang thai ve."
            );
        }

        return success;
    }
}