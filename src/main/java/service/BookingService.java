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


        BigDecimal basePrice = BigDecimal.valueOf(showtime.getPrice());

        if (basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gia ve phai lon hon 0.");
        }

        if (seatSurcharge == null ||
                seatSurcharge.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Phu thu ghe khong hop le.");
        }

        BigDecimal finalPrice = pricePolicy.calculatePrice(
                basePrice, seatSurcharge
        );

        if (finalPrice == null ||
                finalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gia ve sau khi tinh khong hop le.");
        }

        Ticket existingTicket =
                ticketRepository.findByShowtimeAndSeat(
                        showtime.getId(), seatId
                );

        if (existingTicket != null) {
            throw new IllegalArgumentException(
                    "Ghe nay da duoc dat trong suat chieu."
            );
        }

        Ticket ticket = new Ticket(
                UUID.randomUUID().toString(),
                customer.getId(),
                showtime.getId(),
                seatId,
                finalPrice,
                PaymentStatus.PENDING
        );

        ticketRepository.save(ticket);

        return ticket;
    }
}