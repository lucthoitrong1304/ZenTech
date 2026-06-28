# Test cases cho quản lý tài khoản khách hàng

Tài liệu này được viết dựa trên code hiện tại của backend Spring Boot và frontend Angular.

Nguồn đối chiếu chính:
- Backend: `CustomerSelfController`, `CustomerSelfService`, `UploadController`, `UploadService`, `R2StorageService`, `OrderManagementService`, `AuthService`
- Frontend: `account.routes`, `customerAuthGuard`, `AccountService`, `AccountStore`, `AccountOverviewPageComponent`, `AddressBookPageComponent`, `OrderHistoryPageComponent`, `VoucherWalletPageComponent`

## 1. Phạm vi kiểm thử

Các flow được bao phủ:
- Truy cập khu vực tài khoản khách hàng
- Xem/cập nhật hồ sơ cá nhân
- Upload ảnh đại diện
- Đổi mật khẩu từ trang tài khoản
- Quản lý sổ địa chỉ
- Xem lịch sử đơn hàng và chi tiết đơn hàng
- Hủy đơn hàng
- Gửi yêu cầu trả hàng
- Xem ví voucher và sao chép mã voucher

## 2. Endpoint liên quan

| Flow | Method | Endpoint | Auth token |
|---|---:|---|---|
| Hồ sơ của tôi | GET | `/api/customers/me/profile` | Có, customer |
| Cập nhật hồ sơ | PATCH | `/api/customers/me/profile` | Có, customer |
| Danh sách địa chỉ | GET | `/api/customers/me/addresses` | Có, customer |
| Thêm địa chỉ | POST | `/api/customers/me/addresses` | Có, customer |
| Cập nhật địa chỉ | PUT | `/api/customers/me/addresses/{addressId}` | Có, owner |
| Đặt địa chỉ mặc định | PATCH | `/api/customers/me/addresses/{addressId}/default` | Có, owner |
| Xóa địa chỉ | DELETE | `/api/customers/me/addresses/{addressId}` | Có, owner |
| Lịch sử đơn hàng | GET | `/api/customers/me/orders` | Có, customer |
| Chi tiết đơn hàng | GET | `/api/customers/me/orders/{orderId}` | Có, owner |
| Hủy đơn hàng | POST | `/api/customers/me/orders/{orderId}/cancel` | Có, owner |
| Ví voucher | GET | `/api/customers/me/vouchers` | Có, customer |
| Gửi yêu cầu trả hàng | POST | `/api/customers/me/orders/{orderId}/return` | Có, owner |
| Presign upload | POST | `/api/uploads/presign` | Có |
| Đổi mật khẩu | PUT | `/api/auth/password` | Có |

## 3. Route và quyền truy cập

| Route FE | Màn hình |
|---|---|
| `/account` | Redirect tới `/account/overview` |
| `/account/overview` | Tổng quan tài khoản, hồ sơ, đổi mật khẩu |
| `/account/orders` | Lịch sử đơn hàng, chi tiết, hủy đơn, trả hàng |
| `/account/addresses` | Sổ địa chỉ |
| `/account/vouchers` | Ví voucher |

Rule truy cập:
- `accountRoutes` dùng `customerAuthGuard`.
- Nếu chưa đăng nhập, FE điều hướng về `/auth/login?returnUrl={currentUrl}`.
- Backend lấy customer theo account hiện tại; nếu account không có Customer profile thì trả access denied: `Only customers can access customer self-service APIs`.

## 4. Dữ liệu test đề xuất

| Biến | Giá trị mẫu | Ghi chú |
|---|---|---|
| Customer A | Tài khoản role `CUSTOMER`, active | Chủ dữ liệu cần test |
| Customer B | Tài khoản role `CUSTOMER`, active khác | Test cross-account |
| Staff user | OWNER/MANAGER/EMPLOYEE/ADMIN | Không được truy cập customer self-service nếu không có Customer profile |
| Tên hợp lệ | `Nguyen Van A` | Dùng cập nhật profile |
| Tên rỗng | `   ` | Backend từ chối khi field được gửi |
| SĐT hợp lệ | `0909123456` | Dùng địa chỉ |
| Ảnh avatar hợp lệ | JPEG/PNG/WEBP > 0 bytes | Backend hiện không giới hạn max avatar size rõ ràng |
| Ảnh avatar sai type | PDF/TXT | Bị từ chối |
| Đơn có thể hủy | `orderStatus = CREATED`, `paymentStatus = PENDING` | Customer được hủy |
| Đơn không thể hủy | `CONFIRMED`, `SHIPPED`, `COMPLETED` hoặc đã thanh toán | Bị từ chối |
| Đơn có thể trả hàng | `orderStatus = COMPLETED` | Customer được gửi return request |
| Voucher khả dụng | Chưa dùng, coupon active, chưa hết hạn | Trạng thái `AVAILABLE` |
| Voucher đã dùng | `usedAt != null` | Trạng thái `USED` |
| Voucher hết hạn | coupon inactive hoặc `endAt` trước hiện tại | Trạng thái `EXPIRED` |

## 5. Test cases truy cập tài khoản

### CUST-ACC-001 - Guest truy cập `/account/overview` (pass)

Các bước:
1. Đảm bảo chưa đăng nhập.
2. Mở `/account/overview`.

Kết quả mong đợi:
- `customerAuthGuard` chặn route.
- FE điều hướng tới `/auth/login?returnUrl=/account/overview`.
- Không gọi API `/api/customers/me/*`.

### CUST-ACC-002 - Customer truy cập account area thành công (pass)

Tiền điều kiện: Customer A đã đăng nhập.

Các bước:
1. Mở `/account`.

Kết quả mong đợi:
- FE redirect tới `/account/overview`.
- `AccountStore` onInit gọi:
  - `GET /api/customers/me/profile`
  - `GET /api/customers/me/addresses`
  - `GET /api/customers/me/orders?page=0&size=100&sort=createdAt,desc`
  - `GET /api/customers/me/vouchers?page=0&size=100&sort=issuedAt,desc`
- Dữ liệu hiển thị đúng theo Customer A.

### CUST-ACC-003 - Staff không có Customer profile gọi API customer self-service

Tiền điều kiện: Đăng nhập bằng staff/admin không có Customer profile.

Các bước:
1. Gọi `GET /api/customers/me/profile`.

Kết quả mong đợi:
- Backend trả 403/access denied.
- Không lộ dữ liệu customer.

### CUST-ACC-004 - Token hết hạn khi đang ở account area (pass)

Các bước:
1. Mở `/account/overview`.
2. Giả lập access token hết hạn và refresh không thành công.
3. Gọi lại API customer.

Kết quả mong đợi:
- API bị từ chối 401.
- FE điều hướng về login theo interceptor/guard.
- Không hiển thị dữ liệu stale như là dữ liệu mới.

## 6. Test cases hồ sơ cá nhân

### CUST-PROFILE-001 - Xem hồ sơ cá nhân thành công (pass)

Tiền điều kiện: Customer A đã đăng nhập.

Các bước:
1. Gọi `GET /api/customers/me/profile`.

Kết quả mong đợi:
- Response success.
- Data có `customerId`, `fullName`, `email`, `imageUrl`, `registeredAt`.
- Nếu `imageUrl` là object key không bắt đầu bằng `http`, backend convert sang URL hiển thị.
- FE cập nhật current user profile trong `AuthSessionStore`.

### CUST-PROFILE-002 - Cập nhật tên thành công (pass)

Các bước:
1. Mở `/account/overview`.
2. Bấm sửa thông tin.
3. Nhập tên mới hợp lệ.
4. Lưu.

Kết quả mong đợi:
- FE gọi `PATCH /api/customers/me/profile`.
- Backend trim `fullName` và lưu.
- FE hiển thị thông báo cập nhật thành công.
- Header/session cập nhật tên mới.

### CUST-PROFILE-003 - FE chặn lưu tên rỗng (pass)

Các bước:
1. Mở dialog sửa profile.
2. Nhập tên chỉ có khoảng trắng.
3. Lưu.

Kết quả mong đợi:
- FE không gọi API.
- Dialog không lưu dữ liệu rỗng.

### CUST-PROFILE-004 - API từ chối fullName rỗng khi field được gửi (pass)

Các bước:
1. Gọi `PATCH /api/customers/me/profile` với `{ "fullName": "   " }`.

Kết quả mong đợi:
- Backend ném lỗi `fullName is required`.
- Không cập nhật profile.

### CUST-PROFILE-005 - API từ chối fullName vượt 255 ký tự (pass)

Các bước:
1. Gọi API update profile với `fullName` dài hơn 255 ký tự.

Kết quả mong đợi:
- Backend trả lỗi validation `fullName must not exceed 255 characters`.

### CUST-PROFILE-006 - API từ chối imageUrl vượt 500 ký tự (pass)

Các bước:
1. Gọi API update profile với `imageUrl` dài hơn 500 ký tự.

Kết quả mong đợi:
- Backend trả lỗi validation `imageUrl must not exceed 500 characters`.

### CUST-PROFILE-007 - Upload avatar thành công (pass)

Tiền điều kiện: Customer A đã đăng nhập.

Các bước:
1. Chọn file avatar JPEG/PNG/WEBP hợp lệ.
2. FE gọi presign, upload lên R2, sau đó update profile.

Kết quả mong đợi:
- FE gọi `POST /api/uploads/presign` với `purpose = CUSTOMER_AVATAR`.
- Backend trả `presignedUrl`, `fileKey`, `method = PUT`, `requiredHeaders`.
- FE upload file lên R2 bằng `PUT` và bỏ qua JWT hệ thống.
- FE gọi `PATCH /api/customers/me/profile` với `imageUrl = fileKey`.
- Profile và session cập nhật ảnh mới.

### CUST-PROFILE-008 - Upload avatar sai content type (pass)

Các bước:
1. Gọi presign avatar với `contentType = application/pdf`.

Kết quả mong đợi:
- Backend trả lỗi chỉ cho JPEG/PNG/WEBP cho avatar.
- Không tạo presigned URL.

### CUST-PROFILE-009 - Upload avatar fileSize <= 0 (pass)

Các bước:
1. Gọi presign với `fileSize = 0`.

Kết quả mong đợi:
- Backend trả lỗi validation `fileSize must be greater than 0`.

### CUST-PROFILE-010 - Presign upload khi chưa đăng nhập (pass)

Các bước:
1. Gọi `POST /api/uploads/presign` không có token.

Kết quả mong đợi:
- Backend trả 401/403.
- Không trả presigned URL.

## 7. Test cases đổi mật khẩu từ tài khoản khách hàng

### CUST-PASS-001 - Đổi mật khẩu thành công với tài khoản đã có mật khẩu (pass)

Tiền điều kiện: Customer A đã đăng nhập, `isPasswordSet = true`.

Các bước:
1. Mở dialog đổi mật khẩu trong `/account/overview`.
2. Nhập mật khẩu hiện tại đúng.
3. Nhập mật khẩu mới `NewPassword@123`.
4. Xác nhận trùng.
5. Submit.

Kết quả mong đợi:
- FE gọi `PUT /api/auth/password`.
- Backend kiểm tra current password.
- Mật khẩu được hash và cập nhật.
- FE đóng dialog sau khi store báo thành công.

### CUST-PASS-002 - FE chặn thiếu mật khẩu hiện tại (pass)

Tiền điều kiện: `isPasswordSet = true`.

Các bước:
1. Để trống current password.
2. Nhập mật khẩu mới hợp lệ và xác nhận trùng.
3. Submit.

Kết quả mong đợi:
- FE báo current password required.
- Không gọi API.

### CUST-PASS-003 - FE chặn mật khẩu mới dưới 6 ký tự (pass)

Các bước:
1. Nhập mật khẩu mới `A@123`.

Kết quả mong đợi:
- FE báo lỗi minlength.
- Không gọi API.

### CUST-PASS-004 - FE chặn mật khẩu mới thiếu chữ hoa (pass)

Các bước:
1. Nhập mật khẩu mới `password@123`.

Kết quả mong đợi:
- FE báo lỗi thiếu chữ hoa.
- Không gọi API.

### CUST-PASS-005 - FE chặn mật khẩu mới thiếu ký tự đặc biệt (pass)

Các bước:
1. Nhập mật khẩu mới `Password123`.

Kết quả mong đợi:
- FE báo lỗi thiếu ký tự đặc biệt.
- Không gọi API.

### CUST-PASS-006 - FE chặn xác nhận mật khẩu không khớp (pass)

Các bước:
1. Nhập mật khẩu mới `NewPassword@123`.
2. Nhập xác nhận `OtherPassword@123`.

Kết quả mong đợi:
- FE báo password mismatch.
- Không gọi API.

### CUST-PASS-007 - Đặt mật khẩu lần đầu cho tài khoản Google (pass)

Tiền điều kiện: Customer đăng nhập Google, `isPasswordSet = false`.

Các bước:
1. Mở dialog đổi mật khẩu.
2. Không nhập current password.
3. Nhập mật khẩu mới hợp lệ và xác nhận trùng.
4. Submit.

Kết quả mong đợi:
- FE gửi `currentPassword = ""`.
- Backend không yêu cầu current password.
- Backend set `isPasswordSet = true`.
- Session cập nhật trạng thái đã đặt mật khẩu.

## 8. Test cases sổ địa chỉ 

### CUST-ADDR-001 - Xem danh sách địa chỉ (pass)

Tiền điều kiện: Customer A có nhiều địa chỉ, gồm địa chỉ mặc định.

Các bước:
1. Mở `/account/addresses`.
2. Gọi `GET /api/customers/me/addresses`.

Kết quả mong đợi:
- Chỉ trả địa chỉ chưa deleted của Customer A.
- Địa chỉ mặc định đứng đầu.
- Các địa chỉ còn lại sort theo `createdAt` mới nhất.

### CUST-ADDR-002 - Tạo địa chỉ đầu tiên (pass)

Tiền điều kiện: Customer A chưa có địa chỉ active.

Các bước:
1. Thêm địa chỉ với đầy đủ phone, province, ward, street.
2. Không tick mặc định.

Kết quả mong đợi:
- Backend vẫn set địa chỉ đầu tiên là default.
- Response có `isDefault = true`.
- Danh sách địa chỉ cập nhật.

### CUST-ADDR-003 - Tạo địa chỉ mới và đặt default (pass)

Tiền điều kiện: Customer A đã có địa chỉ default.

Các bước:
1. Tạo địa chỉ mới với `isDefault = true`.

Kết quả mong đợi:
- Backend set địa chỉ mới là default.
- Các địa chỉ khác của Customer A có `isDefault = false`.
- FE reload addresses nếu payload `isDefault = true`.

### CUST-ADDR-004 - Tạo địa chỉ không default (pass)

Tiền điều kiện: Customer A đã có default address.

Các bước:
1. Tạo địa chỉ mới với `isDefault = false`.

Kết quả mong đợi:
- Địa chỉ mới được tạo.
- Default address hiện tại không đổi.

### CUST-ADDR-005 - FE chặn tạo địa chỉ thiếu trường bắt buộc (pass)

Các bước:
1. Mở dialog thêm địa chỉ.
2. Để trống phone hoặc province hoặc ward hoặc street.
3. Lưu.

Kết quả mong đợi:
- FE không gọi API.
- Dialog vẫn mở để user bổ sung.

### CUST-ADDR-006 - API từ chối phoneNumber trống (pass)

Các bước:
1. Gọi `POST /api/customers/me/addresses` với `phoneNumber = ""`.

Kết quả mong đợi:
- Backend trả lỗi validation `phoneNumber is required`.
- Không tạo địa chỉ.

### CUST-ADDR-007 - API từ chối field vượt max length (pass)

Các bước:
1. Gọi API với:
   - `phoneNumber` dài hơn 30 ký tự
   - hoặc `province`/`ward` dài hơn 120 ký tự
   - hoặc `street` dài hơn 255 ký tự.

Kết quả mong đợi:
- Backend trả lỗi validation tương ứng.

### CUST-ADDR-008 - Cập nhật địa chỉ thuộc sở hữu thành công (pass)

Tiền điều kiện: Address thuộc Customer A.

Các bước:
1. Mở edit address.
2. Sửa phone/street/ward/province.
3. Lưu.

Kết quả mong đợi:
- FE gọi `PUT /api/customers/me/addresses/{addressId}`.
- Backend trim field và cập nhật `updatedAt`.
- FE hiển thị thông báo cập nhật thành công và reload danh sách.

### CUST-ADDR-009 - Cập nhật address thành default (pass)

Tiền điều kiện: Customer A có ít nhất 2 địa chỉ.

Các bước:
1. Edit địa chỉ không default.
2. Tick default.
3. Lưu.

Kết quả mong đợi:
- Địa chỉ được edit thành default.
- Địa chỉ default cũ bị unset.

### CUST-ADDR-010 - Update address với `isDefault = false` trên địa chỉ đang default (pass)

Tiền điều kiện: Address đang là default.

Các bước:
1. Gọi update address với `isDefault = false`.

Kết quả mong đợi hiện tại:
- Backend set address đó `isDefault = false`.
- Có thể tạm thời không còn default address nếu không set default khác.

Ghi chú: Nếu nghiệp vụ yêu cầu luôn có default address, cần bổ sung rule backend.

### CUST-ADDR-011 - Set default address bằng endpoint riêng (pass)

Các bước:
1. Bấm đặt mặc định cho một địa chỉ không default.

Kết quả mong đợi:
- FE gọi `PATCH /api/customers/me/addresses/{addressId}/default`.
- Backend set duy nhất địa chỉ đó là default.
- FE reload addresses.

### CUST-ADDR-012 - Xóa địa chỉ không default (pass)

Các bước:
1. Xóa một địa chỉ không default.

Kết quả mong đợi:
- Backend soft delete: `deleted = true`, `deletedAt` set, `isDefault = false`.
- Địa chỉ không còn xuất hiện trong `GET /addresses`.

### CUST-ADDR-013 - Xóa địa chỉ default khi còn địa chỉ khác (pass)

Tiền điều kiện: Customer A có ít nhất 2 địa chỉ active.

Các bước:
1. Xóa địa chỉ default.

Kết quả mong đợi:
- Địa chỉ bị soft delete.
- Backend chọn địa chỉ active có `createdAt` cũ nhất còn lại làm default mới.
- FE reload danh sách.

### CUST-ADDR-014 - Xóa địa chỉ default cuối cùng (pass)

Tiền điều kiện: Customer A chỉ có 1 địa chỉ active và đó là default.

Các bước:
1. Xóa địa chỉ đó.

Kết quả mong đợi:
- Địa chỉ bị soft delete.
- Danh sách active rỗng.
- Không có default address.

### CUST-ADDR-015 - Không được thao tác địa chỉ của customer khác (pass)

Tiền điều kiện: Address thuộc Customer A, Customer B đã đăng nhập.

Các bước:
1. Customer B gọi update/delete/set default với `addressId` của Customer A.

Kết quả mong đợi:
- Backend trả not found.
- Address của Customer A không thay đổi.

## 9. Test cases lịch sử và chi tiết đơn hàng

### CUST-ORDER-001 - Xem danh sách đơn hàng (pass)

Tiền điều kiện: Customer A có nhiều đơn hàng.

Các bước:
1. Mở `/account/orders`.

Kết quả mong đợi:
- FE gọi `GET /api/customers/me/orders?page=0&size=100&sort=createdAt,desc`.
- Chỉ hiển thị đơn của Customer A.
- Mỗi đơn có items, trạng thái đơn, trạng thái thanh toán, phương thức thanh toán, giá cuối, phí ship, giảm giá.

### CUST-ORDER-002 - API phân trang đơn hàng (pass)

Các bước:
1. Gọi `GET /api/customers/me/orders?page=0&size=10&sort=createdAt,desc`.

Kết quả mong đợi:
- Response page đúng `content`, `page`, `size`, `totalElements`, `totalPages`, `last`.
- `size` <= 100 do backend normalize.

### CUST-ORDER-003 - Backend normalize page âm (pass)

Các bước:
1. Gọi `GET /api/customers/me/orders?page=-1&size=10`.

Kết quả mong đợi:
- Backend xử lý như `page = 0`.
- Không lỗi.

### CUST-ORDER-004 - Backend normalize size <= 0 (pass)

Các bước:
1. Gọi `GET /api/customers/me/orders?page=0&size=0`.

Kết quả mong đợi:
- Backend xử lý như `size = 10`.
- Không lỗi.

### CUST-ORDER-005 - Backend giới hạn size tối đa 100 (pass)

Các bước:
1. Gọi `GET /api/customers/me/orders?page=0&size=1000`.

Kết quả mong đợi:
- Backend xử lý `size = 100`.

### CUST-ORDER-006 - Filter đơn hàng theo status qua API (UI chưa có)

Các bước:
1. Gọi `GET /api/customers/me/orders?status=COMPLETED`.

Kết quả mong đợi:
- Chỉ trả đơn có `orderStatus = COMPLETED`.

### CUST-ORDER-007 - Sort đơn hàng theo field hợp lệ (UI chưa có)

Các bước:
1. Gọi API với:
   - `sort=createdAt,desc`
   - `sort=finalPrice,asc`
   - `sort=orderStatus,asc`
   - `sort=paymentStatus,desc`

Kết quả mong đợi:
- Backend sort theo field tương ứng và tie-breaker `id asc`.

### CUST-ORDER-008 - Sort đơn hàng field không hợp lệ (pass)

Các bước:
1. Gọi `GET /api/customers/me/orders?sort=unknown,desc`.

Kết quả mong đợi:
- Backend fallback về field mặc định `createdAt`.
- Không lỗi.

### CUST-ORDER-009 - Filter đơn hàng trên UI theo thời gian (UI chưa có)

Các bước:
1. Mở `/account/orders`.
2. Chọn các filter `30 ngày`, `6 tháng`, `2026`, `Tất cả`.

Kết quả mong đợi:
- FE lọc cục bộ trên orders đã load.
- `last30` và `sixMonths` dùng cutoff trong code hiện tại dựa trên mốc `2026-05-24T00:00:00` rồi trừ ngày.
- `year2026` chỉ hiển thị đơn có năm tạo là 2026.

Ghi chú: Mốc ngày cố định trong FE là điểm cần chú ý regression nếu nghiệp vụ muốn filter theo ngày hiện tại.

### CUST-ORDER-010 - Search đơn hàng trên UI (pass)

Các bước:
1. Nhập keyword vào ô search order.

Kết quả mong đợi:
- FE lọc cục bộ theo `orderId` hoặc `productName` trong items.
- Không gọi lại API.
- Search không phân biệt hoa thường.

### CUST-ORDER-011 - Xem chi tiết đơn hàng thuộc sở hữu (pass)

Tiền điều kiện: Order thuộc Customer A.

Các bước:
1. Click order trong danh sách.

Kết quả mong đợi:
- FE gọi `GET /api/customers/me/orders/{orderId}`.
- Backend chỉ tìm order theo `orderId` và `customerId` hiện tại.
- Dialog detail hiển thị shipping address, items, coupons, tổng tiền, trạng thái.

### CUST-ORDER-012 - Không xem được order của customer khác (pass)

Tiền điều kiện: Order thuộc Customer A, Customer B đã đăng nhập.

Các bước:
1. Customer B gọi `GET /api/customers/me/orders/{orderIdOfA}`.

Kết quả mong đợi:
- Backend trả not found.
- Không lộ thông tin đơn của Customer A.

## 10. Test cases hủy đơn hàng

### CUST-CANCEL-001 - Hủy đơn thành công (pass)

Tiền điều kiện: Order thuộc Customer A, `orderStatus = CREATED`, `paymentStatus = PENDING`.

Các bước:
1. Ở `/account/orders`, bấm hủy đơn.
2. Xác nhận confirm browser.

Kết quả mong đợi:
- FE gọi `POST /api/customers/me/orders/{orderId}/cancel`.
- Backend kiểm tra owner.
- Backend restore stock cho từng order detail.
- Backend tạo inventory transaction type `IMPORT`, reason `RETURN`.
- Order chuyển sang `CANCELLED`.
- Backend tạo notification `ORDER_STATUS` cho customer.
- FE hiển thị thông báo hủy thành công, reload orders và cập nhật selected detail.

### CUST-CANCEL-002 - User bấm cancel nhưng chọn Cancel ở confirm (pass)

Các bước:
1. Bấm hủy đơn.
2. Chọn Cancel trong confirm.

Kết quả mong đợi:
- FE không gọi API.
- Order không thay đổi.

### CUST-CANCEL-003 - Không hủy được đơn không ở trạng thái CREATED 

Các bước:
1. Gọi cancel với order `CONFIRMED`, `SHIPPED`, `COMPLETED`, `RETURN_REQUESTED` hoặc `RETURNED`.

Kết quả mong đợi:
- Backend trả lỗi chỉ đơn mới tạo mới có thể hủy.
- Order không đổi trạng thái.

### CUST-CANCEL-004 - Không hủy được đơn đã thanh toán

Tiền điều kiện: Order `CREATED` nhưng `paymentStatus != PENDING`.

Các bước:
1. Gọi cancel.

Kết quả mong đợi:
- Backend trả lỗi chỉ đơn chưa thanh toán mới có thể hủy.
- Order không đổi trạng thái.

### CUST-CANCEL-005 - Không hủy được đơn của customer khác (pass)

Tiền điều kiện: Order thuộc Customer A, Customer B đã đăng nhập.

Các bước:
1. Customer B gọi cancel order của Customer A.

Kết quả mong đợi:
- Backend trả not found.
- Order không đổi trạng thái, stock không restore.

## 11. Test cases yêu cầu trả hàng

### CUST-RETURN-001 - Gửi yêu cầu trả hàng thành công không có file chứng minh

Tiền điều kiện: Order thuộc Customer A, `orderStatus = COMPLETED`, chưa có return request PENDING.

Các bước:
1. Mở `/account/orders`.
2. Chọn trả hàng cho order completed.
3. Chọn lý do.
4. Nhập chi tiết tùy chọn.
5. Không upload file.
6. Submit.

Kết quả mong đợi:
- FE gọi `POST /api/customers/me/orders/{orderId}/return`.
- Payload có `reason`, `details`, `proofFileKeys = ""`.
- Backend tạo `ReturnRequest` status `PENDING`, `resellable = false`.
- Order chuyển sang `RETURN_REQUESTED`.
- FE hiển thị toast thành công, đóng dialog và reload orders.

### CUST-RETURN-002 - FE chặn submit khi chưa chọn lý do

Các bước:
1. Mở dialog trả hàng.
2. Không chọn lý do.
3. Submit.

Kết quả mong đợi:
- FE hiển thị warning chọn lý do trả hàng.
- Không gọi API return.

### CUST-RETURN-003 - API từ chối reason rỗng

Các bước:
1. Gọi return API với `reason = ""`.

Kết quả mong đợi:
- Backend trả lỗi validation `Reason is required`.
- Không tạo return request.

### CUST-RETURN-004 - Upload evidence ảnh hợp lệ

Các bước:
1. Chọn file ảnh <= 5MB.
2. FE upload trước khi submit return.

Kết quả mong đợi:
- FE gọi presign với `purpose = RETURN_EVIDENCE`.
- Backend tạo key prefix `temp/returns/{accountId}/`.
- FE upload lên R2.
- `uploadedFiles` lưu `fileKey`, `fileName`, `type`, `previewUrl`.

### CUST-RETURN-005 - Upload evidence video hợp lệ

Các bước:
1. Chọn file video <= 50MB.

Kết quả mong đợi:
- FE gọi presign `RETURN_EVIDENCE`.
- Upload thành công và thêm vào danh sách file chứng minh.

### CUST-RETURN-006 - FE chặn file evidence vượt dung lượng

Các bước:
1. Chọn ảnh > 5MB hoặc video > 50MB.

Kết quả mong đợi:
- FE hiển thị `uploadError`.
- Không gọi presign cho file vượt dung lượng.

### CUST-RETURN-007 - Presign evidence sai content type

Các bước:
1. Gọi presign `RETURN_EVIDENCE` với content type không thuộc nhóm chat attachment được hỗ trợ.

Kết quả mong đợi:
- Backend trả lỗi unsupported content type.
- Không trả presigned URL.

### CUST-RETURN-008 - Submit return với file evidence đã upload

Tiền điều kiện: Có các temp evidence key hợp lệ của Customer A.

Các bước:
1. Submit return request với `proofFileKeys` là chuỗi key phân tách bằng dấu phẩy.

Kết quả mong đợi:
- Backend validate owner prefix `temp/returns/{accountId}/`.
- Backend promote từng temp key sang `evidence/returns/{accountId}/`.
- `ReturnRequest.proofFileKeys` lưu permanent keys.
- Sau commit, publish cleanup event xóa temp files.

### CUST-RETURN-009 - Submit return với evidence key của user khác

Các bước:
1. Customer B gửi return request với temp key thuộc Customer A.

Kết quả mong đợi:
- Backend trả lỗi invalid return evidence key owner.
- Không tạo return request.

### CUST-RETURN-010 - Không trả hàng được order chưa completed

Các bước:
1. Gọi return API với order `CREATED`, `CONFIRMED`, `SHIPPED`, `CANCELLED`, `RETURN_REQUESTED` hoặc `RETURNED`.

Kết quả mong đợi:
- Backend trả lỗi chỉ đơn hàng đã hoàn thành mới có thể yêu cầu trả hàng.
- Không tạo return request.

### CUST-RETURN-011 - Không tạo return request trùng khi đã có PENDING

Tiền điều kiện: Order đã có return request `PENDING`.

Các bước:
1. Gửi return request lần nữa.

Kết quả mong đợi:
- Backend trả lỗi yêu cầu trả hàng đã tồn tại.
- Không tạo record mới.

### CUST-RETURN-012 - Không trả hàng order của customer khác

Tiền điều kiện: Order thuộc Customer A, Customer B đăng nhập.

Các bước:
1. Customer B gọi return API cho order của Customer A.

Kết quả mong đợi:
- Backend trả access denied hoặc not found theo path xử lý.
- Không tạo return request, không đổi trạng thái order.

## 12. Test cases ví voucher

### CUST-VOUCHER-001 - Xem ví voucher

Tiền điều kiện: Customer A có nhiều voucher.

Các bước:
1. Mở `/account/vouchers`.

Kết quả mong đợi:
- FE gọi `GET /api/customers/me/vouchers?page=0&size=100&sort=issuedAt,desc`.
- Chỉ hiển thị voucher của Customer A.
- FE tab mặc định là `active`.

### CUST-VOUCHER-002 - API phân trang voucher

Các bước:
1. Gọi `GET /api/customers/me/vouchers?page=0&size=10&sort=issuedAt,desc`.

Kết quả mong đợi:
- Response page đúng metadata.
- `size` được normalize tối đa 100.

### CUST-VOUCHER-003 - Filter AVAILABLE qua API

Các bước:
1. Gọi `GET /api/customers/me/vouchers?status=AVAILABLE`.

Kết quả mong đợi:
- Chỉ trả voucher chưa dùng, coupon active và chưa hết hạn.
- Response status resolve là `AVAILABLE`.

### CUST-VOUCHER-004 - Filter USED qua API

Các bước:
1. Gọi `GET /api/customers/me/vouchers?status=USED`.

Kết quả mong đợi:
- Chỉ trả voucher có `usedAt != null`.
- Response status là `USED`.

### CUST-VOUCHER-005 - Filter EXPIRED qua API

Các bước:
1. Gọi `GET /api/customers/me/vouchers?status=EXPIRED`.

Kết quả mong đợi:
- Chỉ trả voucher hết hạn hoặc coupon inactive.
- Response status là `EXPIRED`.

### CUST-VOUCHER-006 - Tab voucher trên UI

Các bước:
1. Mở `/account/vouchers`.
2. Chọn tab Khả dụng, Đã sử dụng, Hết hạn.

Kết quả mong đợi:
- FE lọc cục bộ từ vouchers đã load.
- `active` map sang backend status `AVAILABLE`.
- `used` map sang `USED`.
- `expired` map sang `EXPIRED`.

### CUST-VOUCHER-007 - Hiển thị nội dung voucher theo type

Tiền điều kiện: Có voucher type `PERCENTAGE`, `FIXED_AMOUNT`, `FREE_SHIPPING`.

Các bước:
1. Xem từng voucher trên UI.

Kết quả mong đợi:
- `PERCENTAGE`: hiển thị giá trị dạng `%`.
- `FIXED_AMOUNT`: hiển thị giá trị dạng `K` và mô tả số tiền giảm.
- `FREE_SHIPPING`: hiển thị miễn phí vận chuyển.
- Voucher không `AVAILABLE` có tone muted và action label tương ứng.

### CUST-VOUCHER-008 - Sao chép mã voucher thành công bằng Clipboard API

Các bước:
1. Bấm sao chép mã voucher khả dụng.

Kết quả mong đợi:
- FE dùng `navigator.clipboard.writeText`.
- Hiển thị toast đã sao chép mã.

### CUST-VOUCHER-009 - Sao chép mã voucher fallback `execCommand`

Tiền điều kiện: Browser không hỗ trợ `navigator.clipboard.writeText`.

Các bước:
1. Bấm sao chép mã voucher.

Kết quả mong đợi:
- FE tạo textarea tạm, gọi `document.execCommand('copy')`.
- Xóa textarea sau khi copy.
- Hiển thị toast thành công nếu copy được.

### CUST-VOUCHER-010 - Sao chép mã voucher thất bại

Các bước:
1. Giả lập Clipboard API và fallback đều thất bại.

Kết quả mong đợi:
- FE hiển thị toast lỗi yêu cầu copy thủ công.
- UI không crash.

### CUST-VOUCHER-011 - Không xem được voucher của customer khác

Tiền điều kiện: Customer A có voucher, Customer B đăng nhập.

Các bước:
1. Customer B gọi `/api/customers/me/vouchers`.

Kết quả mong đợi:
- Chỉ trả voucher của Customer B.
- Không có voucher của Customer A.

## 13. Test cases lỗi và bảo mật chung

### CUST-SEC-001 - Tất cả API `/api/customers/me/*` cần đăng nhập

Các bước:
1. Gọi lần lượt profile, addresses, orders, vouchers, cancel, return khi không có token.

Kết quả mong đợi:
- Backend trả 401/403.
- Không thay đổi dữ liệu.

### CUST-SEC-002 - Customer chỉ thao tác dữ liệu của chính mình

Các bước:
1. Dùng Customer B thao tác order/address của Customer A.

Kết quả mong đợi:
- Backend trả not found/access denied.
- Không lộ dữ liệu và không thay đổi dữ liệu của Customer A.

### CUST-SEC-003 - Upload presign yêu cầu auth và owner prefix

Các bước:
1. Gọi presign khi guest.
2. Dùng fileKey do user khác tạo để update avatar/return evidence.

Kết quả mong đợi:
- Guest bị từ chối.
- Return evidence key sai owner bị từ chối khi submit return.

### CUST-SEC-004 - API lỗi không làm UI giữ loading vô hạn

Các bước:
1. Giả lập lỗi ở profile, addresses, orders hoặc vouchers.

Kết quả mong đợi:
- `loading = false`.
- `error` được set.
- UI không crash.

### CUST-SEC-005 - Action message được clear sau khi hiển thị

Các bước:
1. Thực hiện update profile/create address/delete address/cancel order thành công.
2. Quan sát toast/action message.

Kết quả mong đợi:
- Message hiển thị một lần.
- Store có thể clear bằng `clearActionMessage` hoặc event `ActionMessageCleared`.

## 14. Test cases nên tự động hóa trước

Ưu tiên automated tests:
- `CUST-ACC-001`, `CUST-ACC-002`, `CUST-ACC-003`
- `CUST-PROFILE-001`, `CUST-PROFILE-002`, `CUST-PROFILE-004`, `CUST-PROFILE-007`
- `CUST-PASS-001`, `CUST-PASS-002`, `CUST-PASS-004`, `CUST-PASS-007`
- `CUST-ADDR-001`, `CUST-ADDR-002`, `CUST-ADDR-003`, `CUST-ADDR-013`, `CUST-ADDR-015`
- `CUST-ORDER-001`, `CUST-ORDER-006`, `CUST-ORDER-011`, `CUST-ORDER-012`
- `CUST-CANCEL-001`, `CUST-CANCEL-003`, `CUST-CANCEL-004`, `CUST-CANCEL-005`
- `CUST-RETURN-001`, `CUST-RETURN-003`, `CUST-RETURN-008`, `CUST-RETURN-010`, `CUST-RETURN-011`
- `CUST-VOUCHER-001`, `CUST-VOUCHER-003`, `CUST-VOUCHER-006`, `CUST-VOUCHER-008`

Ghi chú:
- Với upload avatar/evidence, nên mock presign/R2 ở E2E nếu môi trường test không có Cloudflare R2.
- Với cancel order, cần seed stock trước/sau để kiểm tra restore inventory.
- Với return request, cần seed order `COMPLETED` và kiểm tra trạng thái order chuyển sang `RETURN_REQUESTED`.
