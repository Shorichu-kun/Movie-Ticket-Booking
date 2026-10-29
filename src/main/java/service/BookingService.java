
package service;

import model.Customer;
import model.Showtime;
import model.Ticket;
import model.PaymentStatus;
import repository.TicketRepository;
import policy.TicketPricePolicy;

import java.math.BigDecimal;
import java.util.UUID;

public class BookingService {

    private TicketRepository ticketRepository;

    public BookingService() {
        ticketRepository = new TicketRepository();
    }

    public Ticket bookTicket(
            Customer customer,
            Showtime showtime,
            String seatId,
            BigDecimal seatSurcharge,
            TicketPricePolicy pricePolicy) {

        // 1. Kiem tra thong tin
        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException("Khach hang khong hop le.");
        }

        if (showtime == null || showtime.getId() == null) {
            throw new IllegalArgumentException("Suat chieu khong hop le.");
        }

        if (seatId == null || seatId.trim().isEmpty()) {
            throw new IllegalArgumentException("Ma ghe khong hop le.");
        }

        if (pricePolicy == null) {
            throw new IllegalArgumentException("Chinh sach gia ve khong hop le.");
        }

        // 2. Kiem tra gia ve
        BigDecimal basePrice = BigDecimal.valueOf(showtime.getPrice());

        // 3. Tinh gia ve
        BigDecimal finalPrice = pricePolicy.calculatePrice(
                basePrice, seatSurcharge
        );

        // 4. Kiem tra ghe da duoc dat chua
        Ticket existingTicket =
                ticketRepository.findByShowtimeAndSeat(
                        showtime.getId(), seatId
                );

        if (existingTicket != null) {
            throw new IllegalArgumentException(
                    "Ghe nay da duoc dat trong suat chieu."
            );
        }

        // 5. Tao ve moi
        Ticket ticket = new Ticket(
                UUID.randomUUID().toString(),
                customer.getId(),
                showtime.getId(),
                seatId,
                finalPrice,
                PaymentStatus.PENDING
        );

        // 6. Luu ve
        ticketRepository.save(ticket);

        return ticket;
    }
}
