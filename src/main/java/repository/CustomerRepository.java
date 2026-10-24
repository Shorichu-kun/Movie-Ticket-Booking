package repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import exception.CustomerConflictException;
import exception.CustomerNotFoundException;
import exception.CustomerStorageException;
import model.Customer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class CustomerRepository {
    private final Path storagePath;
    private final ObjectMapper objectMapper;

    public CustomerRepository(@Value("${customers.storage-path:data/customers.json}") String storagePath,
                              ObjectMapper objectMapper) {
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public synchronized List<Customer> findAll() {
        if (Files.notExists(storagePath)) {
            return new ArrayList<>();
        }
        try {
            List<Customer> customers = objectMapper.readerFor(new TypeReference<List<Customer>>() { })
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(storagePath.toFile());
            if (customers == null || customers.stream().anyMatch(customer -> customer == null
                    || customer.getId() == null || customer.getCustomerType() == null
                    || customer.getFullName() == null || customer.getPhone() == null
                    || customer.getEmail() == null)) {
                throw new IOException("Dữ liệu khách hàng không đúng cấu trúc.");
            }
            return customers;
        } catch (IOException e) {
            throw new CustomerStorageException("Không thể đọc dữ liệu khách hàng.", e);
        }
    }

    public synchronized Optional<Customer> findById(String id) {
        return findAll().stream().filter(customer -> customer.getId().equalsIgnoreCase(id)).findFirst();
    }

    public synchronized Customer save(Customer customer) {
        List<Customer> customers = findAll();
        if (customers.stream().anyMatch(existing -> existing.getId().equalsIgnoreCase(customer.getId()))) {
            throw new CustomerConflictException("id", "Mã khách hàng đã tồn tại.");
        }
        checkUniqueFields(customers, customer);
        customers.add(customer);
        writeAll(customers);
        return customer;
    }

    public synchronized Customer update(Customer customer) {
        List<Customer> customers = findAll();
        int index = -1;
        for (int i = 0; i < customers.size(); i++) {
            if (customers.get(i).getId().equalsIgnoreCase(customer.getId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new CustomerNotFoundException(customer.getId());
        }
        checkUniqueFields(customers, customer);
        customers.set(index, customer);
        writeAll(customers);
        return customer;
    }

    private void checkUniqueFields(List<Customer> customers, Customer candidate) {
        for (Customer customer : customers) {
            if (customer.getId().equalsIgnoreCase(candidate.getId())) {
                continue;
            }
            if (customer.getPhone().equals(candidate.getPhone())) {
                throw new CustomerConflictException("phone", "Số điện thoại đã được khách hàng khác sử dụng.");
            }
            if (customer.getEmail().equalsIgnoreCase(candidate.getEmail())) {
                throw new CustomerConflictException("email", "Email đã được khách hàng khác sử dụng.");
            }
            if (candidate.getStudentId() != null
                    && candidate.getStudentId().equalsIgnoreCase(customer.getStudentId())) {
                throw new CustomerConflictException("studentId", "Mã sinh viên đã được khách hàng khác sử dụng.");
            }
        }
    }

    private void writeAll(List<Customer> customers) {
        Path temporaryFile = null;
        try {
            Files.createDirectories(storagePath.getParent());
            temporaryFile = Files.createTempFile(storagePath.getParent(), ".customers-", ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), customers);
            try {
                Files.move(temporaryFile, storagePath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new CustomerStorageException("Không thể lưu dữ liệu khách hàng.", e);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    temporaryFile.toFile().deleteOnExit();
                }
            }
        }
    }
}
