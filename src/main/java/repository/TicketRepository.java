
package repository;

import model.Ticket;
import model.PaymentStatus;
import utils.JsonFileUtil;

import java.util.List;

public class TicketRepository {

    private static final String FILE_PATH = "data/tickets.json";

    public List<Ticket> findAll() {
        List<Ticket> tickets =
                JsonFileUtil.readJsonFromFile(FILE_PATH, Ticket.class);

        return tickets == null ? new java.util.ArrayList<>() : tickets;
    }

    public Ticket findById(String id) {
        if (id == null) {
            return null;
        }

        for (Ticket ticket : findAll()) {
            if (ticket != null && id.equals(ticket.getId())) {
                return ticket;
            }
        }

        return null;
    }

    public Ticket findByShowtimeAndSeat(
            String showtimeId, String seatId) {

        if (showtimeId == null || seatId == null) {
            return null;
        }

        for (Ticket ticket : findAll()) {
            if (ticket == null) {
                continue;
            }

            if (showtimeId.equals(ticket.getShowtimeId())
                    && seatId.equals(ticket.getSeatId())
                    && ticket.getPaymentStatus() != PaymentStatus.FAILED
                    && ticket.getPaymentStatus() != PaymentStatus.REFUNDED) {
                return ticket;
            }
        }

        return null;
    }

    public void save(Ticket ticket) {
        if (ticket == null || ticket.getId() == null) {
            throw new IllegalArgumentException("Ve khong hop le.");
        }

        if (findById(ticket.getId()) != null) {
            throw new IllegalArgumentException("Ma ve da ton tai.");
        }

        List<Ticket> tickets = findAll();
        tickets.add(ticket);

        JsonFileUtil.writeJsonToFile(FILE_PATH, tickets);
    }

    public boolean update(Ticket updatedTicket) {
        if (updatedTicket == null || updatedTicket.getId() == null) {
            return false;
        }

        List<Ticket> tickets = findAll();

        for (int i = 0; i < tickets.size(); i++) {
            Ticket ticket = tickets.get(i);

            if (ticket != null
                    && updatedTicket.getId().equals(ticket.getId())) {

                tickets.set(i, updatedTicket);
                JsonFileUtil.writeJsonToFile(FILE_PATH, tickets);
                return true;
            }
        }

        return false;
    }
}