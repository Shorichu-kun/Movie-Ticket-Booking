package service;

import model.Movie;
import repository.MovieRepository;

import java.util.List;

public class MovieService {
    private final MovieRepository movieRepository = new MovieRepository();

    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

    public Movie getMovieById(String id) {
        return movieRepository.findById(id);
    }

    public boolean addMovie(Movie movie) {
        if (movie.getId() == null || movie.getTitle() == null) {
            return false;
        }
        if (movieRepository.findById(movie.getId()) != null) {
            return false; // Đã tồn tại trùng ID
        }
        movieRepository.save(movie);
        return true;
    }

    public boolean updateMovie(Movie movie) {
        if (movieRepository.findById(movie.getId()) == null) {
            return false; // Không tìm thấy để update
        }
        movieRepository.update(movie);
        return true;
    }

    public boolean deleteMovie(String id) {
        if (movieRepository.findById(id) == null) {
            return false;
        }
        movieRepository.deleteById(id);
        return true;
    }
}