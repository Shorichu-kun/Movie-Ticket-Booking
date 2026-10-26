package model;

public class Showtime {
    private String id;
    private String movieId;
    private String roomId;
    private String startTime;
    private String endTime;
    private double price;

    public Showtime() {
    }

    public Showtime(String id, String movieId, String roomId, String startTime, String endTime, double price) {
        this.id = id;
        this.movieId = movieId;
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMovieId() { return movieId; }
    public void setMovieId(String movieId) { this.movieId = movieId; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}