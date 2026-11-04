package model;

/**
 * Credential identity is separate from the customer membership type. Customer email lives in
 * Customer.
 */
public record Account(
        String id, String email, String passwordHash, String role, String customerId) {}
