# N6 Cinema — Movie Ticket Booking

Dự án nhóm 6: Hệ thống quản lý rạp chiếu phim.

Ứng dụng Java Spring Boot + HTML/CSS/JavaScript, dùng JSON để lưu dữ liệu. Trang chính: `/welcome.html` (truy cập `/` sẽ chuyển tới đây).

## Customers kết nối dữ liệu thế nào?

```text
customers.html + customers.js (admin)
    → fetch /api/customers (session ADMIN + CSRF cho thao tác ghi)
    → CustomerHandler (nhận/kiểm tra request)
    → CustomerService (kiểm tra dữ liệu, mã SV, phân loại, chính sách giá)
    → CustomerRepository (đọc/ghi JSON)
    → data/customers.json
```

- Backend đóng gói thư mục `Frontend` thành static resources trong JAR. Mở qua Spring Boot, không mở HTML bằng `file://` hoặc Live Server riêng.
- Dữ liệu nằm trên máy chạy backend, không nằm trong localStorage. Ghi khách hàng, tài khoản, vé dùng file tạm rồi thay thế file đích; lỗi lưu trữ trả lỗi HTTP.
- `Customer extends User`: kế thừa thông tin chung. `CustomerType` gồm NORMAL/STUDENT/VIP. Các policy triển khai `TicketPricePolicy`, ưu đãi được tính từ backend.
- `Account` tách biệt thông tin đăng nhập và role ADMIN/CUSTOMER; `customerId` liên kết với `Customer.id`. Role không phải loại khách hàng: khách VIP vẫn là role CUSTOMER.
- Đăng ký tạo cả Account và Customer, mật khẩu lưu BCrypt. Hồ sơ Customers cũ không tự có mật khẩu: admin quản lý hồ sơ như trước; đăng ký trùng email/số điện thoại/mã SV bị từ chối. Chưa có luồng nhận tài khoản cho hồ sơ cũ hay quên mật khẩu qua email.
- Email đăng nhập của khách hàng được lấy từ Customer liên kết. Khi cập nhật email trong hồ sơ hoặc màn hình quản trị, khách hàng dùng email mới ở lần đăng nhập sau.

## Chức năng

| Người dùng | Chức năng |
|---|---|
| Chưa đăng nhập | Xem phim, tìm kiếm, lọc đang/sắp chiếu, xem ưu đãi, đăng ký/đăng nhập |
| Khách hàng | Checkout nhiều bước, combo, thanh toán mô phỏng, vé QR, lịch sử đơn, điểm, voucher, hội viên và hồ sơ/bảo mật |
| Admin | Quản lý danh mục/khách; xem đơn, xác nhận thu tiền thử tại quầy, duyệt hoàn thử và kiểm soát QR/nhận combo |

Phân quyền được kiểm tra tại API và trang quản trị. Người đăng ký không được tự cấp ADMIN/VIP. Phiên dùng cookie HttpOnly, SameSite=Lax, hết hạn sau 30 phút không hoạt động. Thao tác ghi có CSRF; client cập nhật token một lần nếu token hết hiệu lực. Không lưu mật khẩu hoặc token đăng nhập vào localStorage.

## Chạy ứng dụng

Yêu cầu **JDK 17+**, **Maven 3.9+**.

```bash
cd /Users/maccualinh/Downloads/Movie-Ticket-Booking
mvn test
mvn package
```

Tạo tài khoản admin lần đầu bằng biến môi trường (tự chọn email/mật khẩu, không có mật khẩu mặc định):

```bash
export ADMIN_EMAIL='admin@example.com'
read -s 'ADMIN_PASSWORD?Nhập mật khẩu admin (ít nhất 8 ký tự): '
export ADMIN_PASSWORD
java -jar target/Movie-Ticket-Booking-0.0.1-SNAPSHOT.jar
```

Lệnh `read` ở trên dành cho zsh trên macOS. Mật khẩu tối đa 72 byte do BCrypt. Mở `http://localhost:8080`. Admin đăng nhập cùng form với khách hàng và được chuyển đến `/admin.html`. Sau khi tài khoản đã được tạo trong `accounts.json`, có thể bỏ hai biến admin trong những lần khởi động tiếp theo. Đổi biến ADMIN_PASSWORD không thay mật khẩu của tài khoản admin đã tồn tại.

Nếu máy chưa cài Java/Maven, bộ portable được tải trong phiên làm việc này có thể dùng tạm:

```bash
export JAVA_HOME=/private/tmp/movie-tools/zulu17.60.17-ca-jdk17.0.16-macosx_aarch64/zulu-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/private/tmp/movie-tools/apache-maven-3.9.9/bin:$PATH"
```

`/private/tmp` có thể bị hệ điều hành dọn dẹp; nên cài JDK/Maven lâu dài khi tiếp tục phát triển.

## Dữ liệu demo và cấu hình

Repository ban đầu chưa có `movies.json` (chỉ có `movies.js`, backend không đọc file đó), và các file phòng, ghế, suất chiếu đang rỗng. Có thể thêm dữ liệu từ giao diện admin hoặc tạo **bộ demo riêng**, không thay thế dữ liệu cũ:

```bash
python3 scripts/seed-demo.py --directory /tmp/n6-demo-data
export CINEMA_DATA_DIR=/tmp/n6-demo-data
java -jar target/Movie-Ticket-Booking-0.0.1-SNAPSHOT.jar
```

Script yêu cầu thư mục đích rỗng; tạo 5 tên phim hư cấu, 2 phòng, 80 ghế và lịch 7 ngày bắt đầu ngày mai theo giờ Việt Nam. Muốn tiếp tục dùng dữ liệu demo sau khi khởi động lại, giữ cùng `CINEMA_DATA_DIR`. Admin dùng cùng biến bootstrap phía trên khi chạy bộ dữ liệu mới.

| Biến môi trường | Mặc định / ý nghĩa |
|---|---|
| `CINEMA_DATA_DIR` | `data`, thư mục dữ liệu chung |
| `CUSTOMERS_STORAGE_PATH` | `<CINEMA_DATA_DIR>/customers.json` |
| `ACCOUNTS_STORAGE_PATH` | `<CINEMA_DATA_DIR>/accounts.json` |
| `STUDENT_DISCOUNT_RATE` | `0.10` (10%) |
| `VIP_DISCOUNT_RATE` | `0.20` (20%) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Tạo admin khi chưa tồn tại |
| `SERVER_PORT` | `8080` |

Các file chung: `movies.json`, `showtimes.json`, `cinemaRooms.json`, `seats.json`, `tickets.json`. Không commit dữ liệu tài khoản/khách hàng/vé. Cấu hình JSON dành cho một tiến trình ứng dụng; chưa hỗ trợ nhiều instance cùng ghi một thư mục dữ liệu.

## Tài khoản demo trên local

Các tài khoản dưới đây có sẵn khi sử dụng bộ dữ liệu seed trong thư mục `data`.

Mật khẩu chung cho tài khoản demo: **`N6Demo!2026`**

| Loại | Email |
|---|---|
| Admin | `demo.admin@example.test` |
| Khách thường | `demo001@example.test` |
| Sinh viên | `demo002@example.test` |
| VIP | `demo003@example.test` |

## Đặt vé, thanh toán demo và hội viên

1. Mở `/welcome.html`, chọn phim và sang `/booking.html` để chọn ngày/suất.
2. Chọn 1–8 vị trí ghế, đăng nhập rồi giữ cả tập ghế tại server trong 7 phút. Tải lại trang không gia hạn.
3. Chọn tối đa 5 combo, biến thể bắp/nước hoặc bỏ qua.
4. Chọn voucher đã nhận trong ví, điểm muốn dùng và phương thức thanh toán. Server tính và chốt giá; ưu đãi mặc định không cộng dồn.
5. Tạo đơn, sang trạm thanh toán thử và chọn kết quả. Thành công mới phát vé QR; thất bại có thể thử lại khi còn hạn. Đơn, lịch sử các lần trả tiền, điểm và quyền lợi được lưu trong tài khoản.

Các phương thức: MoMo, ZaloPay, ShopeePay, VNPAY QR, ATM, thẻ quốc tế, chuyển khoản, tại quầy; tất cả đều **mô phỏng nội bộ, không thu tiền thật**. QR thanh toán chứa dữ liệu `N6-DEMO`, không phải mã ngân hàng. Không nhập thẻ/CVV/OTP thật. Đơn được điểm phủ hết giá trị hoàn tất nội bộ bằng `LOYALTY`.

Tại quầy áp dụng cho suất còn ít nhất 60 phút; hạn tối đa 15 phút tính từ lúc giữ đầu tiên, không được làm mới bằng cách đổi phương thức. Admin xác nhận thu tiền thử cả đơn. Các callback lặp/đến muộn có xử lý riêng để không cấp vé, cộng điểm hoặc thu thử hai lần.

Trang `/account.html` có tổng quan, lịch sử đặt vé, sổ điểm, ví voucher, quyền lợi hạng, hồ sơ, đổi mật khẩu và thông báo. Đổi email cần mật khẩu hiện tại. Đổi mật khẩu khiến các phiên cũ phải đăng nhập lại.

| Hạng N6 | Chi tiêu ròng trong 365 ngày | Tích điểm trên tiền đủ điều kiện |
|---|---:|---:|
| Member | Dưới 1.500.000đ | 3% |
| Silver | Từ 1.500.000đ | 4% |
| Gold | Từ 3.000.000đ | 5% |
| Platinum | Từ 6.000.000đ | 7% |

1 điểm = 1.000đ, dùng tối thiểu 10 điểm, hạn 12 tháng. Điểm đang giữ trong đơn khác không dùng lại được. Phần trả bằng điểm không sinh điểm/chi tiêu mới. Hạng hội viên độc lập với ưu đãi sinh viên/VIP cũ; dữ liệu vé seed/legacy không tự tạo điểm.

Các chương trình mẫu: `N6WELCOME30` (đơn đầu từ 250.000đ, giảm 30.000đ), `N6WEEKDAY10` (vé ngày thường giảm 10%, tối đa 30.000đ), đổi 60 điểm lấy voucher combo. Nhận mã tại `/offers.html` hoặc ví voucher trước khi sử dụng. Danh mục combo/chính sách demo hiện được cấu hình trong các lớp service, chưa có màn hình CRUD chiến dịch.

Đơn đã trả tiền, chưa sử dụng vé/nhận combo và còn ít nhất 60 phút trước suất được gửi yêu cầu hoàn toàn bộ. Admin duyệt/từ chối; hoàn thử trả tiền/điểm/voucher theo chính sách và đảo điểm đã tích đúng một lần. Chưa hỗ trợ đổi suất hoặc hoàn một phần. Admin check-in QR trong khoảng 60 phút trước suất đến khi kết thúc; sử dụng vé và nhận combo là hai trạng thái riêng.

### Lưu trữ và dữ liệu cũ

`commerce-state.json` trong `CINEMA_DATA_DIR` lưu nguyên tử đơn, giữ ghế, lần trả tiền, hoàn tiền, vé QR, điểm và voucher; được `.gitignore` bỏ qua. Mọi thao tác tranh chấp dùng chung khóa của repository vé cũ. Dự án hỗ trợ một tiến trình ghi; chưa dùng cho nhiều server đồng thời.

Khi khởi động, vé PENDING cũ chưa có hạn được sao lưu vào `data/migration-backups/`, sau đó cấp hạn giữ 15 phút. Vé cũ vẫn hiển thị riêng trong lịch sử, không đoán thời điểm mua hoặc cấp điểm ngược. Trạng thái hết hạn không còn chiếm ghế. Thay/xóa suất, phòng, ghế hoặc phim đang có quyền giữ/vé chưa dùng bị chặn.

Bật/tắt trạm xác nhận thử bằng `COMMERCE_DEMO_ENABLED` (mặc định `true`). Tắt không biến hệ thống thành cổng thanh toán thật; các lệnh xác nhận mô phỏng bị từ chối.

### Cấu trúc OOP để thuyết trình

- `Customer extends User`; `OrderItem` có `TicketItem` và `ComboItem`.
- `Order` đóng gói các chuyển trạng thái; controller không tự gán trạng thái đã trả tiền.
- `PaymentMethod` và các implementation ví/thẻ/chuyển khoản/tại quầy thể hiện đa hình.
- `DiscountStrategy` gồm giảm tiền, giảm phần trăm và combo; chính sách hội viên nằm trong `LoyaltyService`.
- `PricingService`, `VoucherService`, `SeatInventoryService`, `CommerceService` chia trách nhiệm tính giá, quyền lợi, ghế và điều phối.
- `CommerceRepository` có implementation JSON nguyên tử và bộ nhớ cho kiểm thử; domain không gọi HTTP hoặc tự ghi file.

Xem [đặc tả và sơ đồ UML](docs/booking-payment-membership-oop-design.md) và [kế hoạch triển khai](docs/superpowers/plans/2026-11-03-cinema-commerce.md).

## Kiểm thử

```bash
mvn clean test
mvn package
java -jar target/Movie-Ticket-Booking-0.0.1-SNAPSHOT.jar
```

Kiểm thử bao gồm giữ ghế đồng thời, báo giá tại server, quyền sở hữu/CSRF, retry/idempotency, callback lặp và đến muộn, lỗi ghi file/khởi động lại, voucher/điểm đang giữ, hoàn điểm theo nguồn, hạng hội viên, QR, check-in so với hoàn vé và vô hiệu phiên sau đổi mật khẩu. Integration sử dụng thư mục tạm, không ghi đè dữ liệu local.

Để chạy giao diện cùng API khi phát triển: `mvn spring-boot:run`. Sau khi thay file HTML/JS với JAR đang chạy, cần đóng server và build/chạy lại JAR để nhận static resources mới.
