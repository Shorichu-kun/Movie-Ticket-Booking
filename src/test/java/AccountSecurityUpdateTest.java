import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.Map;
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
class AccountSecurityUpdateTest {
  @TempDir static Path directory;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("customers.storage-path", () -> directory.resolve("customers.json").toString());
    r.add("accounts.storage-path", () -> directory.resolve("accounts.json").toString());
    r.add("cinema.data-dir", () -> directory.toString());
    r.add("admin.email", () -> "");
    r.add("admin.password", () -> "");
  }

  @BeforeEach
  void clear() throws Exception {
    Files.deleteIfExists(directory.resolve("accounts.json"));
    Files.deleteIfExists(directory.resolve("customers.json"));
  }

  private ResultActions send(String path, MockHttpSession session, Map<String, ?> payload)
      throws Exception {
    var token =
        json.readTree(
            mvc.perform(get("/api/auth/csrf").session(session))
                .andReturn()
                .getResponse()
                .getContentAsString());
    return mvc.perform(
        post(path)
            .session(session)
            .header(token.get("headerName").asText(), token.get("token").asText())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(payload)));
  }

  private MockHttpSession register() throws Exception {
    MockHttpSession session = new MockHttpSession();
    send(
            "/api/auth/register",
            session,
            Map.of(
                "fullName",
                "Demo User",
                "phone",
                "0912345678",
                "email",
                "secure@example.test",
                "password",
                "Original123!",
                "customerType",
                "NORMAL"))
        .andExpect(status().isCreated());
    return session;
  }

  private MockHttpSession login(String password) throws Exception {
    MockHttpSession session = new MockHttpSession();
    send("/api/auth/login", session, Map.of("email", "secure@example.test", "password", password))
        .andExpect(status().isOk());
    return session;
  }

  @Test
  void changesPasswordAndInvalidatesOtherSessions() throws Exception {
    MockHttpSession first = register(), other = login("Original123!");
    send(
            "/api/auth/change-password",
            first,
            Map.of("currentPassword", "Original123!", "newPassword", "Updated456!"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(other)).andExpect(status().isUnauthorized());
    send(
            "/api/auth/login",
            new MockHttpSession(),
            Map.of("email", "secure@example.test", "password", "Original123!"))
        .andExpect(status().isUnauthorized());
    login("Updated456!");
    assertFalse(Files.readString(directory.resolve("accounts.json")).contains("Updated456!"));
  }

  @Test
  void wrongCurrentPasswordCannotChangeCredentials() throws Exception {
    MockHttpSession s = register();
    send(
            "/api/auth/change-password",
            s,
            Map.of("currentPassword", "Wrong123!", "newPassword", "Updated456!"))
        .andExpect(status().isUnauthorized());
    login("Original123!");
  }

  @Test
  void rejectsWeakPasswordWithoutChangingAccount() throws Exception {
    MockHttpSession s = register();
    send(
            "/api/auth/change-password",
            s,
            Map.of("currentPassword", "Original123!", "newPassword", "short"))
        .andExpect(status().isBadRequest());
    login("Original123!");
  }

  @Test
  void emailChangeRequiresCurrentPassword() throws Exception {
    MockHttpSession s = register();
    var token =
        json.readTree(
            mvc.perform(get("/api/auth/csrf").session(s))
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(
            put("/api/auth/profile")
                .session(s)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"fullName\":\"Demo"
                        + " User\",\"phone\":\"0912345678\",\"email\":\"changed@example.test\",\"customerType\":\"NORMAL\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me").session(s))
        .andExpect(jsonPath("$.email").value("secure@example.test"));
  }
}
