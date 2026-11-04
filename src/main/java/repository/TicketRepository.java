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
            List<Ticket> all = JsonFileUtil.readJsonFromFile(FILE_PATH, Ticket.class);
            for (Ticket ticket : all) {
                if (expiredReservation(ticket)) ticket.setPaymentStatus(PaymentStatus.FAILED);
            }
            return all;
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
                && t.getPaymentStatus() != PaymentStatus.REFUNDED && !expiredReservation(t);
    }

    private static boolean expiredReservation(Ticket ticket) {
        if (ticket.getPaymentStatus() != PaymentStatus.PENDING || ticket.getReservationExpiresAt() == null) return false;
        try {
            return !java.time.Instant.now().isBefore(java.time.Instant.parse(ticket.getReservationExpiresAt()));
        } catch (java.time.DateTimeException invalid) {
            return true;
        }
    }

    public void migrateReservations() {
        synchronized (LOCK) {
            List<Ticket> all = findAll();
            boolean missing = all.stream().anyMatch(t -> t.getPaymentStatus() == PaymentStatus.PENDING
                    && t.getReservationExpiresAt() == null);
            if (!missing) return;
            java.nio.file.Path source = JsonFileUtil.resolve(FILE_PATH).toAbsolutePath();
            try {
                java.nio.file.Path backup = source.getParent().resolve("migration-backups");
                java.nio.file.Files.createDirectories(backup);
                java.nio.file.Files.copy(source, backup.resolve("tickets-" + java.util.UUID.randomUUID() + ".json"));
            } catch (java.io.IOException failure) {
                throw new exception.StorageException("Không thể sao lưu vé trước khi chuyển đổi.", failure);
            }
            String expiry = java.time.Instant.now().plusSeconds(900).toString();
            all.stream().filter(t -> t.getPaymentStatus() == PaymentStatus.PENDING
                    && t.getReservationExpiresAt() == null).forEach(t -> t.setReservationExpiresAt(expiry));
            JsonFileUtil.writeJsonToFile(FILE_PATH, all);
        }
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
                if (active(t)) utils.CommerceInventory.requireSeatAvailable(t.getShowtimeId(), t.getSeatId());
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
                    if (active(updated)) utils.CommerceInventory.requireSeatAvailable(updated.getShowtimeId(), updated.getSeatId());
                    tickets.set(i, updated);
                    JsonFileUtil.writeJsonToFile(FILE_PATH, tickets);
                    return true;
                }
            return false;
        }
    }
}
