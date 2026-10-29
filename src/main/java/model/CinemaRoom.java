package model;

public class CinemaRoom {

    private String id;
    private String name;
    private int rowCount;
    private int seatsPerRow;

    // Constructor rong de ho tro JSON
    public CinemaRoom() {
    }

    // Constructor day du tham so
    public CinemaRoom(String id, String name,
                      int rowCount, int seatsPerRow) {
        this.id = id;
        this.name = name;
        this.rowCount = rowCount;
        this.seatsPerRow = seatsPerRow;
    }

    // Getter va Setter
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getSeatsPerRow() {
        return seatsPerRow;
    }

    public void setSeatsPerRow(int seatsPerRow) {
        this.seatsPerRow = seatsPerRow;
    }

    // Tinh tong so vi tri ghe
    public int getTotalSeats() {
        return rowCount * seatsPerRow;
    }
}
