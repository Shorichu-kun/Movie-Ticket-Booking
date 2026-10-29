package repository;

import model.Seat;
import utils.JsonFileUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SeatRepository {

    private static final String FILE_PATH = "data/seats.json";

    // Lay tat ca ghe
    public List<Seat> findAll() {
        List<Seat> seats =
                JsonFileUtil.readJsonFromFile(FILE_PATH, Seat.class);

        return seats == null ? new ArrayList<>() : seats;
    }

    // Tim ghe theo ID
    public Seat findById(String id) {
        if (id == null) {
            return null;
        }

        return findAll().stream()
                .filter(s -> id.equalsIgnoreCase(s.getId()))
                .findFirst()
                .orElse(null);
    }

    // Lay danh sach ghe cua mot phong
    public List<Seat> findByRoomId(String roomId) {
        if (roomId == null) {
            return new ArrayList<>();
        }

        return findAll().stream()
                .filter(s -> roomId.equalsIgnoreCase(s.getRoomId()))
                .collect(Collectors.toList());
    }

    // Them ghe moi
    public void save(Seat seat) {
        List<Seat> seats = findAll();
        seats.add(seat);
        JsonFileUtil.writeJsonToFile(FILE_PATH, seats);
    }

    // Cap nhat thong tin ghe
    public void update(Seat updatedSeat) {
        List<Seat> seats = findAll();

        for (int i = 0; i < seats.size(); i++) {
            if (updatedSeat.getId() != null &&
                updatedSeat.getId().equalsIgnoreCase(
                        seats.get(i).getId())) {

                seats.set(i, updatedSeat);
                break;
            }
        }

        JsonFileUtil.writeJsonToFile(FILE_PATH, seats);
    }

    // Xoa ghe theo ID
    public void deleteById(String id) {
        List<Seat> seats = findAll();

        seats.removeIf(s -> id != null &&
                id.equalsIgnoreCase(s.getId()));

        JsonFileUtil.writeJsonToFile(FILE_PATH, seats);
    }
     public void saveAll(List<Seat> seats) {
        JsonFileUtil.writeJsonToFile(FILE_PATH, seats);
    }
}
