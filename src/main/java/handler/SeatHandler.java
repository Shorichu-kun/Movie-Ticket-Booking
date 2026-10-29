package handler;

import model.Seat;
import service.SeatService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
@CrossOrigin(origins = "*")
public class SeatHandler {

    private final SeatService seatService =
            new SeatService();

    // Lay danh sach tat ca ghe
    @GetMapping
    public ResponseEntity<List<Seat>> getAllSeats() {
        return ResponseEntity.ok(
                seatService.getAllSeats()
        );
    }

    // Lay danh sach ghe theo phong
    @GetMapping("/room/{roomId}")
    public ResponseEntity<?> getSeatsByRoom(
            @PathVariable String roomId) {

        try {
            return ResponseEntity.ok(
                    seatService.getSeatsByRoom(roomId)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    // Tim ghe theo ma
    @GetMapping("/{id}")
    public ResponseEntity<Seat> getSeatById(
            @PathVariable String id) {

        Seat seat = seatService.getSeatById(id);

        if (seat == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(seat);
    }

    // Tu dong tao ma tran ghe cho phong
    @PostMapping("/room/{roomId}/generate")
    public ResponseEntity<?> generateSeatMatrix(
            @PathVariable String roomId) {

        try {
            List<Seat> seats =
                    seatService.generateSeatMatrix(roomId);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(seats);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (IllegalStateException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        }
    }

    // Cap nhat loai ghe va trang thai ghe
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSeat(
            @PathVariable String id,
            @RequestBody Seat changes) {

        if (seatService.getSeatById(id) == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Seat updated =
                    seatService.updateSeat(id, changes);

            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }
}
