package service;

import model.CinemaRoom;
import repository.CinemaRoomRepository;
import repository.SeatRepository;

import java.util.List;

public class CinemaRoomService {

    private final CinemaRoomRepository roomRepository;
    private final SeatRepository seatRepository;

    public CinemaRoomService() {
        this.roomRepository = new CinemaRoomRepository();
        this.seatRepository = new SeatRepository();
    }

    // Lay danh sach phong chieu
    public List<CinemaRoom> getAllRooms() {
        return roomRepository.findAll();
    }

    // Tim phong theo ma
    public CinemaRoom getRoomById(String id) {
        return roomRepository.findById(id);
    }

    // Them phong chieu
    public CinemaRoom createRoom(CinemaRoom room) {
        validateRoom(room);

        room.setId(room.getId().trim());
        room.setName(room.getName().trim());

        if (roomRepository.findById(room.getId()) != null) {
            throw new IllegalArgumentException(
                "Ma phong da ton tai!"
            );
        }

        roomRepository.save(room);
        return room;
    }

    // Cap nhat phong chieu
    public CinemaRoom updateRoom(
            String id, CinemaRoom updatedRoom) {

        CinemaRoom existingRoom = roomRepository.findById(id);

        if (existingRoom == null) {
            throw new IllegalArgumentException(
                "Khong tim thay phong chieu!"
            );
        }

        if (updatedRoom == null) {
            throw new IllegalArgumentException(
                "Thong tin phong khong hop le!"
            );
        }

        // Khong cho phep doi ma phong
        updatedRoom.setId(existingRoom.getId());
        validateRoom(updatedRoom);
        updatedRoom.setName(updatedRoom.getName().trim());

        // Khong doi kich thuoc khi phong da co ghe
        boolean dimensionChanged =
                existingRoom.getRowCount() != updatedRoom.getRowCount()
                || existingRoom.getSeatsPerRow()
                   != updatedRoom.getSeatsPerRow();

        if (dimensionChanged &&
                !seatRepository.findByRoomId(id).isEmpty()) {
            throw new IllegalStateException(
                "Phong da co ghe. Khong the doi kich thuoc!"
            );
        }

        roomRepository.update(updatedRoom);
        return updatedRoom;
    }

    // Xoa phong chieu
    public void deleteRoom(String id) {
        CinemaRoom room = roomRepository.findById(id);

        if (room == null) {
            throw new IllegalArgumentException(
                "Khong tim thay phong chieu!"
            );
        }

        if (!seatRepository.findByRoomId(id).isEmpty()) {
            throw new IllegalStateException(
                "Phong dang co ghe. Khong the xoa!"
            );
        }

        roomRepository.deleteById(id);
    }

    // Kiem tra du lieu dau vao
    private void validateRoom(CinemaRoom room) {

        if (room == null) {
            throw new IllegalArgumentException(
                "Thong tin phong khong duoc rong!"
            );
        }

        if (room.getId() == null ||
                room.getId().isBlank()) {
            throw new IllegalArgumentException(
                "Ma phong khong duoc rong!"
            );
        }

        if (room.getName() == null ||
                room.getName().isBlank()) {
            throw new IllegalArgumentException(
                "Ten phong khong duoc rong!"
            );
        }

        if (room.getRowCount() < 1 ||
                room.getRowCount() > 26) {
            throw new IllegalArgumentException(
                "So hang ghe phai tu 1 den 26!"
            );
        }

        if (room.getSeatsPerRow() < 1 ||
                room.getSeatsPerRow() > 50) {
            throw new IllegalArgumentException(
                "So ghe moi hang phai tu 1 den 50!"
            );
        }
    }
}
