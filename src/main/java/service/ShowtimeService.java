package service;
import model.Movie;
import model.MovieStatus;
import model.Showtime;
import repository.MovieRepository;
import repository.ShowtimeRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class ShowtimeService {
    private final ShowtimeRepository showtimeRepository = new ShowtimeRepository();
    private final MovieRepository movieRepository = new MovieRepository();

    // Lấy danh sách suất chiếu
    public List<Showtime> getShowtimes(String movieId, String date) {
        List<Showtime> result = new ArrayList<>();
        for (Showtime s : showtimeRepository.findAll()) {
            if (movieId != null && !movieId.isEmpty() && !s.getMovieId().equalsIgnoreCase(movieId)) {
                continue;
            }
            if (date != null && !date.isEmpty() && !s.getStartTime().startsWith(date)) {
                continue;
            }
            result.add(s);
        }
        return result;
    }

    public Showtime getShowtimeById(String id) {
        return showtimeRepository.findById(id);
    }

    // Thêm suất chiếu.
    public String addShowtime(Showtime s) {
        if (s.getId() == null || s.getId().isEmpty()
                || s.getMovieId() == null || s.getRoomId() == null || s.getStartTime() == null) {
            return "Thiếu dữ liệu bắt buộc";
        }
        if (s.getPrice() <= 0) {
            return "Giá vé phải lớn hơn 0";
        }
        if (showtimeRepository.findById(s.getId()) != null) {
            return "Mã suất chiếu đã tồn tại";
        }

        Movie movie = movieRepository.findById(s.getMovieId());
        if (movie == null) {
            return "Phim không tồn tại";
        }
        if (movie.getStatus() == MovieStatus.ENDED) {
            return "Phim đã ngừng chiếu, không thể tạo suất chiếu";
        }

        LocalDateTime start;
        try {
            start = LocalDateTime.parse(s.getStartTime());
        } catch (DateTimeParseException e) {
            return "Sai định dạng thời gian bắt đầu";
        }
        LocalDateTime end = start.plusMinutes(movie.getDuration());
        s.setEndTime(end.toString());

        // Kiểm tra trùng lịch
        for (Showtime other : showtimeRepository.findAll()) {
            if (!other.getRoomId().equalsIgnoreCase(s.getRoomId())) {
                continue;
            }
            LocalDateTime oStart = LocalDateTime.parse(other.getStartTime());
            LocalDateTime oEnd = LocalDateTime.parse(other.getEndTime());
            if (start.isBefore(oEnd) && end.isAfter(oStart)) {
                return "Trùng lịch chiếu với suất " + other.getId() + " trong phòng " + other.getRoomId();
            }
        }

        showtimeRepository.save(s);
        return null;
    }

    public boolean deleteShowtime(String id) {
        if (showtimeRepository.findById(id) == null) {
            return false;
        }
        showtimeRepository.deleteById(id);
        return true;
    }
}
