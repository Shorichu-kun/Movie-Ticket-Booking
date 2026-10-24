package exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String id) {
        super("Không tìm thấy khách hàng có mã " + id + ".");
    }
}
