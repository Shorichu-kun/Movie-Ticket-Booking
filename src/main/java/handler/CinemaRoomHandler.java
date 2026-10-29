package handler;

import model.CinemaRoom;
import service.CinemaRoomService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class CinemaRoomHandler {

    private final CinemaRoomService roomService =
            new CinemaRoomService();

    // Lay tat ca phong chieu
    @GetMapping
    public ResponseEntity<List<CinemaRoom>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    // Tim phong chieu theo ID
    @GetMapping("/{id}")
    public ResponseEntity<CinemaRoom> getRoomById(
            @PathVariable String id) {

        CinemaRoom room = roomService.getRoomById(id);

        if (room == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(room);
    }

    // Them phong chieu moi
    @PostMapping
    public ResponseEntity<?> createRoom(
            @RequestBody CinemaRoom room) {

        try {
            CinemaRoom created = roomService.createRoom(room);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(created);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    // Cap nhat thong tin phong chieu
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoom(
            @PathVariable String id,
            @RequestBody CinemaRoom room) {

        if (roomService.getRoomById(id) == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            CinemaRoom updated =
                    roomService.updateRoom(id, room);

            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (IllegalStateException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        }
    }

    // Xoa phong chieu
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoom(
            @PathVariable String id) {

        if (roomService.getRoomById(id) == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            roomService.deleteRoom(id);

            return ResponseEntity.ok(
                    "Xoa phong chieu thanh cong!"
            );

        } catch (IllegalStateException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }
}
