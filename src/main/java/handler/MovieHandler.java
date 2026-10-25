package handler;

import model.Movie;
import service.MovieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@CrossOrigin(origins = "*")
public class MovieHandler {
    private final MovieService movieService = new MovieService();

    @GetMapping
    public ResponseEntity<List<Movie>> getAllMovies() {
        return ResponseEntity.ok(movieService.getAllMovies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Movie> getMovieById(@PathVariable String id) {
        Movie movie = movieService.getMovieById(id);
        if (movie != null) {
            return ResponseEntity.ok(movie);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<String> addMovie(@RequestBody Movie movie) {
        if (movieService.addMovie(movie)) {
            return ResponseEntity.ok("Thêm phim thành công!");
        }
        return ResponseEntity.badRequest().body("Thêm phim thất bại (ID trùng hoặc thiếu dữ liệu)");
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateMovie(@PathVariable String id, @RequestBody Movie movie) {
        movie.setId(id);
        if (movieService.updateMovie(movie)) {
            return ResponseEntity.ok("Cập nhật thành công!");
        }
        return ResponseEntity.badRequest().body("Cập nhật thất bại!");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMovie(@PathVariable String id) {
        if (movieService.deleteMovie(id)) {
            return ResponseEntity.ok("Xóa phim thành công!");
        }
        return ResponseEntity.badRequest().body("Xóa phim thất bại!");
    }
}