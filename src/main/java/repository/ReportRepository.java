package repository;

import model.PaymentStatus;
import model.Ticket;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ReportRepository {
    private final List<Ticket> ticketDatabase; // Gia dinh lay tu DB/File IO

    public ReportRepository(List<Ticket> ticketDatabase) {
        this.ticketDatabase = ticketDatabase;
    }

    // Lay tat ca ve da thanh toan thanh cong
    public List<Ticket> findPaidTickets() {
        return ticketDatabase.stream()
                .filter(ticket -> ticket.getPaymentStatus() != null && 
        "PAID".equalsIgnoreCase(ticket.getPaymentStatus().name()))
                .collect(Collectors.toList());
    }
}