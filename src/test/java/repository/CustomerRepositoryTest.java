package repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.CustomerStorageException;
import model.Customer;
import model.CustomerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerRepositoryTest {
    @TempDir
    private Path directory;

    @Test
    void persistsInheritedFieldsAndVietnameseTextAcrossRepositoryInstances() {
        Path path = directory.resolve("nested/customers.json");
        var repository = repository(path);
        repository.save(new Customer("CUS-001", "Nguyễn Văn An", "0912345678", "an@example.com",
                CustomerType.STUDENT, "SV2026001"));

        var reloaded = repository(path).findById("cus-001").orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(reloaded.getPhone()).isEqualTo("0912345678");
        assertThat(reloaded.getCustomerType()).isEqualTo(CustomerType.STUDENT);
        assertThat(reloaded.getStudentId()).isEqualTo("SV2026001");
    }

    @Test
    void refusesToOverwriteCorruptData() throws Exception {
        Path path = directory.resolve("customers.json");
        String corruptData = "[{\"id\": \"CUS-001\", invalid json";
        Files.writeString(path, corruptData);

        assertThatThrownBy(() -> repository(path).save(new Customer("CUS-002", "Nguyễn An", "0912345678",
                "an@example.com", CustomerType.NORMAL, null))).isInstanceOf(CustomerStorageException.class);
        assertThat(Files.readString(path)).isEqualTo(corruptData);
    }

    @Test
    void rejectsNullOrIncompleteStoredRecords() throws Exception {
        Path path = directory.resolve("customers.json");
        for (String data : new String[]{"null", "[null]", "[{\"id\":\"CUS-001\"}]", "[] trailing-data", ""}) {
            Files.writeString(path, data);
            assertThatThrownBy(() -> repository(path).findAll()).isInstanceOf(CustomerStorageException.class);
        }
    }

    @Test
    void propagatesWriteFailureInsteadOfReportingSuccessfulRegistration() throws Exception {
        Path blockedParent = directory.resolve("not-a-directory");
        Files.writeString(blockedParent, "existing file");

        assertThatThrownBy(() -> repository(blockedParent.resolve("customers.json")).save(
                new Customer("CUS-001", "Nguyễn An", "0912345678", "an@example.com", CustomerType.NORMAL, null)))
                .isInstanceOf(CustomerStorageException.class);
        assertThat(Files.readString(blockedParent)).isEqualTo("existing file");
    }

    private CustomerRepository repository(Path path) {
        return new CustomerRepository(path.toString(), new ObjectMapper());
    }
}
