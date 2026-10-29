package handler;

import model.Showtime;
import service.ShowtimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/showtimes")
@CrossOrigin(origins = "*")
public class ShowtimeHandler {
    private final ShowtimeService showtimeService = new ShowtimeService();

    @GetMapping
    public ResponseEntity<List<Showtime>> getShowtimes(
            @RequestParam(required = false) String movieId,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(showtimeService.getShowtimes(movieId, date));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Showtime> getShowtimeById(@PathVariable String id) {
        Showtime s = showtimeService.getShowtimeById(id);
        if (s != null) {
            return ResponseEntity.ok(s);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<String> addShowtime(@RequestBody Showtime showtime) {
        String error = showtimeService.addShowtime(showtime);
        if (error == null) {
            return ResponseEntity.ok("Thêm suất chiếu thành công!");
        }
        return ResponseEntity.badRequest().body(error);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteShowtime(@PathVariable String id) {
        if (showtimeService.deleteShowtime(id)) {
            return ResponseEntity.ok("Xóa suất chiếu thành công!");
        }
        return ResponseEntity.badRequest().body("Xóa suất chiếu thất bại!");
    }
}
