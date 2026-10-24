package service;

import dto.CustomerRequest;
import dto.CustomerResponse;
import dto.PriceQuoteRequest;
import dto.PriceQuoteResponse;
import dto.StudentIdCheckResponse;
import exception.CustomerNotFoundException;
import exception.CustomerValidationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import model.Customer;
import model.CustomerType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import policy.NormalCustomerPricePolicy;
import policy.StudentPricePolicy;
import policy.TicketPricePolicy;
import policy.VipPricePolicy;
import repository.CustomerRepository;
import utils.IdGenerator;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final Validator validator;
    private final Map<CustomerType, TicketPricePolicy> pricePolicies;

    public CustomerService(CustomerRepository customerRepository, Validator validator,
                           @Value("${customers.discount.student:0.10}") BigDecimal studentDiscount,
                           @Value("${customers.discount.vip:0.20}") BigDecimal vipDiscount) {
        this.customerRepository = customerRepository;
        this.validator = validator;
        this.pricePolicies = Map.of(
                CustomerType.NORMAL, new NormalCustomerPricePolicy(),
                CustomerType.STUDENT, new StudentPricePolicy(studentDiscount),
                CustomerType.VIP, new VipPricePolicy(vipDiscount));
    }

    public List<CustomerResponse> getCustomers(String query, CustomerType customerType) {
        String keyword = normalizeSearch(query == null ? "" : query.strip());
        return customerRepository.findAll().stream()
                .filter(customer -> customerType == null || customer.getCustomerType() == customerType)
                .filter(customer -> keyword.isEmpty() || matchesSearch(customer, keyword))
                .sorted(Comparator.comparing(customer -> normalizeSearch(customer.getFullName())))
                .map(this::toResponse)
                .toList();
    }

    public CustomerResponse getCustomer(String id) {
        return toResponse(findCustomer(id));
    }

    public CustomerResponse createCustomer(CustomerRequest request) {
        validateCustomer(request);
        Customer customer = toCustomer(IdGenerator.generateCustomerId(), request);
        return toResponse(customerRepository.save(customer));
    }

    public CustomerResponse updateCustomer(String id, CustomerRequest request) {
        Customer existing = findCustomer(id);
        validateCustomer(request);
        return toResponse(customerRepository.update(toCustomer(existing.getId(), request)));
    }

    public StudentIdCheckResponse checkStudentId(String studentId, String excludeCustomerId) {
        String normalizedId = studentId == null ? "" : studentId.strip().toUpperCase(Locale.ROOT);
        if (!normalizedId.matches("[A-Z0-9]{6,20}")) {
            return new StudentIdCheckResponse(normalizedId, false, false,
                    "Mã sinh viên phải có 6–20 ký tự chữ cái A–Z hoặc chữ số, không chứa khoảng trắng.");
        }
        String excludedId = excludeCustomerId == null || excludeCustomerId.isBlank()
                ? null : findCustomer(excludeCustomerId).getId();
        boolean available = customerRepository.findAll().stream()
                .filter(customer -> !customer.getId().equalsIgnoreCase(excludedId))
                .noneMatch(customer -> normalizedId.equalsIgnoreCase(customer.getStudentId()));
        return new StudentIdCheckResponse(normalizedId, true, available, available
                ? "Mã sinh viên đúng định dạng và chưa được tài khoản khác sử dụng."
                : "Mã sinh viên đã được khách hàng khác sử dụng.");
    }

    public Map<CustomerType, BigDecimal> getDiscountPolicies() {
        return Map.of(
                CustomerType.NORMAL, pricePolicies.get(CustomerType.NORMAL).getDiscountRate(),
                CustomerType.STUDENT, pricePolicies.get(CustomerType.STUDENT).getDiscountRate(),
                CustomerType.VIP, pricePolicies.get(CustomerType.VIP).getDiscountRate());
    }

    public PriceQuoteResponse quotePrice(String customerId, PriceQuoteRequest request) {
        validate(request);
        Customer customer = findCustomer(customerId);
        TicketPricePolicy policy = pricePolicies.get(customer.getCustomerType());
        BigDecimal finalPrice;
        try {
            finalPrice = policy.calculatePrice(request.basePrice(), request.seatSurcharge());
        } catch (IllegalArgumentException e) {
            throw new CustomerValidationException("basePrice", e.getMessage());
        }
        BigDecimal subtotal = request.basePrice().add(request.seatSurcharge());
        return new PriceQuoteResponse(customer.getId(), customer.getCustomerType(), request.basePrice(),
                request.seatSurcharge(), subtotal, policy.getDiscountRate(), subtotal.subtract(finalPrice),
                finalPrice, "VND");
    }

    private Customer findCustomer(String id) {
        return customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private void validateCustomer(CustomerRequest request) {
        validate(request);
        if (request.customerType() == CustomerType.STUDENT
                && (request.studentId() == null || !request.studentId().matches("[A-Z0-9]{6,20}"))) {
            throw new CustomerValidationException("studentId",
                    "Sinh viên phải có mã SV gồm 6–20 ký tự chữ cái A–Z hoặc chữ số.");
        }
        if (request.customerType() != CustomerType.STUDENT && request.studentId() != null) {
            throw new CustomerValidationException("studentId", "Mã sinh viên chỉ dùng cho loại khách Sinh viên.");
        }
    }

    private <T> void validate(T request) {
        if (request == null) {
            throw new CustomerValidationException("request", "Dữ liệu gửi lên không được để trống.");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private Customer toCustomer(String id, CustomerRequest request) {
        return new Customer(id, request.fullName(), request.phone(), request.email(),
                request.customerType(), request.studentId());
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getFullName(), customer.getPhone(),
                customer.getEmail(), customer.getCustomerType(), customer.getStudentId(),
                pricePolicies.get(customer.getCustomerType()).getDiscountRate());
    }

    private boolean matchesSearch(Customer customer, String keyword) {
        return List.of(customer.getId(), customer.getFullName(), customer.getPhone(), customer.getEmail(),
                        customer.getStudentId() == null ? "" : customer.getStudentId())
                .stream().anyMatch(value -> normalizeSearch(value).contains(keyword));
    }

    private String normalizeSearch(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace('đ', 'd');
    }
}
