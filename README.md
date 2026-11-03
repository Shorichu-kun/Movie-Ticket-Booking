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
| Khách hàng | Chỉnh sửa hồ sơ, chọn suất/ghế, nhận báo giá, đặt vé, xem vé của mình, hủy vé chưa thanh toán |
| Admin | Quản lý phim, lịch chiếu, phòng/ghế, khách hàng và hạng VIP; xem toàn bộ vé và xác nhận đã thu tiền tại quầy |

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

## Luồng đặt vé

1. Đăng nhập tài khoản khách hàng; chọn phim và suất còn ở tương lai.
2. Chọn 1–20 ghế đang hoạt động, thuộc đúng phòng và chưa có vé đang giữ chỗ.
3. Backend tính `(giá suất + phụ phí ghế) × (1 − ưu đãi khách hàng)` cho từng ghế. Phụ phí: thường 0đ, VIP 20.000đ, đôi 50.000đ.
4. Backend khóa thao tác đặt vé trong tiến trình và lưu toàn bộ ghế trong một lần; một ghế xung đột làm cả yêu cầu bị từ chối. Giá và customerId trong client không được dùng để quyết định giá/quyền sở hữu.
5. Vé mới là `PENDING`: **thanh toán tại quầy**, chưa thu tiền trực tuyến. Admin xác nhận đã thu tiền sẽ chuyển `SUCCESS`. Hủy vé pending chuyển `FAILED` theo enum hiện có và giải phóng ghế.

Chưa tích hợp VNPay/MoMo/ngân hàng; không có thanh toán trực tuyến giả thành công. Vé pending hiện giữ ghế đến khi người dùng/admin hủy; chưa có cơ chế tự hết hạn giữ chỗ. Kiểm tra mã SV hiện kiểm tra định dạng và trùng mã trong hệ thống, chưa xác minh với trường. Giao diện poster là minh họa chữ, vì model Movie hiện chưa lưu ảnh/trailer.

## Kiểm thử

`mvn test` chạy kiểm thử Customer, Repository, policy giá và integration đăng ký/đăng nhập/phân quyền/CSRF/đặt ghế. Integration sử dụng thư mục tạm riêng. Kiểm thử bao gồm hai yêu cầu đặt cùng ghế, từ chối thay đổi hạng VIP, quyền sở hữu vé, lỗi lưu file, ngày chiếu đã qua và ghế không hoạt động.

Các file giao diện chính: `welcome.html`, `welcome.css`, `welcome.js`, `session.js`, `admin.html`, `admin.js`. Các màn hình quản lý cũ ở `movies.html`, `showtimes.html`, `rooms.html`, `customers.html`.
