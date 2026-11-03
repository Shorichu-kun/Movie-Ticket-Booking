package utils;

import handler.ApiException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class CommerceInventory {
  private static final Map<Path, Policy> POLICIES = new ConcurrentHashMap<>();

  private CommerceInventory() {}

  public static void register(
      Path directory,
      BiPredicate<String, String> occupied,
      Predicate<String> activeShow,
      Predicate<String> activeSeat) {
    POLICIES.put(
        directory.toAbsolutePath().normalize(), new Policy(occupied, activeShow, activeSeat));
  }

  private static Policy policy() {
    return POLICIES.get(
        JsonFileUtil.resolve("data/tickets.json").toAbsolutePath().normalize().getParent());
  }

  public static boolean isOccupied(String showtimeId, String seatId) {
    Policy policy = policy();
    return policy != null && policy.occupied().test(showtimeId, seatId);
  }

  public static void requireSeatAvailable(String showtimeId, String seatId) {
    if (isOccupied(showtimeId, seatId)) {
      throw new ApiException(409, "Ghế đang được giữ hoặc đã bán trong một đơn hàng.");
    }
  }

  public static void requireShowtimeMutable(String id) {
    Policy policy = policy();
    if (policy != null && policy.activeShow().test(id)) {
      throw new ApiException(
          409, "Suất đang có ghế được giữ hoặc vé chưa sử dụng; không thể sửa/xóa.");
    }
  }

  public static void requireSeatMutable(String id) {
    Policy policy = policy();
    if (policy != null && policy.activeSeat().test(id)) {
      throw new ApiException(
          409, "Ghế đang thuộc lượt giữ hoặc vé chưa sử dụng; không thể sửa/xóa.");
    }
  }

  private record Policy(
      BiPredicate<String, String> occupied,
      Predicate<String> activeShow,
      Predicate<String> activeSeat) {}
}
