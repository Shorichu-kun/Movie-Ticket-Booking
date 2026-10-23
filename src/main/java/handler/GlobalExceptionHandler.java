package handler;

import exception.CustomerConflictException;
import exception.CustomerNotFoundException;
import exception.CustomerStorageException;
import exception.CustomerValidationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = CustomerHandler.class)
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(CustomerNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), Map.of());
    }

    @ExceptionHandler(CustomerConflictException.class)
    public ResponseEntity<ApiError> handleConflict(CustomerConflictException e) {
        return error(HttpStatus.CONFLICT, e.getMessage(), Map.of(e.getField(), e.getMessage()));
    }

    @ExceptionHandler(CustomerValidationException.class)
    public ResponseEntity<ApiError> handleCustomerValidation(CustomerValidationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), Map.of(e.getField(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(),
                field.getDefaultMessage() == null ? "Dữ liệu không hợp lệ." : field.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Vui lòng kiểm tra thông tin đã nhập.", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(violation ->
                fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return error(HttpStatus.BAD_REQUEST, "Vui lòng kiểm tra thông tin đã nhập.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody() {
        return error(HttpStatus.BAD_REQUEST,
                "Dữ liệu JSON không hợp lệ. Loại khách hàng phải là NORMAL, STUDENT hoặc VIP.", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "Tham số " + e.getName() + " không hợp lệ.";
        return error(HttpStatus.BAD_REQUEST, message, Map.of(e.getName(), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException e) {
        String message = "Thiếu tham số " + e.getParameterName() + ".";
        return error(HttpStatus.BAD_REQUEST, message, Map.of(e.getParameterName(), message));
    }

    @ExceptionHandler(CustomerStorageException.class)
    public ResponseEntity<ApiError> handleStorage(CustomerStorageException e) {
        LOGGER.error("Customer storage operation failed", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể đọc hoặc lưu dữ liệu khách hàng. Vui lòng kiểm tra nơi lưu trữ và thử lại.", Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, fieldErrors, Instant.now()));
    }

    public record ApiError(int status, String message, Map<String, String> fieldErrors, Instant timestamp) {
    }
}
