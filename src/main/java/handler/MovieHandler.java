package handler;

import model.Movie;
import service.MovieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

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

    @PostMapping("/poster")
    public ResponseEntity<?> uploadPoster(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File không được để trống"));
        }

        try {
            // 1. Tạo thư mục data/posters nếu chưa có
            Path uploadPath = Paths.get("data/posters").toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 2. Tạo tên file ngẫu nhiên để tránh trùng
            String originalFilename = file.getOriginalFilename();
            String extension = ".jpg";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFileName = UUID.randomUUID().toString() + extension;

            // 3. Ghi file vào thư mục data/posters/
            Path targetLocation = uploadPath.resolve(newFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // 4. Trả về đúng JSON format FE yêu cầu
            return ResponseEntity.ok(Map.of("url", "/posters/" + newFileName));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Không thể lưu poster: " + e.getMessage()));
        }
    }
}