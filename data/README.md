# Dữ liệu mẫu N6 Cinema

Tạo dữ liệu ngay trong thư mục `data`:

```bash
python3 data/seed.py
```

Script dùng thư viện chuẩn Python 3.9+, giữ nguyên bản ghi có sẵn, bỏ qua ID
đã tồn tại và sao lưu các file JSON cũ vào thư mục tạm `n6-seed-backup-*`.
Chạy khi ứng dụng không có thao tác ghi/đặt vé; các repository đọc lại file
ở mỗi request nên chỉ cần tải lại trang sau khi tạo dữ liệu.

Bộ mặc định gồm:

| File | Số bản ghi mới khi thư mục chưa có dữ liệu |
|---|---:|
| `movies.json` | 48 phim hư cấu: 32 đang chiếu, 12 sắp chiếu, 4 đã kết thúc |
| `cinemaRooms.json` | 8 phòng |
| `seats.json` | 800 ghế thường/VIP/đôi; có ghế bảo trì/ngừng sử dụng |
| `showtimes.json` | 1.520 suất: 7 ngày trước, hôm nay và 30 ngày tiếp theo |
| `customers.json` | 120 khách giả: 40 thường, 40 sinh viên, 40 VIP |
| `accounts.json` | 120 tài khoản khách và 1 tài khoản quản trị mẫu |
| `tickets.json` | 3.040 vé: thành công, chờ thanh toán, thất bại và hoàn tiền |

Ngày được tính theo `Asia/Ho_Chi_Minh` tại lúc chạy. Các suất cách nhau 3 giờ,
có giá cuối tuần/buổi tối, không trùng phòng hoặc ghế đang giữ chỗ. Phim sắp
chiếu chỉ có lịch từ ngày thứ 14; phim đã kết thúc chỉ có lịch trong quá khứ.
Giá vé mẫu gồm phụ phí ghế và giảm giá mặc định 10% sinh viên, 20% VIP.
Nếu bạn thay tỷ lệ giảm giá trong cấu hình, vé mẫu vẫn giữ giá lịch sử đã tạo.

## Tài khoản thử trên máy local

Mật khẩu chung: **`N6Demo!2026`**. Đây là tài khoản demo công khai cho local.

| Vai trò | Email |
|---|---|
| Admin | `demo.admin@example.test` |
| Khách thường | `demo001@example.test` |
| Sinh viên | `demo002@example.test` |
| VIP | `demo003@example.test` |

Các email khách tiếp tục đến `demo120@example.test`, lặp lại ba hạng trên.
Mật khẩu trong `accounts.json` được lưu bằng BCrypt. Email `example.test`
và thông tin khách đều là dữ liệu giả, không gửi email/SMS tới các số mẫu.

## Tạo một bộ dữ liệu riêng hoặc lớn hơn

```bash
python3 data/seed.py --directory /tmp/n6-large-demo --days 60 --past-days 14
CINEMA_DATA_DIR=/tmp/n6-large-demo mvn spring-boot:run
```

Chạy lại trong cùng ngày không tạo bản ghi trùng. Chạy vào ngày khác sẽ bổ
sung các ngày mới còn thiếu, giữ nguyên vé và dữ liệu đã sửa. Các file
`customers.json`, `accounts.json`, `tickets.json` đang được `.gitignore` loại
khỏi Git; script này cho phép tái tạo chúng trên máy khác.

`movies.js` và `Showtimes.js` là file cũ, backend sử dụng các file `.json`.
