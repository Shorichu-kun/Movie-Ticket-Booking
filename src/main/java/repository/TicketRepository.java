
package repository;

import model.Ticket;
import model.PaymentStatus;
import utils.JsonFileUtil;

import java.util.ArrayList;
import java.util.List;

public class TicketRepository {

    private static final String FILE_PATH = "data/tickets.json";

    public List<Ticket> findAll() {
        return JsonFileUtil.readJsonFromFile(
                FILE_PATH, Ticket.class
        );
    }

    public Ticket findById(String id) {
        for (Ticket ticket : findAll()) {
            if (ticket.getId() != null
                    && ticket.getId().equals(id)) {
                return ticket;
            }
        }
        return null;
    }

    public Ticket findByShowtimeAndSeat(
            String showtimeId, String seatId) {

        for (Ticket ticket : findAll()) {
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
        List<Ticket> tickets = findAll();
        tickets.add(ticket);
        JsonFileUtil.writeJsonToFile(FILE_PATH, tickets);
    }

    public boolean update(Ticket updatedTicket) {
        List<Ticket> tickets = findAll();

        for (int i = 0; i < tickets.size(); i++) {
            if (tickets.get(i).getId() != null
                    && tickets.get(i).getId()
                    .equals(updatedTicket.getId())) {

                tickets.set(i, updatedTicket);
                JsonFileUtil.writeJsonToFile(FILE_PATH, tickets);
                return true;
            }
        }

        return false;
    }
}
