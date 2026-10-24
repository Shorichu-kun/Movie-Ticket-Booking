package dto;

public record StudentIdCheckResponse(String studentId, boolean valid, boolean available, String message) {
}
