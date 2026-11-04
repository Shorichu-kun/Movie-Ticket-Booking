package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import exception.StorageException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Atomic file replacement; failures propagate instead of reporting false success. */
public class JsonFileUtil {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Path dataDirectory = Path.of("data");

    public static void setDataDirectory(String path) {
        dataDirectory = Path.of(path);
    }

    public static Path resolve(String filePath) {
        Path path = Path.of(filePath);
        return path.getParent() != null && path.getParent().toString().equals("data")
                ? dataDirectory.resolve(path.getFileName())
                : path;
    }

    public static <T> List<T> readJsonFromFile(String filePath, Class<T> clazz) {
        Path path = resolve(filePath);
        if (Files.notExists(path)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<T> data =
                    gson.fromJson(reader, TypeToken.getParameterized(List.class, clazz).getType());
            if (data == null || data.stream().anyMatch(Objects::isNull))
                throw new IOException("Invalid data");
            return data;
        } catch (IOException | RuntimeException e) {
            throw new StorageException("Không thể đọc dữ liệu.", e);
        }
    }

    public static <T> void writeJsonToFile(String filePath, List<T> data) {
        Path target = resolve(filePath).toAbsolutePath(), temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".cinema-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            throw new StorageException("Không thể lưu dữ liệu.", e);
        } finally {
            if (temporary != null)
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
        }
    }
}
