package dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import model.CustomerType;

import java.util.Locale;

public record CustomerRequest(
        @NotBlank(message = "Họ tên không được để trống.")
        @Size(min = 2, max = 100, message = "Họ tên phải có từ 2 đến 100 ký tự.")
        @Pattern(regexp = "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$", message = "Họ tên không hợp lệ.")
        String fullName,
        @NotBlank(message = "Số điện thoại không được để trống.")
        @Pattern(regexp = "0[1-9][0-9]{8}", message = "Số điện thoại phải có 10 chữ số, bắt đầu bằng 0.")
        String phone,
        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không đúng định dạng.")
        @Size(max = 254, message = "Email không được dài quá 254 ký tự.")
        String email,
        @NotNull(message = "Vui lòng chọn loại khách hàng.") CustomerType customerType,
        @Size(max = 20, message = "Mã sinh viên không được dài quá 20 ký tự.") String studentId
) {
    public CustomerRequest {
        fullName = fullName == null ? null : fullName.strip().replaceAll("\\s+", " ");
        phone = phone == null ? null : phone.strip().replaceAll("[\\s().-]", "");
        if (phone != null && phone.startsWith("+84")) {
            phone = "0" + phone.substring(3);
        }
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        studentId = studentId == null || studentId.isBlank()
                ? null : studentId.strip().toUpperCase(Locale.ROOT);
    }
}
