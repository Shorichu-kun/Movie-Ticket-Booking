package handler;

import dto.CustomerRequest;
import dto.CustomerResponse;
import dto.PriceQuoteRequest;
import dto.PriceQuoteResponse;
import dto.StudentIdCheckResponse;
import jakarta.validation.Valid;
import model.CustomerType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import service.CustomerService;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
public class CustomerHandler {
    private final CustomerService customerService;

    public CustomerHandler(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerResponse> getCustomers(@RequestParam(defaultValue = "") String q,
                                               @RequestParam(required = false) CustomerType customerType) {
        return customerService.getCustomers(q, customerType);
    }

    @GetMapping("/{id}")
    public CustomerResponse getCustomer(@PathVariable String id) {
        return customerService.getCustomer(id);
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse customer = customerService.createCustomer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(customer.id()).toUri();
        return ResponseEntity.created(location).body(customer);
    }

    @PutMapping("/{id}")
    public CustomerResponse updateCustomer(@PathVariable String id, @Valid @RequestBody CustomerRequest request) {
        return customerService.updateCustomer(id, request);
    }

    @GetMapping("/student-id/check")
    public StudentIdCheckResponse checkStudentId(@RequestParam String studentId,
                                                @RequestParam(required = false) String excludeCustomerId) {
        return customerService.checkStudentId(studentId, excludeCustomerId);
    }

    @GetMapping("/policies")
    public Map<CustomerType, BigDecimal> getDiscountPolicies() {
        return customerService.getDiscountPolicies();
    }

    @PostMapping("/{id}/price-quote")
    public PriceQuoteResponse quotePrice(@PathVariable String id, @Valid @RequestBody PriceQuoteRequest request) {
        return customerService.quotePrice(id, request);
    }
}
