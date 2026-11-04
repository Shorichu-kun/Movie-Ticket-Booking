# Welcome, customer accounts and booking

Implement in the existing local Spring Boot + static HTML project. User requested a functional welcome website, registration/login, bookings, admin/customer visibility, and an explanation of customer persistence. No hosting or remote push is requested.

## Design
- Preserve existing customer OOP model and JSON persistence. Separate Account role (ADMIN/CUSTOMER) from CustomerType (NORMAL/STUDENT/VIP).
- Session cookie authentication; hashed passwords; CSRF protection. Registration creates a linked Customer, never grants ADMIN or VIP. Admin provisioning from environment, no committed default password.
- Welcome catalogue with search, now showing/coming soon, quick booking, membership benefits. Public movie/showtime/room reads. Customer profile and own tickets. Admin hub links existing management screens and confirms payment at counter.
- A booking uses authenticated customer and server-side showtime/seat/pricing data. Atomic multi-seat reservation; reject occupied/nonexistent/inactive seats, past showtimes, and ended films. No pretend payment gateway: reserve and pay at counter; admin confirms receipt.
- Vietnamese, responsive, warm orange/cream cinema design. Original branding N6 Cinema; no copied Beta branding. Preserve the current movie admin page as movies.html. index.html and welcome.html open the welcome experience.

## Shared API contract
- GET /api/auth/csrf -> {token, headerName}; write requests send returned header.
- GET /api/auth/me -> {id,email,role,customer: CustomerResponse|null}; 401 when anonymous.
- POST /api/auth/register -> same user; payload {fullName,phone,email,password,customerType,studentId}; NORMAL/STUDENT only.
- POST /api/auth/login {email,password} -> same user; POST /api/auth/logout.
- PUT /api/auth/profile {fullName,phone,email,customerType,studentId} -> same user. Cannot self-assign VIP; preserve existing VIP. Identity login email consistency required.
- GET /api/customers/policies public; /api/customers/student-id/check public (format/availability only). Other customer APIs admin-only.
- GET /api/movies, /api/showtimes, /api/rooms remain public with original model fields. Writes and all admin HTML pages require ADMIN.
- GET /api/bookings/showtimes/{id}/seats -> array {id,roomId,rowLabel,seatNumber,type,status,available,price}; price is calculated for current customer if authenticated, otherwise normal price.
- POST /api/bookings/quote {showtimeId,seatIds} -> {subtotal,discountAmount,discountRate,total}.
- POST /api/bookings {showtimeId,seatIds} -> Ticket[] (existing model fields); authenticated CUSTOMER only.
- GET /api/bookings/mine -> own Ticket[]; GET /api/bookings -> all Ticket[], ADMIN only.
- POST /api/bookings/{id}/cancel -> updated Ticket; pending ticket owner only, or admin.
- POST /api/bookings/{id}/confirm-payment -> updated Ticket; ADMIN only, PENDING -> SUCCESS.
- Errors JSON {message,fieldErrors?} with meaningful HTTP status.

## Tasks and verification
1. Backend authentication, role enforcement, ownership and booking persistence with focused failing integration tests first; run Maven full suite. All API contract above in src/pom/tests. Protect existing tests by authenticating admin, not disabling filters globally.
2. Independent frontend in Frontend/: welcome, auth modal, profile, booking seat picker, ticket history, admin hub; shared fetch CSRF/session helper for old admin pages. Keep customer API connections. Syntax-check JS, then exercise actual browser flows and responsive layout.
3. README: actual data paths and data flow, runtime setup, optional safe demo seed, admin provisioning, payment boundaries, start command. Integrate and verify no customer can call admin APIs or read others' tickets; duplicate booking must fail; persisted data must survive restart.

## Review focus
Privilege escalation; customer ID/price tampering; booking race/double booking; CSRF/session expiry; existing admin UI compatibility and stored-content escaping. Do not overwrite existing data for demo.
