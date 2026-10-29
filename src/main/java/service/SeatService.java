package service;

import model.CinemaRoom;
import model.Seat;
import model.SeatType;
import model.SeatStatus;

import repository.CinemaRoomRepository;
import repository.SeatRepository;

import java.util.ArrayList;
import java.util.List;

public class SeatService {

    private final SeatRepository seatRepository;
    private final CinemaRoomRepository roomRepository;

    public SeatService() {
        this.seatRepository = new SeatRepository();
        this.roomRepository = new CinemaRoomRepository();
    }

    // Lay tat ca ghe
    public List<Seat> getAllSeats() {
        return seatRepository.findAll();
    }

    // Lay ghe theo ma
    public Seat getSeatById(String id) {
        return seatRepository.findById(id);
    }

    // Lay danh sach ghe cua mot phong
    public List<Seat> getSeatsByRoom(String roomId) {
        requireRoom(roomId);
        return seatRepository.findByRoomId(roomId);
    }

    // Kiem tra phong ton tai
    private CinemaRoom requireRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException(
                "Ma phong khong hop le!"
            );
        }

        CinemaRoom room = roomRepository.findById(roomId);

        if (room == null) {
            throw new IllegalArgumentException(
                "Khong tim thay phong chieu!"
            );
        }

        return room;
    }

    // Tao tu dong ma tran ghe
    public List<Seat> generateSeatMatrix(String roomId) {

        CinemaRoom room = requireRoom(roomId);

        if (room.getRowCount() < 1 ||
            room.getRowCount() > 26 ||
            room.getSeatsPerRow() < 1 ||
            room.getSeatsPerRow() > 50) {
            throw new IllegalArgumentException(
                "Kich thuoc phong khong hop le!"
            );
        }

        if (!seatRepository.findByRoomId(room.getId()).isEmpty()) {
            throw new IllegalStateException(
                "Phong da co ghe, khong duoc tao trung!"
            );
        }

        List<Seat> allSeats =
                new ArrayList<>(seatRepository.findAll());

        List<Seat> newSeats = new ArrayList<>();

        for (int row = 0; row < room.getRowCount(); row++) {

            String rowLabel =
                    String.valueOf((char) ('A' + row));

            for (int number = 1;
                 number <= room.getSeatsPerRow();
                 number++) {

                String seatId =
                        room.getId() + "-" + rowLabel + number;

                Seat seat = new Seat(
                        seatId,
                        room.getId(),
                        rowLabel,
                        number,
                        SeatType.NORMAL,
                        SeatStatus.ACTIVE
                );

                newSeats.add(seat);
            }
        }

        allSeats.addAll(newSeats);
        seatRepository.saveAll(allSeats);

        return newSeats;
    }

    // Them mot ghe tai vi tri con trong
    public Seat createSeat(Seat seat) {
        if (seat == null) {
            throw new IllegalArgumentException(
                "Thong tin ghe khong hop le!"
            );
        }

        CinemaRoom room = requireRoom(seat.getRoomId());

        String row = seat.getRowLabel();

        if (row == null || row.isBlank()) {
            throw new IllegalArgumentException(
                "Hang ghe khong duoc rong!"
            );
        }

        row = row.trim().toUpperCase();

        if (row.length() != 1 ||
            row.charAt(0) < 'A' ||
            row.charAt(0) >= 'A' + room.getRowCount() ||
            seat.getSeatNumber() < 1 ||
            seat.getSeatNumber() > room.getSeatsPerRow()) {
            throw new IllegalArgumentException(
                "Vi tri ghe nam ngoai so do phong!"
            );
        }

        String seatId = room.getId() + "-"
                + row + seat.getSeatNumber();

        if (seatRepository.findById(seatId) != null) {
            throw new IllegalArgumentException(
                "Vi tri ghe da ton tai!"
            );
        }

        seat.setId(seatId);
        seat.setRoomId(room.getId());
        seat.setRowLabel(row);

        if (seat.getType() == null) {
            seat.setType(SeatType.NORMAL);
        }

        if (seat.getStatus() == null) {
            seat.setStatus(SeatStatus.ACTIVE);
        }

        seatRepository.save(seat);
        return seat;
    }

    // Cap nhat loai ghe va trang thai
    public Seat updateSeat(String id, Seat changes) {

        Seat existing = seatRepository.findById(id);

        if (existing == null) {
            throw new IllegalArgumentException(
                "Khong tim thay ghe!"
            );
        }

        if (changes == null) {
            throw new IllegalArgumentException(
                "Thong tin cap nhat khong hop le!"
            );
        }

        if (changes.getType() != null) {
            existing.setType(changes.getType());
        }

        if (changes.getStatus() != null) {
            existing.setStatus(changes.getStatus());
        }

        // Giu nguyen ma ghe va vi tri
        seatRepository.update(existing);

        return existing;
    }

    // Ngung su dung ghe thay vi xoa du lieu
    public Seat disableSeat(String id) {

        Seat existing = seatRepository.findById(id);

        if (existing == null) {
            throw new IllegalArgumentException(
                "Khong tim thay ghe!"
            );
        }

        existing.setStatus(SeatStatus.DISABLED);
        seatRepository.update(existing);

        return existing;
    }
}