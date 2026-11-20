# Dữ liệu mẫu

Thư mục mặc định có 30 phim, phòng và ghế mẫu, cùng 48 suất chiếu theo ngày cố định. Để tạo lịch chiếu mới, chạy script trong một thư mục rỗng.

```sh
python3 scripts/seed-demo.py --directory /tmp/neon-cinema-demo
CINEMA_DATA_DIR=/tmp/neon-cinema-demo mvn spring-boot:run
```

Bộ demo gồm 30 phim hư cấu (24 đang chiếu và 6 sắp chiếu), 2 phòng, 80 ghế và 56 suất trong 7 ngày kể từ ngày mai theo giờ Việt Nam.

Đăng ký tài khoản khách trong ứng dụng. Dùng `ADMIN_EMAIL` và `ADMIN_PASSWORD` để tạo tài khoản quản trị nếu cần.
