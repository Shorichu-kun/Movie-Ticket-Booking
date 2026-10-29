package repository;

import model.Showtime;
import utils.JsonFileUtil;

import java.util.List;

public class ShowtimeRepository {
    private final String FILE_PATH = "data/showtimes.json";

    public List<Showtime> findAll() {
        return JsonFileUtil.readJsonFromFile(FILE_PATH, Showtime.class);
    }

    public Showtime findById(String id) {
        return findAll().stream()
                .filter(s -> s.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public void save(Showtime showtime) {
        List<Showtime> list = findAll();
        list.add(showtime);
        JsonFileUtil.writeJsonToFile(FILE_PATH, list);
    }

    public void deleteById(String id) {
        List<Showtime> list = findAll();
        list.removeIf(s -> s.getId().equalsIgnoreCase(id));
        JsonFileUtil.writeJsonToFile(FILE_PATH, list);
    }
}

