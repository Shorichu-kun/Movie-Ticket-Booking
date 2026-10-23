package utils;

import java.util.Locale;
import java.util.UUID;

public final class IdGenerator {
    private IdGenerator() {
    }

    public static String generateCustomerId() {
        return "CUS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }
}
