package repository;

import model.Movie;
import utils.JsonFileUtil;

import java.util.ArrayList;
import java.util.List;

public class MovieRepository {
    private final String FILE_PATH = "data/movies.json";

    public List<Movie> findAll() {
        return JsonFileUtil.readJsonFromFile(FILE_PATH, Movie.class);
    }

    public Movie findById(String id) {
        return findAll().stream()
                .filter(m -> m.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public void save(Movie movie) {
        List<Movie> movies = findAll();
        movies.add(movie);
        JsonFileUtil.writeJsonToFile(FILE_PATH, movies);
    }

    public void update(Movie updatedMovie) {
        List<Movie> movies = findAll();
        for (int i = 0; i < movies.size(); i++) {
            if (movies.get(i).getId().equalsIgnoreCase(updatedMovie.getId())) {
                movies.set(i, updatedMovie);
                break;
            }
        }
        JsonFileUtil.writeJsonToFile(FILE_PATH, movies);
    }

    public void deleteById(String id) {
        List<Movie> movies = findAll();
        movies.removeIf(m -> m.getId().equalsIgnoreCase(id));
        JsonFileUtil.writeJsonToFile(FILE_PATH, movies);
    }
}