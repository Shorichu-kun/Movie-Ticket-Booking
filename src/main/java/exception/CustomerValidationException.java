package exception;

public class CustomerValidationException extends RuntimeException {
    private final String field;

    public CustomerValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
