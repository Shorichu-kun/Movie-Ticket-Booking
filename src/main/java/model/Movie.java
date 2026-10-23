package model;

public class Movie {
    private String id;
    private String title;
    private int duration;
    private String genre;
    private MovieStatus status;


    public Movie() {
    }


    public Movie(String id, String title, int duration, String genre, MovieStatus status) {
        this.id = id;
        this.title = title;
        this.duration = duration;
        this.genre = genre;
        this.status = status;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public MovieStatus getStatus() {
        return status;
    }

    public void setStatus(MovieStatus status) {
        this.status = status;
    }
}