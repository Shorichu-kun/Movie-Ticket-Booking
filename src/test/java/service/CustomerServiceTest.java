package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dto.CustomerRequest;
import dto.CustomerResponse;
import dto.PriceQuoteRequest;
import exception.CustomerConflictException;
import exception.CustomerNotFoundException;
import exception.CustomerValidationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import model.CustomerType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import repository.CustomerRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerServiceTest {
    private static ValidatorFactory validatorFactory;

    @TempDir
    private Path directory;

    private CustomerService service;

    @BeforeAll
    static void initializeValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        service = new CustomerService(new CustomerRepository(directory.resolve("customers.json").toString(),
                new ObjectMapper()), validatorFactory.getValidator(), new BigDecimal("0.10"), new BigDecimal("0.20"));
    }

    @Test
    void registersNormalizedStudentWithGeneratedId() {
        CustomerResponse customer = service.createCustomer(new CustomerRequest("  Nguyễn   Văn An  ",
                "+84 912 345 678", " AN@EXAMPLE.COM ", CustomerType.STUDENT, " sv2026001 "));

        assertThat(customer.id()).startsWith("CUS-");
        assertThat(customer.fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(customer.phone()).isEqualTo("0912345678");
        assertThat(customer.email()).isEqualTo("an@example.com");
        assertThat(customer.studentId()).isEqualTo("SV2026001");
        assertThat(customer.discountRate()).isEqualByComparingTo("0.10");
        assertThat(service.getCustomer(customer.id())).isEqualTo(customer);
    }

    @ParameterizedTest
    @CsvSource({"NORMAL,120000.00", "STUDENT,108000.00", "VIP,96000.00"})
    void appliesCorrectPolicyIncludingSeatSurcharge(CustomerType type, BigDecimal expectedPrice) {
        CustomerResponse customer = service.createCustomer(request(type));
        var quote = service.quotePrice(customer.id(), new PriceQuoteRequest(new BigDecimal("100000"),
                new BigDecimal("20000")));

        assertThat(quote.finalPrice()).isEqualByComparingTo(expectedPrice);
        assertThat(quote.finalPrice().add(quote.discountAmount())).isEqualByComparingTo(quote.subtotal());
        assertThat(quote.currency()).isEqualTo("VND");
    }

    @Test
    void usesConfiguredRatesRatherThanHardcodedDefaults() {
        service = new CustomerService(new CustomerRepository(directory.resolve("custom.json").toString(),
                new ObjectMapper()), validatorFactory.getValidator(), new BigDecimal("0.15"), new BigDecimal("0.25"));
        var student = service.createCustomer(request(CustomerType.STUDENT));

        assertThat(service.quotePrice(student.id(), new PriceQuoteRequest(new BigDecimal("100000"),
                BigDecimal.ZERO)).finalPrice()).isEqualByComparingTo("85000");
        assertThat(service.getDiscountPolicies().get(CustomerType.VIP)).isEqualByComparingTo("0.25");
    }

    @Test
    void changesTypeWithoutChangingCustomerIdAndReleasesStudentId() {
        var student = service.createCustomer(request(CustomerType.STUDENT));
        var updated = service.updateCustomer(student.id(), request(CustomerType.VIP));

        assertThat(updated.id()).isEqualTo(student.id());
        assertThat(updated.studentId()).isNull();
        assertThat(updated.customerType()).isEqualTo(CustomerType.VIP);
        assertThat(service.checkStudentId("SV2026001", null).available()).isTrue();
        assertThat(service.getCustomers("", null)).hasSize(1);
    }

    @Test
    void checksStudentIdFormatUniquenessAndExcludesCurrentCustomer() {
        var student = service.createCustomer(request(CustomerType.STUDENT));

        assertThat(service.checkStudentId("sv2026001", null).available()).isFalse();
        assertThat(service.checkStudentId(" sv2026001 ", student.id()).available()).isTrue();
        assertThat(service.checkStudentId("SV 123", null).valid()).isFalse();
        assertThat(service.checkStudentId("", null).valid()).isFalse();
        assertThat(service.checkStudentId("SV2026002", null).available()).isTrue();
    }

    @Test
    void rejectsMissingOrMalformedStudentIdAndUnexpectedStudentIdOnNormalCustomer() {
        assertThatThrownBy(() -> service.createCustomer(new CustomerRequest("Nguyễn An", "0912345678",
                "an@example.com", CustomerType.STUDENT, null))).isInstanceOf(CustomerValidationException.class);
        assertThatThrownBy(() -> service.createCustomer(new CustomerRequest("Nguyễn An", "0912345678",
                "an@example.com", CustomerType.STUDENT, "SV 001"))).isInstanceOf(CustomerValidationException.class);
        assertThatThrownBy(() -> service.createCustomer(new CustomerRequest("Nguyễn An", "0912345678",
                "an@example.com", CustomerType.NORMAL, "SV2026001"))).isInstanceOf(CustomerValidationException.class);
        assertThat(service.getCustomers("", null)).isEmpty();
    }

    @Test
    void rejectsDuplicatePhoneAfterCountryCodeNormalization() {
        service.createCustomer(request(CustomerType.NORMAL));

        assertThatThrownBy(() -> service.createCustomer(new CustomerRequest("Trần Bình", "+84 912 345 678",
                "binh@example.com", CustomerType.NORMAL, null)))
                .isInstanceOf(CustomerConflictException.class).hasMessageContaining("Số điện thoại");
    }

    @Test
    void rejectsDuplicateEmailRegardlessOfCase() {
        service.createCustomer(request(CustomerType.NORMAL));

        assertThatThrownBy(() -> service.createCustomer(new CustomerRequest("Trần Bình", "0987654321",
                " AN@EXAMPLE.COM ", CustomerType.NORMAL, null)))
                .isInstanceOf(CustomerConflictException.class).hasMessageContaining("Email");
    }

    @Test
    void rechecksStudentIdAtSaveEvenIfPrecheckPassed() {
        assertThat(service.checkStudentId("SV2026001", null).available()).isTrue();
        service.createCustomer(request(CustomerType.STUDENT));

        assertThatThrownBy(() -> service.createCustomer(new CustomerRequest("Trần Bình", "0987654321",
                "binh@example.com", CustomerType.STUDENT, "sv2026001")))
                .isInstanceOf(CustomerConflictException.class).hasMessageContaining("Mã sinh viên");
    }

    @Test
    void conflictingUpdateLeavesOriginalRecordUnchanged() {
        var first = service.createCustomer(request(CustomerType.NORMAL));
        var second = service.createCustomer(new CustomerRequest("Trần Bình", "0987654321", "binh@example.com",
                CustomerType.VIP, null));

        assertThatThrownBy(() -> service.updateCustomer(second.id(), new CustomerRequest("Trần Bình", first.phone(),
                second.email(), CustomerType.VIP, null))).isInstanceOf(CustomerConflictException.class);
        assertThat(service.getCustomer(second.id())).isEqualTo(second);
    }

    @Test
    void searchesVietnameseNamesWithoutAccentsAndFiltersType() {
        var customer = service.createCustomer(request(CustomerType.STUDENT));

        assertThat(service.getCustomers("nguyen van", CustomerType.STUDENT)).containsExactly(customer);
        assertThat(service.getCustomers("sv2026001", null)).containsExactly(customer);
        assertThat(service.getCustomers("091234", null)).containsExactly(customer);
        assertThat(service.getCustomers("nguyen", CustomerType.VIP)).isEmpty();
    }

    @Test
    void rejectsInvalidContactDetailsAtServiceBoundary() {
        assertThatThrownBy(() -> service.createCustomer(new CustomerRequest(" ", "abc", "bad-email",
                null, null))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.createCustomer(null)).isInstanceOf(CustomerValidationException.class);
        assertThat(service.getCustomers("", null)).isEmpty();
    }

    @Test
    void rejectsInvalidPriceAndUnknownCustomer() {
        var customer = service.createCustomer(request(CustomerType.NORMAL));
        assertThatThrownBy(() -> service.quotePrice(customer.id(), new PriceQuoteRequest(BigDecimal.ZERO,
                BigDecimal.ZERO))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.quotePrice(customer.id(), new PriceQuoteRequest(BigDecimal.TEN,
                new BigDecimal("-1")))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.quotePrice("missing", new PriceQuoteRequest(BigDecimal.TEN,
                BigDecimal.ZERO))).isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> service.updateCustomer("missing", request(CustomerType.NORMAL)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void preventsConcurrentRegistrationsWithSamePhone() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Boolean> registration = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for registration");
                try {
                    service.createCustomer(request(CustomerType.NORMAL));
                    return true;
                } catch (CustomerConflictException e) {
                    return false;
                }
            };
            var first = executor.submit(registration);
            var second = executor.submit(registration);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(service.getCustomers("", null)).hasSize(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private CustomerRequest request(CustomerType type) {
        return new CustomerRequest("Nguyễn Văn An", "0912345678", "an@example.com", type,
                type == CustomerType.STUDENT ? "SV2026001" : null);
    }
}
