package repository;

import handler.ApiException;

import model.*;

import utils.JsonFileUtil;

import java.util.*;

/** All repository instances share one lock, including the legacy booking/payment services. */
public class TicketRepository {
    public static final Object LOCK = new Object();
    private static final String FILE_PATH = "data/tickets.json";

    public List<Ticket> findAll() {
        synchronized (LOCK) {
            return JsonFileUtil.readJsonFromFile(FILE_PATH, Ticket.class);
        }
    }

    public Ticket findById(String id) {
        synchronized (LOCK) {
            return findAll().stream()
                    .filter(t -> Objects.equals(id, t.getId()))
                    .findFirst()
                    .orElse(null);
        }
    }

    public static boolean active(Ticket t) {
        return t.getPaymentStatus() != PaymentStatus.FAILED
                && t.getPaymentStatus() != PaymentStatus.REFUNDED;
    }

    public Ticket findByShowtimeAndSeat(String showtimeId, String seatId) {
        synchronized (LOCK) {
            return findAll().stream()
                    .filter(
                            t ->
                                    Objects.equals(showtimeId, t.getShowtimeId())
                                            && Objects.equals(seatId, t.getSeatId())
                                            && active(t))
                    .findFirst()
                    .orElse(null);
        }
    }

    public void save(Ticket ticket) {
        saveAll(List.of(ticket));
    }

    public void saveAll(List<Ticket> additions) {
        synchronized (LOCK) {
            List<Ticket> tickets = findAll();
            for (Ticket t : additions) {
                if (t == null || t.getId() == null)
                    throw new IllegalArgumentException("Vé không hợp lệ.");
                if (tickets.stream().anyMatch(e -> e.getId().equals(t.getId())))
                    throw new ApiException(409, "Mã vé đã tồn tại.");
                if (active(t)
                        && tickets.stream()
                                .anyMatch(
                                        e ->
                                                active(e)
                                                        && Objects.equals(
                                                                e.getShowtimeId(),
                                                                t.getShowtimeId())
                                                        && Objects.equals(
                                                                e.getSeatId(), t.getSeatId())))
                    throw new ApiException(409, "Ghế đã được đặt trong suất chiếu.");
                tickets.add(t);
            }
            JsonFileUtil.writeJsonToFile(FILE_PATH, tickets);
        }
    }

    public boolean update(Ticket updated) {
        synchronized (LOCK) {
            if (updated == null || updated.getId() == null) return false;
            List<Ticket> tickets = findAll();
            for (int i = 0; i < tickets.size(); i++)
                if (updated.getId().equals(tickets.get(i).getId())) {
                    tickets.set(i, updated);
                    JsonFileUtil.writeJsonToFile(FILE_PATH, tickets);
                    return true;
                }
            return false;
        }
    }
}
