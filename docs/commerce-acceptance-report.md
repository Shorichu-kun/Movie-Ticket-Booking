# Đánh giá đặt vé, thanh toán demo và hội viên N6

Ngày đối chiếu: 04/11/2026. Phạm vi: đồ án OOP, thanh toán mô phỏng nội bộ, một tiến trình ứng dụng.

## Căn cứ đối chiếu

- [CGV FAQ](https://www.cgv.vn/default/faq/): quy trình mua vé, thời hạn giữ ghế, điểm thưởng và chi tiêu xét hạng.
- [Chính sách giao dịch Beta](https://dev.cinemas.betacorp.vn/dieu-khoan/dieu-khoan-thanh-toan.htm): kiểm tra thông tin trước thanh toán, xác nhận đơn, xử lý giao dịch lỗi và các trường hợp hoàn.

Các trang được đối chiếu qua nội dung công khai được tìm kiếm lập chỉ mục. Đây là đánh giá các hành vi có thể kiểm tra của đồ án, không phải chứng nhận tương đương toàn bộ ứng dụng thương mại hoặc kiểm thử trực tiếp ứng dụng Beta/CGV.

## Kết quả theo tiêu chí

| Tiêu chí trải nghiệm | N6 đã triển khai và kiểm tra | Giới hạn |
|---|---|---|
| Đặt vé theo bước | Suất → ghế → combo → ưu đãi/thanh toán → vé điện tử | Một rạp, nhiều phòng |
| Giữ ghế | Độc quyền toàn bộ tập ghế, có hạn, không tăng hạn khi tải lại | JSON khóa chung trong một tiến trình |
| Giá minh bạch | Server tính vé, phụ phí, combo, ưu đãi, điểm và số tiền còn thu | Chính sách N6 riêng, không dùng giá của các rạp tham khảo |
| Nhiều phương thức trả tiền | 8 phương thức gồm ví/QR/thẻ/chuyển khoản/tại quầy và LOYALTY cho đơn 0đ | Mô phỏng, không kết nối cổng thật |
| Thanh toán thất bại và thử lại | Giữ lịch sử từng lần; chặn xử lý lặp và đối soát khoản thu muộn | Không có đối soát ngân hàng thật |
| Vé điện tử | QR có kiểm tra chủ sở hữu và trạng thái; check-in/nhận combo độc lập | Phiếu demo, không phải hóa đơn điện tử hợp lệ |
| Tài khoản | Tổng quan, lịch sử/chi tiết đơn, hồ sơ, đổi mật khẩu và thông báo | Không gửi email/SMS thật |
| Loyalty | Điểm theo lô/hạn, giữ điểm, ví voucher, đổi quà, sinh nhật, bốn hạng theo chi tiêu 365 ngày | Chính sách/chiến dịch mẫu trong code; chưa có CRUD chiến dịch |
| Hoàn | Yêu cầu/duyệt hoàn cả đơn, đảo tiền thử/điểm/voucher, giữ nguồn điểm khi hoàn nhiều đơn | Chưa đổi suất hoặc hoàn một phần |
| An toàn tài khoản | Session HttpOnly, CSRF, kiểm tra chủ sở hữu, đổi mật khẩu vô hiệu phiên cũ | Không có xác minh giấy tờ/sinh viên từ trường |
| Giao diện | Đã thử checkout trên trình duyệt; màn hình 390px không tràn ngang | Chưa chứng nhận WCAG hoặc kiểm thử mọi thiết bị |
| OOP | Order đóng gói trạng thái; OrderItem kế thừa; PaymentMethod/DiscountStrategy đa hình; repository trừu tượng | Chưa triển khai hạ tầng nhiều máy chủ |

## Các lỗi đã tìm và sửa trong quá trình kiểm tra

- API đặt vé cũ có thể chiếm ghế đang được luồng mới giữ; đã dùng chung khóa và kiểm tra tồn ghế.
- Xử lý callback lặp/đến muộn hoặc lần trả tiền đã bị thay thế có thể ghi nhận sai; đã bổ sung hoàn thử riêng và chống cấp vé/điểm lặp.
- Hoàn điểm theo nhiều thứ tự có thể tạo dư điểm hoặc mất khoản điểm phải bù; đã giữ nguồn điểm, nguồn nợ và lịch sử sử dụng để đảo chính xác.
- Điểm đã dùng rồi hết hạn cần được phục hồi ít nhất 30 ngày khi hoàn; đã bổ sung quy tắc và kiểm thử.
- Đơn đang giữ điểm có thể thiếu nguồn sau khi hoàn một đơn trước đó; được hủy/giải phóng nguyên tử và xử lý hoàn nếu kết quả thanh toán đến sau.
- Vé đã check-in không thể nhận combo sau; đã tách hai trạng thái và xử lý quét lặp an toàn.
- Thay số lượng combo có thể chưa cập nhật quote trước khi chuyển bước; đã đọc giá trị lựa chọn hiện tại khi lập request và cập nhật theo sự kiện nhập.
- Lượt kiểm tra cuối phát hiện cần bảo vệ cả vé legacy khi sửa/xóa danh mục, chuẩn hóa mã không phân biệt hoa/thường và mở lại nút tiếp tục sau khi giữ ghế mới thay lượt hết hạn. Có kiểm thử hồi quy cho các đường này.

## Bằng chứng kiểm thử

- Maven: **96 kiểm thử đạt, 0 thất bại, 0 lỗi** trên domain, repository, HTTP và bảo mật. Kiểm thử phục hồi checkout bằng Node: **2 kịch bản đạt**.
- Hành trình trình duyệt: một vé VIP + combo = 199.000đ; MoMo thử thất bại → VNPAY thử thành công → vé QR; tài khoản ghi 199.000đ chi tiêu và 5 điểm; nhận voucher N6WEEKDAY10 vào ví.
- Kiểm tra HTTP trên bộ seed riêng: MoMo, ZaloPay, ShopeePay, VNPAY, ATM, thẻ quốc tế, chuyển khoản, tại quầy; tạo đơn lặp, kết quả lặp, quyền sở hữu QR/đơn, hoàn toàn bộ, tổng chi tiêu và ví voucher đều đạt.
- Kiểm tra tĩnh JavaScript bằng `node --check`; kiểm tra định dạng Git bằng `git diff --check`.

## Kết luận

Bộ chức năng chính phù hợp để trình diễn đồ án OOP và luồng đặt vé có trạng thái. Các kết nối thanh toán, thông báo, hóa đơn và xác minh bên ngoài cần triển khai thêm trước khi sử dụng kinh doanh thực tế. Kiểm thử cuối: **96/96 Maven** và **2/2 kịch bản phục hồi checkout** đạt. Commit sửa ba lỗi tích hợp cuối: `bb8f994`.
