import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.*;
import java.util.*;

@SpringBootTest(classes = MovieTicketBookingApplication.class)
@AutoConfigureMockMvc
class WelcomeSecurityIntegrationTest {
    @TempDir static Path directory;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired repository.AccountRepository accountRepository;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("customers.storage-path", () -> directory.resolve("customers.json").toString());
        r.add("accounts.storage-path", () -> directory.resolve("accounts.json").toString());
        r.add("cinema.data-dir", () -> directory.toString());
        r.add("admin.email", () -> "admin@example.com");
        r.add("admin.password", () -> "AdminPass123!");
    }

    @BeforeEach
    void clear() throws Exception {
        Files.deleteIfExists(directory.resolve("customers.json"));
        Files.deleteIfExists(directory.resolve("accounts.json"));
        Files.deleteIfExists(directory.resolve("tickets.json"));
        Files.writeString(
                directory.resolve("movies.json"),
                "[{\"id\":\"M1\",\"title\":\"Test\",\"duration\":90,\"status\":\"NOW_SHOWING\"}]");
        Files.writeString(
                directory.resolve("showtimes.json"),
                "[{\"id\":\"ST1\",\"movieId\":\"M1\",\"roomId\":\"R1\",\"startTime\":\"2099-11-03T18:30\",\"endTime\":\"2099-11-03T20:00\",\"price\":100000}]");
        Files.writeString(
                directory.resolve("cinemaRooms.json"),
                "[{\"id\":\"R1\",\"name\":\"One\",\"rowCount\":1,\"seatsPerRow\":3}]");
        Files.writeString(
                directory.resolve("seats.json"),
                "[{\"id\":\"R1-A1\",\"roomId\":\"R1\",\"rowLabel\":\"A\",\"seatNumber\":1,\"type\":\"NORMAL\",\"status\":\"ACTIVE\"},{\"id\":\"R1-A2\",\"roomId\":\"R1\",\"rowLabel\":\"A\",\"seatNumber\":2,\"type\":\"VIP\",\"status\":\"ACTIVE\"}]");
    }

    @Test
    void protectsAdminDataAndPages() throws Exception {
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/movies.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/movies")).andExpect(status().isOk());
        mvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registration("a@example.com", "0912345678")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        MockHttpSession customer = register("a@example.com", "0912345678");
        mvc.perform(get("/api/customers").session(customer)).andExpect(status().isForbidden());
        send("/api/movies", customer, "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/movies.html").session(customer)).andExpect(status().is3xxRedirection());
    }

    @Test
    void hashesPasswordsAndKeepsProfileLoginIdentityConsistent() throws Exception {
        MockHttpSession session = register("a@example.com", "0912345678");
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.customer.email").value("a@example.com"));
        String accounts = Files.readString(directory.resolve("accounts.json"));
        org.junit.jupiter.api.Assertions.assertFalse(accounts.contains("Password123!"));
        mvc.perform(
                        put("/api/auth/profile")
                                .session(session)
                                .header(csrf(session)[0], csrf(session)[1])
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fullName\":\"Test"
                                            + " User\",\"phone\":\"0912345678\",\"email\":\"new@example.com\",\"customerType\":\"NORMAL\"}"))
                .andExpect(status().isOk());
        send("/api/auth/logout", session, "{}").andExpect(status().isOk());
        MockHttpSession login = new MockHttpSession();
        send(
                        "/api/auth/login",
                        login,
                        "{\"email\":\"new@example.com\",\"password\":\"Password123!\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));
        send(
                        "/api/auth/login",
                        new MockHttpSession(),
                        "{\"email\":\"a@example.com\",\"password\":\"Password123!\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSelfAssignedVip() throws Exception {
        send(
                        "/api/auth/register",
                        new MockHttpSession(),
                        registration("a@example.com", "0912345678").replace("NORMAL", "VIP"))
                .andExpect(status().isBadRequest());
        MockHttpSession s = register("a@example.com", "0912345678");
        mvc.perform(
                        put("/api/auth/profile")
                                .session(s)
                                .header(csrf(s)[0], csrf(s)[1])
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        registration("a@example.com", "0912345678")
                                                .replace("NORMAL", "VIP")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void booksAtomicallyAndEnforcesOwnershipAndPaymentRoles() throws Exception {
        MockHttpSession a = register("a@example.com", "0912345678"),
                b = register("b@example.com", "0912345679");
        send("/api/bookings/quote", a, "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(100000));
        String body =
                send("/api/bookings", a, "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\"]}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$[0].paymentStatus").value("PENDING"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = json.readTree(body).get(0).get("id").asText();
        send("/api/bookings", b, "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A2\",\"R1-A1\"]}")
                .andExpect(status().isConflict());
        mvc.perform(get("/api/bookings/showtimes/ST1/seats"))
                .andExpect(jsonPath("$[1].available").value(true));
        mvc.perform(get("/api/bookings/mine").session(b))
                .andExpect(jsonPath("$.length()").value(0));
        send("/api/bookings/" + id + "/cancel", b, "{}").andExpect(status().isForbidden());
        send("/api/bookings/" + id + "/confirm-payment", a, "{}").andExpect(status().isForbidden());
        MockHttpSession admin = new MockHttpSession();
        send(
                        "/api/auth/login",
                        admin,
                        "{\"email\":\"admin@example.com\",\"password\":\"AdminPass123!\"}")
                .andExpect(status().isOk());
        send("/api/bookings/" + id + "/confirm-payment", admin, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));
        send("/api/bookings/" + id + "/cancel", a, "{}").andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidSeatsAndSurfacesStorageFailure() throws Exception {
        MockHttpSession s = register("a@example.com", "0912345678");
        send("/api/bookings", s, "{\"showtimeId\":\"ST1\",\"seatIds\":[\"missing\"]}")
                .andExpect(status().isBadRequest());
        send("/api/bookings", s, "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\",\"R1-A1\"]}")
                .andExpect(status().isBadRequest());
        Files.createDirectory(directory.resolve("tickets.json"));
        send("/api/bookings", s, "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\"]}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void anonymousStudentCheckCannotExcludeSomeoneElsesIdentity() throws Exception {
        MockHttpSession student = new MockHttpSession();
        String body =
                send(
                                "/api/auth/register",
                                student,
                                registration("a@example.com", "0912345678")
                                        .replace("\"NORMAL\"", "\"STUDENT\"")
                                        .replace(
                                                "\"role\":",
                                                "\"studentId\":\"SV2026001\",\"role\":"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String customerId = json.readTree(body).get("customer").get("id").asText();
        mvc.perform(
                        get("/api/customers/student-id/check")
                                .param("studentId", "SV2026001")
                                .param("excludeCustomerId", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(
                        get("/api/customers/student-id/check")
                                .param("studentId", "SV2026002")
                                .param("excludeCustomerId", "not-real"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void concurrentRequestsCannotSellOneSeatTwice() throws Exception {
        MockHttpSession a = register("a@example.com", "0912345678"),
                b = register("b@example.com", "0912345679");
        String[] ta = csrf(a), tb = csrf(b);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<Integer> first =
                    pool.submit(
                            () -> {
                                start.await();
                                return mvc.perform(
                                                post("/api/bookings")
                                                        .session(a)
                                                        .header(ta[0], ta[1])
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(
                                                                "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\"]}"))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus();
                            });
            java.util.concurrent.Future<Integer> second =
                    pool.submit(
                            () -> {
                                start.await();
                                return mvc.perform(
                                                post("/api/bookings")
                                                        .session(b)
                                                        .header(tb[0], tb[1])
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(
                                                                "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\"]}"))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus();
                            });
            start.countDown();
            java.util.List<Integer> statuses =
                    new java.util.ArrayList<>(
                            java.util.List.of(
                                    first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                                    second.get(10, java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(statuses);
            org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(201, 409), statuses);
            org.junit.jupiter.api.Assertions.assertEquals(
                    1, new repository.TicketRepository().findAll().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsPastEndedAndInactiveSeatsAndPricesStudentServerSide() throws Exception {
        MockHttpSession s = new MockHttpSession();
        send(
                        "/api/auth/register",
                        s,
                        registration("a@example.com", "0912345678")
                                .replace("\"NORMAL\"", "\"STUDENT\"")
                                .replace("\"role\":", "\"studentId\":\"SV2026001\",\"role\":"))
                .andExpect(status().isCreated());
        send(
                        "/api/bookings/quote",
                        s,
                        "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\",\"R1-A2\"],\"total\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(220000))
                .andExpect(jsonPath("$.total").value(198000));
        String original = Files.readString(directory.resolve("showtimes.json"));
        Files.writeString(
                directory.resolve("showtimes.json"), original.replace("2099-11-03", "2000-11-03"));
        send("/api/bookings", s, "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\"]}")
                .andExpect(status().isConflict());
        Files.writeString(directory.resolve("showtimes.json"), original);
        String movie = Files.readString(directory.resolve("movies.json"));
        Files.writeString(directory.resolve("movies.json"), movie.replace("NOW_SHOWING", "ENDED"));
        send("/api/bookings", s, "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\"]}")
                .andExpect(status().isConflict());
        Files.writeString(directory.resolve("movies.json"), movie);
        Files.writeString(
                directory.resolve("seats.json"),
                Files.readString(directory.resolve("seats.json")).replace("ACTIVE", "MAINTENANCE"));
        send("/api/bookings", s, "{\"showtimeId\":\"ST1\",\"seatIds\":[\"R1-A1\"]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsRegistrationBeforeCreatingCustomerWhenCredentialsAreUnreadable() throws Exception {
        Files.createDirectory(directory.resolve("accounts.json"));
        send(
                        "/api/auth/register",
                        new MockHttpSession(),
                        registration("a@example.com", "0912345678"))
                .andExpect(status().isInternalServerError());
        org.junit.jupiter.api.Assertions.assertFalse(
                Files.exists(directory.resolve("customers.json")));
    }

    @Test
    void rejectsEveryPersistedAdminEmailForProfileAndAdminCustomerWrites() throws Exception {
        MockHttpSession customer = register("a@example.com", "0912345678");
        accountRepository.save(
                new model.Account(
                        "old-admin",
                        "old-admin@example.com",
                        passwordEncoder.encode("Password123!"),
                        "ADMIN",
                        null));
        String profile = registration("old-admin@example.com", "0912345678");
        String[] token = csrf(customer);
        mvc.perform(
                        put("/api/auth/profile")
                                .session(customer)
                                .header(token[0], token[1])
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(profile))
                .andExpect(status().isBadRequest());
        MockHttpSession admin = new MockHttpSession();
        send(
                        "/api/auth/login",
                        admin,
                        "{\"email\":\"admin@example.com\",\"password\":\"AdminPass123!\"}")
                .andExpect(status().isOk());
        send("/api/customers", admin, registration("old-admin@example.com", "0912345679"))
                .andExpect(status().isBadRequest());
        String customerId =
                json.readTree(
                                mvc.perform(get("/api/auth/me").session(customer))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .get("customer")
                        .get("id")
                        .asText();
        token = csrf(admin);
        mvc.perform(
                        put("/api/customers/" + customerId)
                                .session(admin)
                                .header(token[0], token[1])
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(profile))
                .andExpect(status().isBadRequest());
    }

    MockHttpSession register(String email, String phone) throws Exception {
        MockHttpSession session = new MockHttpSession();
        send("/api/auth/register", session, registration(email, phone))
                .andExpect(status().isCreated());
        return session;
    }

    String registration(String email, String phone) {
        return "{\"fullName\":\"Test User\",\"phone\":\""
                + phone
                + "\",\"email\":\""
                + email
                + "\",\"password\":\"Password123!\",\"customerType\":\"NORMAL\",\"role\":\"ADMIN\"}";
    }

    String[] csrf(MockHttpSession s) throws Exception {
        JsonNode n =
                json.readTree(
                        mvc.perform(get("/api/auth/csrf").session(s))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        return new String[] {n.get("headerName").asText(), n.get("token").asText()};
    }

    org.springframework.test.web.servlet.ResultActions send(
            String path, MockHttpSession s, String body) throws Exception {
        String[] token = csrf(s);
        return mvc.perform(
                post(path)
                        .session(s)
                        .header(token[0], token[1])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }
}
