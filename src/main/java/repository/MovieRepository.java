package repository;

import java.util.List;
import model.Movie;
import utils.JsonFileUtil;

public class MovieRepository {
  private final String FILE_PATH = "data/movies.json";

  public List<Movie> findAll() {
    return JsonFileUtil.readJsonFromFile(FILE_PATH, Movie.class);
  }

  public Movie findById(String id) {
    return findAll().stream().filter(m -> m.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
  }

  public void save(Movie movie) {
    List<Movie> movies = findAll();
    movies.add(movie);
    JsonFileUtil.writeJsonToFile(FILE_PATH, movies);
  }

  public void update(Movie updatedMovie) {
    synchronized (TicketRepository.LOCK) {
      Movie existing = findById(updatedMovie.getId());
      if (existing != null) updatedMovie.setId(existing.getId());
      requireLinkedShowsMutable(updatedMovie.getId());
      List<Movie> movies = findAll();
      for (int i = 0; i < movies.size(); i++) {
        if (movies.get(i).getId().equalsIgnoreCase(updatedMovie.getId())) {
          movies.set(i, updatedMovie);
          break;
        }
      }
      JsonFileUtil.writeJsonToFile(FILE_PATH, movies);
    }
  }

  public void deleteById(String id) {
    synchronized (TicketRepository.LOCK) {
      Movie existing = findById(id);
      String canonical = existing == null ? id : existing.getId();
      requireLinkedShowsMutable(canonical);
      List<Movie> movies = findAll();
      movies.removeIf(m -> m.getId().equalsIgnoreCase(id));
      JsonFileUtil.writeJsonToFile(FILE_PATH, movies);
    }
  }

  private void requireLinkedShowsMutable(String movieId) {
    new ShowtimeRepository()
        .findAll().stream()
            .filter(
                show -> show.getMovieId() != null && show.getMovieId().equalsIgnoreCase(movieId))
            .forEach(show -> utils.CommerceInventory.requireShowtimeMutable(show.getId()));
  }
}
