package exception;

public class CustomerConflictException extends RuntimeException {
    private final String field;

    public CustomerConflictException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
