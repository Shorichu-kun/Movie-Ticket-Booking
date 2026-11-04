package repository;

import java.util.ArrayList;
import java.util.List;
import model.CinemaRoom;
import utils.JsonFileUtil;

public class CinemaRoomRepository {

  private static final String FILE_PATH = "data/cinemaRooms.json";

  // Lay danh sach tat ca phong chieu
  public List<CinemaRoom> findAll() {
    List<CinemaRoom> rooms = JsonFileUtil.readJsonFromFile(FILE_PATH, CinemaRoom.class);

    return rooms == null ? new ArrayList<>() : rooms;
  }

  // Tim phong theo ID
  public CinemaRoom findById(String id) {
    if (id == null) {
      return null;
    }

    return findAll().stream().filter(r -> id.equalsIgnoreCase(r.getId())).findFirst().orElse(null);
  }

  // Them phong chieu
  public void save(CinemaRoom room) {
    List<CinemaRoom> rooms = findAll();
    rooms.add(room);
    JsonFileUtil.writeJsonToFile(FILE_PATH, rooms);
  }

  // Cap nhat phong chieu
  public void update(CinemaRoom updatedRoom) {
    synchronized (TicketRepository.LOCK) {
      CinemaRoom existing = findById(updatedRoom.getId());
      if (existing != null) updatedRoom.setId(existing.getId());
      requireLinkedInventoryMutable(updatedRoom.getId());
      List<CinemaRoom> rooms = findAll();

      for (int i = 0; i < rooms.size(); i++) {
        if (updatedRoom.getId() != null
            && updatedRoom.getId().equalsIgnoreCase(rooms.get(i).getId())) {

          rooms.set(i, updatedRoom);
          break;
        }
      }

      JsonFileUtil.writeJsonToFile(FILE_PATH, rooms);
    }
  }

  // Xoa phong theo ID
  public void deleteById(String id) {
    synchronized (TicketRepository.LOCK) {
      CinemaRoom existing = findById(id);
      requireLinkedInventoryMutable(existing == null ? id : existing.getId());
      List<CinemaRoom> rooms = findAll();

      rooms.removeIf(r -> id != null && id.equalsIgnoreCase(r.getId()));

      JsonFileUtil.writeJsonToFile(FILE_PATH, rooms);
    }
  }

  private void requireLinkedInventoryMutable(String roomId) {
    new SeatRepository()
        .findByRoomId(roomId)
        .forEach(seat -> utils.CommerceInventory.requireSeatMutable(seat.getId()));
    new ShowtimeRepository()
        .findAll().stream()
            .filter(show -> show.getRoomId() != null && show.getRoomId().equalsIgnoreCase(roomId))
            .forEach(show -> utils.CommerceInventory.requireShowtimeMutable(show.getId()));
  }
}
