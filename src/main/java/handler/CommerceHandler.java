package handler;

import java.util.*;
import model.Account;
import model.Customer;
import model.commerce.CommerceRequests.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import service.AccountService;
import service.commerce.CommerceService;

@RestController
@RequestMapping("/api/commerce")
public class CommerceHandler {
  private final CommerceService commerce;
  private final AccountService accounts;

  public CommerceHandler(CommerceService commerce, AccountService accounts) {
    this.commerce = commerce;
    this.accounts = accounts;
  }

  @GetMapping("/catalog")
  public Map<String, Object> catalog() {
    return commerce.catalog();
  }

  @GetMapping("/shows/{id}/seats")
  public Map<String, Object> seats(@PathVariable String id, Authentication auth) {
    return commerce.seats(id, accounts.customer(auth));
  }

  @PostMapping("/holds")
  public Map<String, Object> hold(@RequestBody HoldRequest request, Authentication auth) {
    return commerce.createHold(owner(auth), request);
  }

  @GetMapping("/holds/{id}")
  public Map<String, Object> hold(@PathVariable String id, Authentication auth) {
    return commerce.hold(owner(auth), id);
  }

  @PutMapping("/holds/{id}/seats")
  public Map<String, Object> seats(
      @PathVariable String id, @RequestBody SeatSelection request, Authentication auth) {
    return commerce.updateHold(owner(auth), id, request.seatIds());
  }

  @DeleteMapping("/holds/{id}")
  public Map<String, Object> release(@PathVariable String id, Authentication auth) {
    return commerce.releaseHold(owner(auth), id);
  }

  @PostMapping("/quote")
  public Object quote(@RequestBody CheckoutRequest request, Authentication auth) {
    return commerce.quote(customer(auth), request);
  }

  @PostMapping("/orders")
  public Map<String, Object> order(
      @RequestBody CheckoutRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      Authentication auth) {
    return commerce.createOrder(customer(auth), request, key);
  }

  @GetMapping("/orders")
  public Object orders(Authentication auth) {
    return commerce.orders(owner(auth));
  }

  @GetMapping("/orders/{id}")
  public Map<String, Object> order(@PathVariable String id, Authentication auth) {
    return commerce.order(owner(auth), id);
  }

  @PostMapping("/orders/{id}/cancel")
  public Map<String, Object> cancel(@PathVariable String id, Authentication auth) {
    return commerce.cancel(owner(auth), id);
  }

  @PostMapping("/orders/{id}/refund")
  public Map<String, Object> refund(@PathVariable String id, Authentication auth) {
    return commerce.requestRefund(owner(auth), id);
  }

  @PostMapping("/orders/{id}/payments")
  public Map<String, Object> payment(
      @PathVariable String id,
      @RequestBody MethodSelection request,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      Authentication auth) {
    return commerce.startPayment(owner(auth), id, request.method(), key);
  }

  @GetMapping("/payments/{id}")
  public Map<String, Object> payment(@PathVariable String id, Authentication auth) {
    Account account = accounts.require(auth);
    return commerce.payment(account.customerId(), account.role().equals("ADMIN"), id);
  }

  @PostMapping("/payments/{id}/confirm")
  public Map<String, Object> confirm(
      @PathVariable String id, @RequestBody Scenario request, Authentication auth) {
    Account account = accounts.require(auth);
    return commerce.confirmPayment(
        account.customerId(), account.role().equals("ADMIN"), id, request.scenario());
  }

  @GetMapping("/me")
  public Map<String, Object> me(Authentication auth) {
    return commerce.me(owner(auth));
  }

  @PutMapping("/me/preferences")
  public Map<String, Object> preferences(@RequestBody Preferences request, Authentication auth) {
    return commerce.preferences(owner(auth), request);
  }

  @PostMapping("/vouchers/claim")
  public Map<String, Object> claim(@RequestBody Code request, Authentication auth) {
    return commerce.claimVoucher(owner(auth), request.code());
  }

  @PostMapping("/rewards/redeem")
  public Map<String, Object> redeem(
      @RequestBody Reward request,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      Authentication auth) {
    return commerce.redeem(owner(auth), request.rewardId(), key);
  }

  @GetMapping("/admin/orders")
  public Object all(Authentication auth) {
    admin(auth);
    return commerce.adminOrders();
  }

  @PostMapping("/admin/orders/{id}/confirm-counter")
  public Map<String, Object> counter(@PathVariable String id, Authentication auth) {
    admin(auth);
    return commerce.confirmCounter(id);
  }

  @PostMapping("/admin/orders/{id}/refund")
  public Map<String, Object> approve(
      @PathVariable String id, @RequestBody Approval request, Authentication auth) {
    admin(auth);
    return commerce.approveRefund(id, request.approved());
  }

  @PostMapping("/admin/check-in")
  public Map<String, Object> checkIn(@RequestBody CheckIn request, Authentication auth) {
    admin(auth);
    return commerce.checkIn(request.token(), request.collectCombo());
  }

  private Customer customer(Authentication auth) {
    accounts.require(auth);
    Customer customer = accounts.customer(auth);
    if (customer == null)
      throw new ApiException(403, "Chỉ khách hàng có thể sử dụng chức năng này.");
    return customer;
  }

  private String owner(Authentication auth) {
    return customer(auth).getId();
  }

  private void admin(Authentication auth) {
    if (!accounts.require(auth).role().equals("ADMIN"))
      throw new ApiException(403, "Chỉ quản trị viên được thực hiện thao tác này.");
  }

  public record SeatSelection(List<String> seatIds) {}

  public record MethodSelection(String method) {}

  public record Scenario(String scenario) {}

  public record Code(String code) {}

  public record Reward(String rewardId) {}

  public record Approval(boolean approved) {}

  public record CheckIn(String token, boolean collectCombo) {}
}
