package service;

import repository.ReportRepository;
import model.Ticket;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ReportService {
    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    // 1. Thong ke tong doanh thu theo Phim
    public Map<String, Double> getRevenueByMovie() {
        return reportRepository.findPaidTickets().stream()
                .collect(Collectors.groupingBy(
                        ticket -> ticket.getShowtime().getMovie().getTitle(),
                        Collectors.summingDouble((Ticket ticket) -> ticket.getPrice())
                ));
    }

    // 2. Thong ke tong doanh thu theo Ngay
    public Map<LocalDate, Double> getRevenueByDate() {
        return reportRepository.findPaidTickets().stream()
                .collect(Collectors.groupingBy(
                        Ticket::getBookingDate,
                        Collectors.summingDouble((Ticket ticket) -> ticket.getPrice())
                ));
    }
}