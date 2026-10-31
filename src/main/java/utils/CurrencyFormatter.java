package utils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * CurrencyFormatter
 */
public class CurrencyFormatter {
    private static final Locale VIETNAM_LOCALE = new Locale("vi", "VN");
    public static String formatVND(double amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(VIETNAM_LOCALE);
        return formatter.format(amount);
    }
}