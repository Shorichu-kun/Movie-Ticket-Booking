package exception;

public class CustomerStorageException extends RuntimeException {
    public CustomerStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
