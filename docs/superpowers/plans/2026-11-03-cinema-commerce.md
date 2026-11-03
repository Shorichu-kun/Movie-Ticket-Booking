# N6 Cinema OOP Commerce Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement task-by-task.

**Goal:** Deliver working multi-step cinema checkout, simulated payments and a customer membership area.

**Architecture:** Encapsulated order/hold/payment/loyalty domain with polymorphic payment and discount strategies, atomic JSON commerce state and session-protected HTTP endpoints. Existing catalog and customer identities remain authoritative. Separate responsive checkout/account pages consume the same API.

**Tech Stack:** Java 17, Spring Boot 3.2, Jackson, vanilla JavaScript/CSS, JUnit/MockMvc.

**Spec:** `docs/booking-payment-membership-oop-design.md`

## Global Constraints

- Payments are local simulation only; all screens and receipts explicitly label demo transactions.
- Work in the user-selected `thanhdo` checkout. Do not change branches or push without a new request.
- No added docstrings. Stage and commit only task files after relevant checks.
- Preserve existing JSON/customer data and legacy pricing/authorization. Serialize commerce mutations atomically.
- Use Asia/Ho_Chi_Minh. Online hold 7 minutes; counter deadline 15 minutes from original creation. Max 8 seats.
- One point = 1,000 VND; Member/Silver/Gold/Platinum rates 3/4/5/7%, thresholds 1.5/3/6 million VND rolling 365 days.

## Review Focus

- Cross-user resource access and forged prices must fail at the server boundary (Task 1/4 integration tests).
- Expiry, repeated requests and late payment events must never double-sell or double-award (Task 1 domain tests).
- Payment failure and restart must retain pending order history without granting tickets (Task 1 persistence tests).
- User-controlled text must render as text, including in order details and notifications (Task 2/3 browser checks).
- Existing ticket occupancy must remain visible during migration and legacy booking routes must not bypass holds (Task 4 tests).

## Execution rulings

- User's instruction to code authorizes implementation of the approved design without another plan approval pause.
- Keep current branch in place: user previously explicitly selected `thanhdo` and rejected an unexpected feature branch.
- Commerce backend has one delegated implementer. UI/integration work stays with the primary agent, with disjoint file ownership and a shared API contract below.
- Plan/spec conflicts resolve in favor of the spec; minor API and storage choices are documented here.

## Shared API contract

Base `/api/commerce`. Authentication uses existing AccountService/Cinema session; write requests use existing CSRF handling. Money JSON values are VND numbers. Instants are ISO-8601 with timezone. Errors use `message`.

- `GET /catalog`: `{products:[{id,name,description,price,variants:[string]}],methods:[{code,label,family}],offers:[{code,title,description,minimumAmount,discountAmount,percent,maxDiscount,expiresAt}],rewards:[{id,title,points,campaignCode}]}`.
- `GET /shows/{id}/seats`: `{showtime,movie,room,seats:[{id,rowLabel,seatNumber,type,status,available,heldByMe,price}],serverTime}`. Existing catalog model field names retained.
- `POST /holds` `{showtimeId,seatIds}`; `GET /holds/{id}`; `PUT /holds/{id}/seats` `{seatIds}`; `DELETE /holds/{id}`. Hold `{id,showtimeId,seatIds,createdAt,expiresAt,status,serverTime}`.
- `POST /quote` `{holdId,combos:[{productId,quantity,variant}],voucherCode,points,method}`. Quote `{subtotal,ticketSubtotal,comboSubtotal,discount,discountLabel,pointsUsed,pointsValue,total,pointsEarned,expiresAt}`.
- `POST /orders`: quote request plus `{contact:{name,email,phone}}`, `Idempotency-Key` required.
- `GET /orders` current customer's orders; `GET /orders/{id}` owned order; POST `/{id}/cancel`; POST `/{id}/refund` returns updated order.
- Order `{id,code,status,createdAt,expiresAt,movie,showtime,room,seatIds,seatLabels,items:[{type,title,quantity,unitPrice,total}],quote,contact,method,attempts,tickets:[{id,seatLabel,token,status}],events:[{at,message}],comboCollected}`.
- `POST /orders/{id}/payments` `{method}`, `Idempotency-Key` required -> attempt `{id,orderId,method,status,amount,expiresAt}`.
- `POST /payments/{id}/confirm` `{scenario:SUCCESS|FAILED|CANCELLED|PROCESSING|LATE_SUCCESS}` -> updated order. Only owning customer in enabled demo mode or admin; PROCESSING can later resolve, terminal states cannot be arbitrarily overwritten.
- `GET /me`: `{membership:{tier,points,availablePoints,reservedPoints,expiringPoints,yearlySpend,nextTier,nextThreshold,remainingToNext,earnRate},vouchers,pointEntries:[{id,type,points,createdAt,description,orderId,expiresAt}],orders,notifications,preferences}`.
- `PUT /me/preferences` `{birthDate,favoriteGenre,notifyShowtime,notifyOffers}` -> preferences. Birthday cannot be changed repeatedly to claim gifts.
- `POST /vouchers/claim` `{code}` -> updated me. `POST /rewards/redeem` `{rewardId}`, idempotency key -> updated me.
- Admin: `GET /admin/orders`; `POST /admin/orders/{id}/confirm-counter`; `POST /admin/orders/{id}/refund` `{approved:boolean}`; `POST /admin/check-in` `{token,collectCombo:boolean}`. Return updated order.
- Payment codes: MOMO, ZALOPAY, SHOPEEPAY, VNPAY, ATM, CARD, TRANSFER, CASH, LOYALTY (internal zero-payable only).
- Main agent adds real QR PNG rendering at `GET /api/commerce/tickets/{id}/qr` and password change at `POST /api/auth/change-password` `{currentPassword,newPassword}`.

### Task 1: Transactional OOP backend

**Files:** Create `src/main/java/{model,service,repository}/commerce/*`, `src/main/java/handler/CommerceHandler.java`, tests under `src/test/java/commerce/`. Root agent owns shared security/config/legacy integration files.

**Interfaces:** Produce the shared HTTP contract; expose `CommerceService` functions for legacy occupancy checks and owned ticket lookup. Domain classes keep private fields or immutable records and explicit state transitions. PaymentMethod strategies use normalized initiation/results; OrderItem subclasses implement ticket/combo behavior. No dependency on frontend code.

- [ ] Write meaningful failing tests for exclusive seat holds, expiry, idempotency, payment failure/retry, duplicate/late success, voucher/points reservation, full refund reversal and restart durability.
- [ ] Run targeted tests and capture RED before implementing their behavior.
- [ ] Implement domain + atomic state repository, catalog products/campaigns and service/controller contract.
- [ ] Implement membership/voucher ledger, profile preferences, notifications, refunds/check-in and legacy occupancy bridge.
- [ ] Run targeted tests and full Maven suite, inspect failures and fix.
- [ ] Commit only backend task files; write report with APIs, tests and integration notes.

### Task 2: Checkout and simulated payment interface

**Files:** Replace `Frontend/booking.html`; create `Frontend/booking.js`, `Frontend/commerce.css`, `Frontend/payment-demo.html`, `Frontend/payment-demo.js`, `Frontend/order.html`, `Frontend/order.js`, shared `Frontend/commerce-ui.js`.

**Interfaces:** Consume shared API/Cinema transport. No price calculation is authoritative in UI.

- [ ] Add browser-driven checks for seat selection, hold countdown, quote/benefit changes and failed/retried/successful demo payment.
- [ ] Implement five-step responsive checkout and order receipt/check-in QR views, no card/OTP collection or bank transfer QR.
- [ ] Exercise real endpoints and inspect browser on desktop/mobile; fix text overflow, navigation, refresh and auth recovery.
- [ ] Commit checkout files after relevant checks.

### Task 3: Customer account and offers

**Files:** Create `Frontend/account.html`, `Frontend/account.js`, `Frontend/offers.html`, `Frontend/offers.js`; modify welcome navigation and account password endpoint/service/repository.

**Interfaces:** Consume `/me`, owned orders, campaigns/rewards and preferences; reuse existing profile update endpoint with membership type protected.

- [ ] Write failing integration test for password change, wrong current password and ownership restrictions.
- [ ] Implement profile/security, membership progress, history, point ledger, voucher wallet/reward redemption, offers, notifications and order drill-down.
- [ ] Test password/session behavior, membership balances and rendering of stored text; browser-check tabs, empty states and mobile.
- [ ] Commit account and offers task files.

### Task 4: Integration, admin, migration and review

**Files:** Shared Spring security/config, legacy booking/repository/service bridges, admin UI, seed/readme/gitignore, end-to-end integration tests.

**Interfaces:** All catalog mutations and legacy bookings respect the commerce occupancy lock. Commerce state is ignored by git and demo mode explicitly configured. Migration preserves existing ticket occupancy without awarding points for seed/legacy data.

- [ ] Write boundary tests for unauthorized/cross-user reads and writes, legacy collision with new holds and immutable paid order history.
- [ ] Integrate navigation, admin orders/counter/refund/check-in and price/QR access; protect catalog deletions/updates while active inventory exists.
- [ ] Add non-destructive demo bootstrap/seed, document data migration, demo scenarios, OOP class map and run commands.
- [ ] Run full `mvn clean test`, JavaScript syntax checks, real HTTP checkout/refund/account journey and browser layout checks.
- [ ] Review whole diff independently, fix material findings, commit task files and verify final status. Do not push automatically.
