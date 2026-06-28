# Test cases cho các flow Auth

Tài liệu này được viết dựa trên code hiện tại của backend Spring Boot và frontend Angular.

Nguồn đối chiếu chính:
- Backend: `AuthController`, `AuthService`, các DTO trong `src/main/java/hcmute/edu/zentech/dto/request`
- Frontend: `AuthService`, `LoginComponent`, `RegisterComponent`, `ForgotPasswordComponent`, `ResetPasswordComponent`, `ManagementChangePasswordPage`, `AdminChangePasswordComponent`

## 1. Phạm vi kiểm thử

Các flow được bao phủ:
- Đăng ký tài khoản khách hàng
- Đăng nhập bằng email/mật khẩu
- Đăng nhập Google
- Quên mật khẩu
- Đặt lại mật khẩu bằng token
- Đổi mật khẩu hoặc đặt mật khẩu lần đầu cho tài khoản Google

## 2. Endpoint liên quan

| Flow | Method | Endpoint | Auth token |
|---|---:|---|---|
| Đăng ký | POST | `/api/auth/register` | Không |
| Đăng nhập | POST | `/api/auth/login` | Không |
| Đăng nhập Google | POST | `/api/auth/google` | Không |
| Quên mật khẩu | POST | `/api/auth/forgot-password` | Không |
| Đặt lại mật khẩu | POST | `/api/auth/reset-password` | Không |
| Đổi mật khẩu | PUT | `/api/auth/password` | Có |

## 3. Dữ liệu test đề xuất

| Biến | Giá trị mẫu | Ghi chú |
|---|---|---|
| Email hợp lệ mới | `qa.customer+auth01@example.com` | Chưa tồn tại trong DB |
| Email đã tồn tại | Email đã đăng ký trước đó | Dùng cho case trùng email |
| Email không tồn tại | `qa.notfound@example.com` | Dùng cho quên mật khẩu |
| Mật khẩu hợp lệ | `Password@123` | Tối thiểu 6 ký tự, có chữ hoa và ký tự đặc biệt |
| Mật khẩu thiếu chữ hoa | `password@123` | Không đạt rule đăng ký/reset |
| Mật khẩu thiếu ký tự đặc biệt | `Password123` | Không đạt rule đăng ký/reset |
| Mật khẩu ngắn | `P@123` | Không đạt min length |
| Google ID token hợp lệ | Token lấy từ Google Identity Services | Dùng test tích hợp |
| Google ID token không hợp lệ | `invalid.google.token` | Dùng test lỗi xác thực |

## 4. Test cases đăng ký

### AUTH-REG-001 - Đăng ký thành công với dữ liệu hợp lệ (Pass)

Tiền điều kiện: Email chưa tồn tại.

Các bước:
1. Mở `/auth/register`.
2. Nhập `fullName`, email hợp lệ, mật khẩu `Password@123`, xác nhận mật khẩu trùng.
3. Bấm đăng ký.

Kết quả mong đợi:
- FE gọi `POST /api/auth/register` với `role = CUSTOMER`.
- Backend tạo `AccountUser` role `CUSTOMER`, `isActive = true`, `isPasswordSet = true`.
- Backend tạo hồ sơ `Customer`.
- Response 200 với thông báo đăng ký thành công.
- FE hiển thị toast thành công và điều hướng về `/auth/login`.

### AUTH-REG-002 - Không cho submit khi email trống (Pass)

Các bước:
1. Mở `/auth/register`.
2. Để trống email, nhập các trường còn lại hợp lệ.
3. Bấm đăng ký.

Kết quả mong đợi:
- Form báo lỗi email bắt buộc.
- Không gọi API.

### AUTH-REG-003 - Không cho submit khi email sai định dạng (Pass)

Các bước:
1. Nhập email `abc`.
2. Nhập mật khẩu hợp lệ và xác nhận trùng.
3. Bấm đăng ký.

Kết quả mong đợi:
- Form báo lỗi email sai định dạng.
- Không gọi API.

### AUTH-REG-004 - Không cho submit khi mật khẩu dưới 6 ký tự (Pass)

Các bước:
1. Nhập mật khẩu `P@123`.
2. Nhập xác nhận mật khẩu trùng.
3. Bấm đăng ký.

Kết quả mong đợi:
- Form báo lỗi độ dài tối thiểu.
- Không gọi API.
- Nếu gọi API trực tiếp, backend trả lỗi validation.

### AUTH-REG-005 - Không cho submit khi mật khẩu thiếu chữ hoa (Pass)

Các bước:
1. Nhập mật khẩu `password@123`.
2. Nhập xác nhận mật khẩu trùng.
3. Bấm đăng ký.

Kết quả mong đợi:
- Form báo lỗi mật khẩu không đạt độ phức tạp.
- Không gọi API.
- Nếu gọi API trực tiếp, backend trả lỗi validation theo pattern.

### AUTH-REG-006 - Không cho submit khi mật khẩu thiếu ký tự đặc biệt (Pass)

Các bước:
1. Nhập mật khẩu `Password123`.
2. Nhập xác nhận mật khẩu trùng.
3. Bấm đăng ký.

Kết quả mong đợi:
- Form báo lỗi mật khẩu không đạt độ phức tạp.
- Không gọi API.
- Nếu gọi API trực tiếp, backend trả lỗi validation theo pattern.

### AUTH-REG-007 - Không cho submit khi xác nhận mật khẩu không khớp (Pass)

Các bước:
1. Nhập mật khẩu `Password@123`.
2. Nhập xác nhận mật khẩu `Password@456`.
3. Bấm đăng ký.

Kết quả mong đợi:
- Form báo lỗi xác nhận mật khẩu không khớp.
- Không gọi API.

### AUTH-REG-008 - Đăng ký thất bại khi email đã tồn tại (Pass)

Tiền điều kiện: Email đã có trong DB.

Các bước:
1. Nhập email đã tồn tại.
2. Nhập mật khẩu hợp lệ và xác nhận trùng.
3. Bấm đăng ký.

Kết quả mong đợi:
- FE gọi `POST /api/auth/register`.
- Backend trả lỗi với thông báo email đã được sử dụng.
- FE hiển thị toast lỗi.
- Không tạo thêm tài khoản hoặc customer trùng.

### AUTH-REG-009 - Đăng ký với fullName trống (Pass) => Cần improve lại buộc phải nhập full name

Các bước:
1. Để trống `fullName`.
2. Nhập email mới, mật khẩu hợp lệ và xác nhận trùng.
3. Bấm đăng ký.

Kết quả mong đợi:
- Đăng ký thành công.
- Backend tạo customer với tên mặc định nếu `fullName` là `null`; nếu FE gửi chuỗi rỗng sau `trim`, cần kiểm tra dữ liệu lưu thực tế.

## 5. Test cases đăng nhập email/mật khẩu

### AUTH-LOGIN-001 - Đăng nhập thành công role CUSTOMER (Pass)

Tiền điều kiện: Tài khoản customer active, có mật khẩu đúng.

Các bước:
1. Mở `/auth/login`.
2. Nhập email và mật khẩu đúng.
3. Bấm đăng nhập.

Kết quả mong đợi:
- FE gọi `POST /api/auth/login`.
- Response có `accessToken`, `refreshToken`, `accountId`, `email`, `roles`, `imageUrl`, `isPasswordSet`.
- FE lưu session.
- FE hiển thị toast đăng nhập thành công.
- Nếu không có `returnUrl`, điều hướng về `/`.

### AUTH-LOGIN-002 - Đăng nhập thành công role ADMIN (Pass)

Tiền điều kiện: Tài khoản admin active.

Các bước:
1. Đăng nhập bằng tài khoản admin.

Kết quả mong đợi:
- Response chứa role admin.
- FE điều hướng tới `/admin/dashboard`.

### AUTH-LOGIN-003 - Đăng nhập thành công role OWNER/MANAGER/EMPLOYEE (Pass)

Tiền điều kiện: Tài khoản quản trị vận hành active.

Các bước:
1. Đăng nhập bằng tài khoản có role `OWNER`, `MANAGER` hoặc `EMPLOYEE`.

Kết quả mong đợi:
- FE điều hướng tới `/management/dashboard`.

### AUTH-LOGIN-004 - Đăng nhập customer với returnUrl hợp lệ (Pass)

Tiền điều kiện: URL hiện tại là `/auth/login?returnUrl=/cart`.

Các bước:
1. Đăng nhập bằng tài khoản customer hợp lệ.

Kết quả mong đợi:
- FE điều hướng tới `/cart`.

### AUTH-LOGIN-005 - Chặn returnUrl không an toàn (Pass)

Tiền điều kiện: URL hiện tại là `/auth/login?returnUrl=https://evil.example`.

Các bước:
1. Đăng nhập bằng tài khoản customer hợp lệ.

Kết quả mong đợi:
- FE không điều hướng tới domain ngoài.
- FE điều hướng về `/`.

### AUTH-LOGIN-006 - Không cho submit khi email trống (Pass)

Các bước:
1. Để trống email.
2. Nhập mật khẩu.
3. Bấm đăng nhập.

Kết quả mong đợi:
- Form báo lỗi email bắt buộc.
- Không gọi API.

### AUTH-LOGIN-007 - Không cho submit khi email sai định dạng (Pass)

Các bước:
1. Nhập email `abc`.
2. Nhập mật khẩu.
3. Bấm đăng nhập.

Kết quả mong đợi:
- Form báo lỗi email sai định dạng.
- Không gọi API.

### AUTH-LOGIN-008 - Không cho submit khi mật khẩu trống (Pass)

Các bước:
1. Nhập email hợp lệ.
2. Để trống mật khẩu.
3. Bấm đăng nhập.

Kết quả mong đợi:
- Form báo lỗi mật khẩu bắt buộc.
- Không gọi API.

### AUTH-LOGIN-009 - Đăng nhập thất bại khi sai mật khẩu (Pass)

Các bước:
1. Nhập email tồn tại.
2. Nhập mật khẩu sai.
3. Bấm đăng nhập.

Kết quả mong đợi:
- Backend trả lỗi "Email hoặc mật khẩu không chính xác."
- FE hiển thị toast lỗi.
- Không lưu session.

### AUTH-LOGIN-010 - Đăng nhập thất bại khi tài khoản bị khóa hoặc inactive (Pass)

Tiền điều kiện: Tài khoản có `isActive = false` hoặc bị disabled/locked.

Các bước:
1. Đăng nhập bằng email và mật khẩu đúng.

Kết quả mong đợi:
- Backend từ chối đăng nhập.
- FE hiển thị toast lỗi phù hợp.
- Không lưu token.

## 6. Test cases đăng nhập Google

### AUTH-GOOGLE-001 - Đăng nhập Google thành công với email chưa tồn tại (Pass)

Tiền điều kiện: Google ID token hợp lệ, email chưa tồn tại trong DB.

Các bước:
1. Mở `/auth/login`.
2. Bấm đăng nhập Google và chọn tài khoản Google hợp lệ.

Kết quả mong đợi:
- FE nhận `credential` từ Google Identity Services.
- FE gọi `POST /api/auth/google` với `{ token }`.
- Backend verify token thành công.
- Backend tạo `AccountUser` role `CUSTOMER`, `isActive = true`, `isPasswordSet = false`.
- Backend tạo `Customer` với tên và ảnh từ Google nếu có.
- Response có token đăng nhập và `isPasswordSet = false`.
- FE lưu session, toast thành công và điều hướng theo role.

### AUTH-GOOGLE-002 - Đăng nhập Google thành công với customer đã tồn tại (Pass)

Tiền điều kiện: Email Google đã có trong DB, role `CUSTOMER`, tài khoản active.

Các bước:
1. Đăng nhập Google bằng email đó.

Kết quả mong đợi:
- Backend không tạo account mới.
- Nếu customer chưa có `imageUrl` và Google có ảnh, backend cập nhật ảnh.
- Response có access token, refresh token và thông tin account.

### AUTH-GOOGLE-003 - Đăng nhập Google thành công với nhân sự đã tồn tại (Pass)

Tiền điều kiện: Email Google đã có trong DB với role khác `CUSTOMER`, tài khoản active.

Các bước:
1. Đăng nhập Google bằng email đó.

Kết quả mong đợi:
- Backend dùng account hiện có.
- Nếu chưa có hồ sơ employee thì tạo employee.
- Nếu employee chưa có ảnh và Google có ảnh thì cập nhật ảnh.
- FE điều hướng tới `/admin/dashboard` hoặc `/management/dashboard` theo role.

### AUTH-GOOGLE-004 - Đăng nhập Google thất bại khi token rỗng (Pass)

Các bước:
1. Gọi trực tiếp `POST /api/auth/google` với `{ "token": "" }`.

Kết quả mong đợi:
- Backend trả lỗi validation `Token không được để trống`.
- Không tạo tài khoản.

### AUTH-GOOGLE-005 - Đăng nhập Google thất bại khi token không hợp lệ hoặc hết hạn (Pass)

Các bước:
1. Gọi `POST /api/auth/google` với token không hợp lệ.

Kết quả mong đợi:
- Backend trả lỗi xác thực Google thất bại hoặc token hết hạn.
- FE hiển thị toast "Đăng nhập Google thất bại" hoặc thông báo lỗi từ backend.
- Không lưu session.

### AUTH-GOOGLE-006 - Đăng nhập Google thất bại khi tài khoản đã tồn tại nhưng bị khóa (Pass)

Tiền điều kiện: Email Google đã có trong DB, `isActive = false`.

Các bước:
1. Đăng nhập Google bằng email đó.

Kết quả mong đợi:
- Backend từ chối đăng nhập.
- Thông báo tài khoản đã bị khóa.
- Không cấp token.

## 7. Test cases quên mật khẩu

### AUTH-FORGOT-001 - Gửi email khôi phục thành công (Pass)

Tiền điều kiện: Email tồn tại, tài khoản có `isPasswordSet = true`.

Các bước:
1. Mở `/auth/forgot-password`.
2. Nhập email hợp lệ.
3. Bấm gửi yêu cầu.

Kết quả mong đợi:
- FE gọi `POST /api/auth/forgot-password`.
- Backend tạo bản ghi `PasswordResetToken` với thời hạn 10 phút.
- Backend gửi email chứa link dạng `{frontendUrl}/reset-password?token={token}`.
- FE hiển thị toast yêu cầu đã được xử lý.

### AUTH-FORGOT-002 - Không cho submit khi email trống (Pass)

Các bước:
1. Để trống email.
2. Bấm gửi yêu cầu.

Kết quả mong đợi:
- Form báo lỗi email bắt buộc.
- Không gọi API.

### AUTH-FORGOT-003 - Không cho submit khi email sai định dạng (Pass)

Các bước:
1. Nhập email `abc`.
2. Bấm gửi yêu cầu.

Kết quả mong đợi:
- Form báo lỗi email sai định dạng.
- Không gọi API.

### AUTH-FORGOT-004 - Quên mật khẩu thất bại khi email không tồn tại (Pass)

Các bước:
1. Nhập email không tồn tại.
2. Bấm gửi yêu cầu.

Kết quả mong đợi:
- Backend trả lỗi không tìm thấy tài khoản với email này.
- FE hiển thị toast lỗi.
- Không tạo reset token.

### AUTH-FORGOT-005 - Quên mật khẩu thất bại với tài khoản Google chưa đặt mật khẩu (Pass)

Tiền điều kiện: Account được tạo bằng Google, `isPasswordSet = false`.

Các bước:
1. Nhập email của tài khoản Google.
2. Bấm gửi yêu cầu.

Kết quả mong đợi:
- Backend trả lỗi yêu cầu đăng nhập Google trước rồi đặt mật khẩu trong hồ sơ.
- Không tạo reset token.
- FE hiển thị toast lỗi.

## 8. Test cases đặt lại mật khẩu bằng token (Pass)

### AUTH-RESET-001 - Đặt lại mật khẩu thành công

Tiền điều kiện: Có reset token hợp lệ, chưa hết hạn.

Các bước:
1. Mở `/reset-password?token={validToken}` hoặc `/auth/reset-password?token={validToken}` tùy route đang dùng.
2. Nhập mật khẩu mới `NewPassword@123`.
3. Nhập xác nhận mật khẩu trùng.
4. Bấm đổi mật khẩu.

Kết quả mong đợi:
- FE gọi `POST /api/auth/reset-password` với token và `newPassword`.
- Backend kiểm tra token tồn tại và chưa hết hạn.
- Backend cập nhật mật khẩu đã hash, set `isPasswordSet = true`.
- Backend xóa reset token sau khi dùng.
- FE hiển thị toast thành công và điều hướng về `/auth/login`.
- Đăng nhập được bằng mật khẩu mới.

### AUTH-RESET-002 - Không cho submit khi URL thiếu token (Pass)

Các bước:
1. Mở `/reset-password` không có query `token`.
2. Nhập mật khẩu hợp lệ và xác nhận trùng.
3. Bấm đổi mật khẩu.

Kết quả mong đợi:
- FE hiển thị lỗi link khôi phục không hợp lệ hoặc thiếu token.
- Không gọi API.

### AUTH-RESET-003 - Không cho submit khi mật khẩu mới trống (Pass)

Các bước:
1. Mở URL có token.
2. Để trống mật khẩu mới.
3. Bấm đổi mật khẩu.

Kết quả mong đợi:
- Form báo lỗi mật khẩu mới bắt buộc.
- Không gọi API.

### AUTH-RESET-004 - Không cho submit khi mật khẩu mới dưới 6 ký tự (Pass)

Các bước:
1. Nhập mật khẩu mới `N@123`.
2. Nhập xác nhận trùng.
3. Bấm đổi mật khẩu.

Kết quả mong đợi:
- Form báo lỗi độ dài tối thiểu.
- Không gọi API.

### AUTH-RESET-005 - Không cho submit khi mật khẩu mới thiếu chữ hoa (Pass)

Các bước:
1. Nhập mật khẩu mới `newpassword@123`.
2. Nhập xác nhận trùng.
3. Bấm đổi mật khẩu.

Kết quả mong đợi:
- Form báo lỗi mật khẩu không đạt độ phức tạp.
- Không gọi API.

### AUTH-RESET-006 - Không cho submit khi mật khẩu mới thiếu ký tự đặc biệt (Pass)

Các bước:
1. Nhập mật khẩu mới `NewPassword123`.
2. Nhập xác nhận trùng.
3. Bấm đổi mật khẩu.

Kết quả mong đợi:
- Form báo lỗi mật khẩu không đạt độ phức tạp.
- Không gọi API.

### AUTH-RESET-007 - Không cho submit khi xác nhận mật khẩu không khớp (Pass)

Các bước:
1. Nhập mật khẩu mới `NewPassword@123`.
2. Nhập xác nhận `OtherPassword@123`.
3. Bấm đổi mật khẩu.

Kết quả mong đợi:
- Form báo lỗi xác nhận mật khẩu không khớp.
- Không gọi API.

### AUTH-RESET-008 - Đặt lại mật khẩu thất bại khi token không tồn tại (Pass)

Các bước:
1. Gọi màn reset với token bất kỳ không có trong DB.
2. Nhập mật khẩu hợp lệ.
3. Bấm đổi mật khẩu.

Kết quả mong đợi:
- Backend trả lỗi link khôi phục không hợp lệ hoặc không tồn tại.
- FE hiển thị toast lỗi.
- Không cập nhật mật khẩu.

### AUTH-RESET-009 - Đặt lại mật khẩu thất bại khi token hết hạn (Pass)

Tiền điều kiện: Reset token đã quá 10 phút.

Các bước:
1. Mở link reset với token hết hạn.
2. Nhập mật khẩu hợp lệ.
3. Bấm đổi mật khẩu.

Kết quả mong đợi:
- Backend xóa token hết hạn.
- Backend trả lỗi token đã hết hạn.
- FE hiển thị toast lỗi.
- Không cập nhật mật khẩu.

### AUTH-RESET-010 - Không thể dùng lại token sau khi reset thành công (Pass)

Tiền điều kiện: Token đã được dùng thành công ở `AUTH-RESET-001`.

Các bước:
1. Mở lại link reset cũ.
2. Nhập mật khẩu hợp lệ khác.
3. Bấm đổi mật khẩu.

Kết quả mong đợi:
- Backend trả lỗi token không hợp lệ hoặc không tồn tại.
- Không cập nhật mật khẩu lần hai.

## 9. Test cases đổi mật khẩu

### AUTH-CHANGE-001 - Đổi mật khẩu thành công với tài khoản đã có mật khẩu (Pass)

Tiền điều kiện: Người dùng đã đăng nhập, `isPasswordSet = true`.

Các bước:
1. Mở màn đổi mật khẩu tương ứng role.
2. Nhập mật khẩu hiện tại đúng.
3. Nhập mật khẩu mới tối thiểu 6 ký tự.
4. Nhập xác nhận mật khẩu trùng.
5. Bấm cập nhật.

Kết quả mong đợi:
- FE gọi `PUT /api/auth/password` kèm access token.
- Payload có `currentPassword` và `newPassword`.
- Backend xác thực current password đúng.
- Backend cập nhật mật khẩu đã hash và set `isPasswordSet = true`.
- FE hiển thị toast thành công.
- Session cập nhật trạng thái `isPasswordSet = true`.
- Đăng nhập được bằng mật khẩu mới.

### AUTH-CHANGE-002 - Không cho submit khi thiếu mật khẩu hiện tại với tài khoản đã có mật khẩu (Pass)

Tiền điều kiện: Người dùng đã đăng nhập, `isPasswordSet = true`.

Các bước:
1. Để trống mật khẩu hiện tại.
2. Nhập mật khẩu mới và xác nhận trùng.
3. Bấm cập nhật.

Kết quả mong đợi:
- FE báo lỗi mật khẩu hiện tại bắt buộc.
- Không gọi API.
- Nếu gọi API trực tiếp, backend trả lỗi yêu cầu nhập mật khẩu hiện tại.

### AUTH-CHANGE-003 - Đổi mật khẩu thất bại khi mật khẩu hiện tại sai (Pass)

Tiền điều kiện: Người dùng đã đăng nhập, `isPasswordSet = true`.

Các bước:
1. Nhập mật khẩu hiện tại sai.
2. Nhập mật khẩu mới hợp lệ theo UI.
3. Nhập xác nhận trùng.
4. Bấm cập nhật.

Kết quả mong đợi:
- Backend trả lỗi mật khẩu hiện tại không đúng.
- FE hiển thị toast lỗi.
- Không cập nhật mật khẩu.

### AUTH-CHANGE-004 - Đặt mật khẩu lần đầu cho tài khoản Googlen(Pass)

Tiền điều kiện: Người dùng đăng nhập bằng Google, `isPasswordSet = false`.

Các bước:
1. Mở màn đổi mật khẩu.
2. Không nhập mật khẩu hiện tại.
3. Nhập mật khẩu mới tối thiểu 6 ký tự.
4. Nhập xác nhận trùng.
5. Bấm cập nhật.

Kết quả mong đợi:
- FE gửi payload với `currentPassword` rỗng và `newPassword`.
- Backend không yêu cầu kiểm tra current password vì `isPasswordSet = false`.
- Backend cập nhật mật khẩu và set `isPasswordSet = true`.
- FE hiển thị toast thành công và cập nhật session.
- Từ lần sau, màn đổi mật khẩu yêu cầu current password.

### AUTH-CHANGE-005 - Không cho submit khi mật khẩu mới trống (Pass)

Các bước:
1. Nhập mật khẩu hiện tại nếu cần.
2. Để trống mật khẩu mới.
3. Bấm cập nhật.

Kết quả mong đợi:
- FE báo lỗi mật khẩu mới bắt buộc.
- Không gọi API.

### AUTH-CHANGE-006 - Không cho submit khi mật khẩu mới dưới 6 ký tự (Pass)

Các bước:
1. Nhập mật khẩu mới `12345`.
2. Nhập xác nhận trùng.
3. Bấm cập nhật.

Kết quả mong đợi:
- FE báo lỗi mật khẩu mới phải có ít nhất 6 ký tự.
- Không gọi API.

### AUTH-CHANGE-007 - Không cho submit khi xác nhận mật khẩu không khớp (Pass)

Các bước:
1. Nhập mật khẩu mới `New123`.
2. Nhập xác nhận `Other123`.
3. Bấm cập nhật.

Kết quả mong đợi:
- FE báo lỗi xác nhận mật khẩu không khớp.
- Không gọi API.

### AUTH-CHANGE-008 - Đổi mật khẩu thất bại khi chưa đăng nhập (Pass)

Các bước:
1. Gọi trực tiếp `PUT /api/auth/password` không có access token.

Kết quả mong đợi:
- Backend trả 401 Unauthorized.
- Không cập nhật mật khẩu.

### AUTH-CHANGE-009 - Đổi mật khẩu thất bại khi access token hết hạn (Pass)

Tiền điều kiện: Access token hết hạn, refresh token không thể refresh hoặc không có.

Các bước:
1. Gọi `PUT /api/auth/password`.

Kết quả mong đợi:
- Backend trả 401 hoặc FE bị điều hướng về `/auth/login` theo interceptor.
- Không cập nhật mật khẩu.

### AUTH-CHANGE-010 - Ghi nhận khác biệt rule mật khẩu giữa reset/register và change password (Pass)

Mục tiêu: Xác nhận behavior hiện tại của code.

Các bước:
1. Với user đã đăng nhập, nhập mật khẩu mới `abcdef`.
2. Nhập xác nhận trùng.
3. Bấm cập nhật.

Kết quả mong đợi hiện tại:
- FE chỉ kiểm tra tối thiểu 6 ký tự.
- Backend `ChangePasswordRequest` chỉ kiểm tra `newPassword` không rỗng.
- Hệ thống có thể cho đổi sang mật khẩu không có chữ hoa/ký tự đặc biệt.

Ghi chú: Nếu yêu cầu nghiệp vụ muốn mật khẩu luôn mạnh, cần bổ sung validation pattern cho `ChangePasswordRequest` và FE đổi mật khẩu.

## 10. Test cases bảo mật và regression chung

### AUTH-SEC-001 - Không log plaintext password hoặc token nhạy cảm

Các bước:
1. Thực hiện đăng nhập, đăng ký, reset password, đổi mật khẩu.
2. Kiểm tra frontend log và backend log.

Kết quả mong đợi:
- Không ghi plaintext password.
- Token, refresh token và authorization header không bị log trực tiếp.

### AUTH-SEC-002 - API public không tự gắn access token

Các bước:
1. Kiểm tra network khi gọi login/register/google/forgot/reset.

Kết quả mong đợi:
- FE dùng context bỏ qua auth token cho các API public.
- Request không phụ thuộc session cũ.

### AUTH-SEC-003 - Response đăng nhập không thiếu trường bắt buộc

Các bước:
1. Đăng nhập email và Google thành công.
2. Kiểm tra body response.

Kết quả mong đợi:
- Có `accessToken`, `refreshToken`, `accountId`, `email`, `roles`.
- Có `isPasswordSet` để FE xác định có cần đặt mật khẩu lần đầu hay không.

### AUTH-SEC-004 - Refresh/logout không làm hỏng flow auth chính

Các bước:
1. Đăng nhập thành công.
2. Refresh token.
3. Logout.
4. Thử truy cập API cần auth.

Kết quả mong đợi:
- Refresh token cấp access token mới.
- Logout xóa refresh token.
- Sau logout, API cần auth bị từ chối nếu không có token hợp lệ.

## 11. Ghi chú kiểm thử tự động đề xuất

Nên ưu tiên automated tests ở các tầng:
- Backend controller/service tests cho validation và lỗi nghiệp vụ.
- Frontend component/store tests cho validation, toast và điều hướng.
- E2E tests cho happy path: đăng ký, đăng nhập, quên mật khẩu, reset mật khẩu, đổi mật khẩu.

Các case nên tự động hóa sớm:
- `AUTH-REG-001`, `AUTH-REG-008`
- `AUTH-LOGIN-001`, `AUTH-LOGIN-004`, `AUTH-LOGIN-005`, `AUTH-LOGIN-009`
- `AUTH-GOOGLE-004`, `AUTH-GOOGLE-005`; với Google thật nên mock token verifier ở backend test
- `AUTH-FORGOT-001`, `AUTH-FORGOT-004`, `AUTH-FORGOT-005`
- `AUTH-RESET-001`, `AUTH-RESET-008`, `AUTH-RESET-009`, `AUTH-RESET-010`
- `AUTH-CHANGE-001`, `AUTH-CHANGE-003`, `AUTH-CHANGE-004`, `AUTH-CHANGE-008`
