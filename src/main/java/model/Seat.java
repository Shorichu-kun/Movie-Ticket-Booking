package model;

public class Seat {

    private String id;
    private String roomId;
    private String rowLabel;
    private int seatNumber;
    private SeatType type;
    private SeatStatus status;

    public Seat() {
    }

    // Constructor day du tham so
    public Seat(String id, String roomId,
                String rowLabel, int seatNumber,
                SeatType type, SeatStatus status) {
        this.id = id;
        this.roomId = roomId;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.type = type;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public void setRowLabel(String rowLabel) {
        this.rowLabel = rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(int seatNumber) {
        this.seatNumber = seatNumber;
    }

    public SeatType getType() {
        return type;
    }

    public void setType(SeatType type) {
        this.type = type;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    // Lay ten hien thi cua ghe, vi du A1
    public String getSeatLabel() {
        return rowLabel + seatNumber;
    }
}
