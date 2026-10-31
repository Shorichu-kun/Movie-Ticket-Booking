package service;

import model.Movie;
import model.Showtime;
import model.Ticket;
import repository.MovieRepository;
import repository.ReportRepository;
import repository.ShowtimeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportService {
    private final ReportRepository reportRepository;
    private final ShowtimeRepository showtimeRepository = new ShowtimeRepository();
    private final MovieRepository movieRepository = new MovieRepository();

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    // 1. Thống kê tổng doanh thu theo Phim
    public Map<String, BigDecimal> getRevenueByMovie() {
        List<Ticket> paidTickets = reportRepository.findPaidTickets();

        return paidTickets.stream().collect(Collectors.groupingBy(
                ticket -> {
                    Showtime showtime = showtimeRepository.findById(ticket.getShowtimeId());
                    if (showtime != null) {
                        Movie movie = movieRepository.findById(showtime.getMovieId());
                        if (movie != null) {
                            return movie.getTitle();
                        }
                    }
                    return "Không xác định";
                },
                Collectors.reducing(
                        BigDecimal.ZERO,
                        ticket -> ticket.getPrice() != null ? ticket.getPrice() : BigDecimal.ZERO,
                        BigDecimal::add
                )
        ));
    }

    // 2. Thống kê tổng doanh thu theo Ngày
    public Map<LocalDate, BigDecimal> getRevenueByDate() {
        List<Ticket> paidTickets = reportRepository.findPaidTickets();

        return paidTickets.stream().collect(Collectors.groupingBy(
                ticket -> {
                    Showtime showtime = showtimeRepository.findById(ticket.getShowtimeId());
                    if (showtime != null && showtime.getStartTime() != null && !showtime.getStartTime().isEmpty()) {
                        String startTimeStr = showtime.getStartTime();
                        // Lấy 10 ký tự đầu đại diện cho YYYY-MM-DD
                        if (startTimeStr.length() >= 10) {
                            return LocalDate.parse(startTimeStr.substring(0, 10));
                        }
                    }
                    return LocalDate.now();
                },
                Collectors.reducing(
                        BigDecimal.ZERO,
                        ticket -> ticket.getPrice() != null ? ticket.getPrice() : BigDecimal.ZERO,
                        BigDecimal::add
                )
        ));
    }
}