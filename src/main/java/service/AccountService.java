package service;

import dto.*;

import handler.ApiException;

import jakarta.annotation.PostConstruct;

import model.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import repository.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final PasswordEncoder passwords;
    private final String adminEmail, adminPassword;

    public AccountService(
            AccountRepository accounts,
            CustomerRepository customers,
            CustomerService customerService,
            PasswordEncoder passwords,
            @Value("${admin.email:}") String adminEmail,
            @Value("${admin.password:}") String adminPassword) {
        this.accounts = accounts;
        this.customers = customers;
        this.customerService = customerService;
        this.passwords = passwords;
        this.adminEmail = normalize(adminEmail);
        this.adminPassword = adminPassword;
    }

    @PostConstruct
    public void provisionAdmin() {
        synchronized (AccountRepository.LOCK) {
            if (adminEmail.isBlank() && adminPassword.isBlank()) return;
            if (adminEmail.isBlank()
                    || adminPassword.isBlank()
                    || !adminEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                throw new IllegalStateException(
                        "Configure both ADMIN_EMAIL and a valid ADMIN_PASSWORD.");
            validatePassword(adminPassword);
            if (accounts.findAll().stream()
                    .anyMatch(a -> a.role().equals("ADMIN") && adminEmail.equals(a.email())))
                return;
            if (customers.findAll().stream()
                    .anyMatch(c -> adminEmail.equalsIgnoreCase(c.getEmail())))
                throw new IllegalStateException("ADMIN_EMAIL already belongs to a customer.");
            accounts.save(
                    new Account(
                            UUID.randomUUID().toString(),
                            adminEmail,
                            passwords.encode(adminPassword),
                            "ADMIN",
                            null));
        }
    }

    public Account register(Registration r) {
        synchronized (AccountRepository.LOCK) {
            validatePassword(r.password());
            CustomerRequest request =
                    new CustomerRequest(
                            r.fullName(), r.phone(), r.email(), r.customerType(), r.studentId());
            if (request.customerType() == CustomerType.VIP)
                throw new ApiException(400, "Không thể tự đăng ký hạng VIP.");
            if (accounts.findAll().stream()
                    .anyMatch(
                            a ->
                                    a.role().equals("ADMIN")
                                            && a.email().equalsIgnoreCase(request.email())))
                throw new ApiException(409, "Email đã được sử dụng.");
            CustomerResponse customer = customerService.createCustomer(request);
            Account a =
                    new Account(
                            UUID.randomUUID().toString(),
                            null,
                            passwords.encode(r.password()),
                            "CUSTOMER",
                            customer.id());
            try {
                accounts.save(a);
            } catch (RuntimeException e) {
                try {
                    customers.deleteById(customer.id());
                } catch (RuntimeException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            }
            return a;
        }
    }

    public Account login(String email, String password) {
        synchronized (AccountRepository.LOCK) {
            provisionAdmin();
            String normalized = normalize(email);
            Account match =
                    accounts.findAll().stream()
                            .filter(a -> emailOf(a).equalsIgnoreCase(normalized))
                            .findFirst()
                            .orElse(null);
            if (match == null
                    || password == null
                    || !passwords.matches(password, match.passwordHash()))
                throw new ApiException(401, "Email hoặc mật khẩu không đúng.");
            return match;
        }
    }

    public Account require(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal()))
            throw new ApiException(401, "Vui lòng đăng nhập.");
        return accounts.findAll().stream()
                .filter(a -> a.id().equals(authentication.getName()))
                .findFirst()
                .orElseThrow(() -> new ApiException(401, "Phiên đăng nhập không còn hợp lệ."));
    }

    public Customer customer(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
            return null;
        Account a = require(auth);
        return a.customerId() == null
                ? null
                : customers
                        .findById(a.customerId())
                        .orElseThrow(
                                () -> new ApiException(401, "Không tìm thấy hồ sơ khách hàng."));
    }

    public UserView view(Account a) {
        return new UserView(
                a.id(),
                emailOf(a),
                a.role(),
                a.customerId() == null ? null : customerService.getCustomer(a.customerId()));
    }

    public UserView profile(Authentication auth, CustomerRequest request) {
        synchronized (AccountRepository.LOCK) {
            Account a = require(auth);
            if (!a.role().equals("CUSTOMER"))
                throw new ApiException(403, "Chỉ khách hàng có hồ sơ cá nhân.");
            CustomerResponse old = customerService.getCustomer(a.customerId());
            if (request.customerType() == CustomerType.VIP
                    && old.customerType() != CustomerType.VIP)
                throw new ApiException(400, "Không thể tự nâng hạng VIP.");
            CustomerRequest safe =
                    old.customerType() == CustomerType.VIP
                            ? new CustomerRequest(
                                    request.fullName(),
                                    request.phone(),
                                    request.email(),
                                    CustomerType.VIP,
                                    null)
                            : request;
            customerService.updateCustomer(a.customerId(), safe);
            return view(a);
        }
    }

    private String emailOf(Account a) {
        return a.customerId() == null
                ? a.email()
                : customers.findById(a.customerId()).map(Customer::getEmail).orElse("");
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static void validatePassword(String p) {
        if (p == null || p.length() < 8 || p.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(400, "Mật khẩu phải có ít nhất 8 ký tự và không quá 72 byte.");
    }

    public record Registration(
            String fullName,
            String phone,
            String email,
            String password,
            CustomerType customerType,
            String studentId) {}

    public record UserView(String id, String email, String role, CustomerResponse customer) {}
}
