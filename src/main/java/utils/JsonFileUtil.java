package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class JsonFileUtil {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static <T> List<T> readJsonFromFile(String filePath, Class<T> clazz) {
        File file = new File(filePath);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(file)) {
            Type typeOfT = TypeToken.getParameterized(List.class, clazz).getType();
            List<T> data = gson.fromJson(reader, typeOfT);
            return data != null ? data : new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static <T> void writeJsonToFile(String filePath, List<T> data) {
        File file = new File(filePath);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}