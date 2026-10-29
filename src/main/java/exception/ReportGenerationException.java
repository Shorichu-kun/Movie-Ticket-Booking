package exception;

/**
 * Ngoai le tuy chinh khi xay ra loi trong qua trinh tong hop bao cao
 */
public class ReportGenerationException extends Exception {
    public ReportGenerationException(String message) {
        super(message);
    }

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}