import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;

@SpringBootTest(classes = MovieTicketBookingApplication.class)
@AutoConfigureMockMvc
class CommerceApiIntegrationTest {
  @TempDir static Path directory;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("customers.storage-path", () -> directory.resolve("customers.json").toString());
    r.add("accounts.storage-path", () -> directory.resolve("accounts.json").toString());
    r.add("cinema.data-dir", () -> directory.toString());
    r.add("admin.email", () -> "admin@integration.test");
    r.add("admin.password", () -> "Admin123!");
  }

  @BeforeEach
  void fixtures() throws Exception {
    utils.JsonFileUtil.setDataDirectory(directory.toString());
    Files.writeString(
        directory.resolve("movies.json"),
        "[{\"id\":\"M1\",\"title\":\"Demo Film\",\"duration\":90,\"status\":\"NOW_SHOWING\"}]");
    Files.writeString(
        directory.resolve("showtimes.json"),
        "[{\"id\":\"ST1\",\"movieId\":\"M1\",\"roomId\":\"R1\",\"startTime\":\"2099-11-03T18:00:00\",\"endTime\":\"2099-11-03T19:30:00\",\"price\":100000}]");
    Files.writeString(
        directory.resolve("cinemaRooms.json"),
        "[{\"id\":\"R1\",\"name\":\"Room One\",\"rowCount\":1,\"seatsPerRow\":3}]");
    Files.writeString(
        directory.resolve("seats.json"),
        "[{\"id\":\"A1\",\"roomId\":\"R1\",\"rowLabel\":\"A\",\"seatNumber\":1,\"type\":\"NORMAL\",\"status\":\"ACTIVE\"},{\"id\":\"A2\",\"roomId\":\"R1\",\"rowLabel\":\"A\",\"seatNumber\":2,\"type\":\"NORMAL\",\"status\":\"ACTIVE\"},{\"id\":\"A3\",\"roomId\":\"R1\",\"rowLabel\":\"A\",\"seatNumber\":3,\"type\":\"NORMAL\",\"status\":\"ACTIVE\"}]");
  }

  private MockHttpSession register() throws Exception {
    var s = new MockHttpSession();
    int n = (int) (System.nanoTime() % 100000000);
    String phone = String.format("09%08d", n);
    send(
            "/api/auth/register",
            s,
            Map.of(
                "fullName",
                "Cinema Guest",
                "phone",
                phone,
                "email",
                UUID.randomUUID() + "@example.test",
                "password",
                "Password123!",
                "customerType",
                "NORMAL"),
            null)
        .andExpect(status().isCreated());
    return s;
  }

  private ResultActions send(String path, MockHttpSession s, Object body, String key)
      throws Exception {
    JsonNode token =
        json.readTree(
            mvc.perform(get("/api/auth/csrf").session(s))
                .andReturn()
                .getResponse()
                .getContentAsString());
    var request =
        post(path)
            .session(s)
            .header(token.get("headerName").asText(), token.get("token").asText())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body));
    if (key != null) request.header("Idempotency-Key", key);
    return mvc.perform(request);
  }

  private JsonNode result(ResultActions action) throws Exception {
    return json.readTree(action.andReturn().getResponse().getContentAsString());
  }

  private JsonNode hold(MockHttpSession s, String seat) throws Exception {
    return result(
        send("/api/commerce/holds", s, Map.of("showtimeId", "ST1", "seatIds", List.of(seat)), null)
            .andExpect(status().isOk()));
  }

  private JsonNode order(MockHttpSession s, String seat) throws Exception {
    JsonNode h = hold(s, seat);
    return result(
        send(
                "/api/commerce/orders",
                s,
                Map.of(
                    "holdId",
                    h.get("id").asText(),
                    "combos",
                    List.of(),
                    "points",
                    0,
                    "method",
                    "MOMO",
                    "contact",
                    Map.of(
                        "name",
                        "Cinema Guest",
                        "email",
                        "guest@example.test",
                        "phone",
                        "0912345678")),
                UUID.randomUUID().toString())
            .andExpect(status().isOk()));
  }

  @Test
  void legacyBookingCannotTakeCommerceHeldSeat() throws Exception {
    MockHttpSession s = register();
    hold(s, "A1");
    send("/api/bookings", s, Map.of("showtimeId", "ST1", "seatIds", List.of("A1")), null)
        .andExpect(status().isConflict());
  }

  @Test
  void catalogDeletionCannotDestroyHeldInventory() throws Exception {
    MockHttpSession s = register();
    hold(s, "A2");
    MockHttpSession admin = new MockHttpSession();
    send(
            "/api/auth/login",
            admin,
            Map.of("email", "admin@integration.test", "password", "Admin123!"),
            null)
        .andExpect(status().isOk());
    var token =
        json.readTree(
            mvc.perform(get("/api/auth/csrf").session(admin))
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(
            delete("/api/showtimes/ST1")
                .session(admin)
                .header(token.get("headerName").asText(), token.get("token").asText()))
        .andExpect(status().isConflict());
  }

  @Test
  void protectsOwnedOrdersAndIssuesOwnedQrOnlyAfterPayment() throws Exception {
    MockHttpSession s = register(), other = register();
    JsonNode o = order(s, "A3");
    String id = o.get("id").asText();
    mvc.perform(get("/api/commerce/orders/" + id).session(other)).andExpect(status().isForbidden());
    mvc.perform(get("/api/commerce/admin/orders").session(s)).andExpect(status().isForbidden());
    JsonNode a =
        result(
            send(
                    "/api/commerce/orders/" + id + "/payments",
                    s,
                    Map.of("method", "MOMO"),
                    UUID.randomUUID().toString())
                .andExpect(status().isOk()));
    JsonNode paid =
        result(
            send(
                    "/api/commerce/payments/" + a.get("id").asText() + "/confirm",
                    s,
                    Map.of("scenario", "SUCCESS"),
                    null)
                .andExpect(status().isOk()));
    String ticket = paid.get("tickets").get(0).get("id").asText();
    byte[] png =
        mvc.perform(get("/api/commerce/tickets/" + ticket + "/qr").session(s))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertEquals((byte) 0x89, png[0]);
    mvc.perform(get("/api/commerce/tickets/" + ticket + "/qr").session(other))
        .andExpect(status().isForbidden());
  }
}
