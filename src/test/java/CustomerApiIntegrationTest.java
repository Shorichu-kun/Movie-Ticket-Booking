import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MovieTicketBookingApplication.class)
@AutoConfigureMockMvc
class CustomerApiIntegrationTest {
    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry registry) {
        registry.add("customers.storage-path", () -> directory.resolve("customers.json").toString());
    }

    @BeforeEach
    void clearStorage() throws Exception {
        Files.deleteIfExists(directory.resolve("customers.json"));
    }

    @Test
    void registersAndRetrievesCustomerThroughAllThreeLayers() throws Exception {
        String id = createStudent();

        mockMvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.studentId").value("SV2026001"))
                .andExpect(jsonPath("$.discountRate").value(0.10));
        mockMvc.perform(get("/api/customers").param("q", "nguyen").param("customerType", "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/customers").param("customerType", "VIP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsConflictWithFieldErrorsForDuplicateRegistration() throws Exception {
        createStudent();
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(studentJson()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.phone").isNotEmpty());
    }

    @Test
    void returnsBadRequestForMissingStudentIdAndInvalidContactDetails() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Nguyễn An", "phone", "0912345678",
                                "email", "an@example.com", "customerType", "STUDENT"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.studentId").isNotEmpty());
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", " ", "phone", "abc",
                                "email", "invalid", "customerType", "NORMAL"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.fullName").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.phone").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
    }

    @Test
    void updatesTypeAndCalculatesNewDiscount() throws Exception {
        String id = createStudent();
        mockMvc.perform(put("/api/customers/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Nguyễn Văn An", "phone", "0912345678",
                                "email", "an@example.com", "customerType", "VIP"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.customerType").value("VIP")).andExpect(jsonPath("$.studentId").isEmpty());
        mockMvc.perform(post("/api/customers/{id}/price-quote", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"basePrice\":100000,\"seatSurcharge\":20000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalPrice").value(96000))
                .andExpect(jsonPath("$.discountAmount").value(24000));
    }

    @Test
    void exposesStudentPrecheckAndPoliciesWithoutCustomerInformation() throws Exception {
        String id = createStudent();
        mockMvc.perform(get("/api/customers/student-id/check").param("studentId", "sv2026001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(get("/api/customers/student-id/check").param("studentId", "sv2026001")
                        .param("excludeCustomerId", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(get("/api/customers/student-id/check").param("studentId", "SV 01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
        mockMvc.perform(get("/api/customers/policies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.NORMAL").value(0)).andExpect(jsonPath("$.STUDENT").value(0.10))
                .andExpect(jsonPath("$.VIP").value(0.20));
    }

    @Test
    void returns404ForMissingCustomer() throws Exception {
        mockMvc.perform(get("/api/customers/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(put("/api/customers/missing").contentType(MediaType.APPLICATION_JSON).content(studentJson()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns400ForInvalidJsonEnumsAndMissingParameters() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(studentJson().replace("STUDENT", "UNKNOWN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/customers").param("customerType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/customers/student-id/check"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.studentId").isNotEmpty());
    }

    @Test
    void rejectsNonPositivePricesThroughApi() throws Exception {
        String id = createStudent();
        mockMvc.perform(post("/api/customers/{id}/price-quote", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"basePrice\":0,\"seatSurcharge\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.basePrice").isNotEmpty());
    }

    @Test
    void reportsStorageErrorsWithoutLeakingInternalFilePaths() throws Exception {
        Files.writeString(directory.resolve("customers.json"), "broken-data");
        mockMvc.perform(get("/api/customers")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(
                        "Không thể đọc hoặc lưu dữ liệu khách hàng. Vui lòng kiểm tra nơi lưu trữ và thử lại."))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void servesCustomerFrontendFromPackagedResources() throws Exception {
        mockMvc.perform(get("/customers.html")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html"));
        mockMvc.perform(get("/customers.js")).andExpect(status().isOk());
        mockMvc.perform(get("/customers.css")).andExpect(status().isOk());
        mockMvc.perform(get("/api/movies")).andExpect(status().isOk());
    }

    private String createStudent() throws Exception {
        String result = mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(studentJson()))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(result).get("id").asText();
    }

    private String studentJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of("fullName", "Nguyễn Văn An", "phone", "0912345678",
                "email", "an@example.com", "customerType", "STUDENT", "studentId", "SV2026001"));
    }
}
