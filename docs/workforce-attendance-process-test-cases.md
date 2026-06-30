# Test cases cho quy trình Lịch làm việc, Chấm công, Yêu cầu, Duyệt yêu cầu và Kỳ công

Tài liệu này bao phủ một quy trình thống nhất gồm: quản lý loại phép và hạn mức, lập lịch làm việc, nhân viên chấm công, nhân viên gửi yêu cầu/đề xuất, quản lý duyệt yêu cầu, xem báo cáo chấm công và khóa/mở kỳ công.

Nguồn đối chiếu chính:
- Backend: `ShiftController`, `AttendanceController`, `AttendanceAdjustmentController`, `ShiftSwapController`, `LeaveRequestController`, `EmployeeManagementController`, `PayPeriodController`, `ShiftService`, `AttendanceService`, `ApprovalService`, `LeaveManagementService`, `PayPeriodService`
- Frontend: `work-schedules`, `attendance-report`, `requests`, `approvals`, `leave-settings`, `pay-periods`, `management-layout`

## 1. Phạm vi kiểm thử

Các flow được bao phủ:
- Quản lý loại phép và hạn mức phép cho nhân viên.
- Tạo ca làm việc, cập nhật ca, xem lịch theo tuần, gán ca lẻ, gán ca hàng loạt, sao chép lịch tuần.
- Chấm công bằng khuôn mặt và vị trí.
- Gửi yêu cầu nghỉ phép, đổi ca và chỉnh sửa công.
- Duyệt hoặc từ chối yêu cầu.
- Báo cáo chấm công tính theo lịch, sự kiện chấm công, yêu cầu nghỉ phép/chỉnh công đã duyệt.
- Tạo kỳ công, kiểm tra trùng thời gian, khóa/mở kỳ công và tác động của kỳ công bị khóa tới toàn bộ quy trình.

## 2. Endpoint liên quan

| Nhóm | Method | Endpoint | Auth token |
|---|---:|---|---|
| Loại phép nhân viên xem | GET | `/api/leave-types` | Có |
| Loại phép quản lý xem | GET | `/api/management/leave-types` | Có, management |
| Tạo loại phép | POST | `/api/management/leave-types` | Có, management |
| Cập nhật loại phép | PATCH | `/api/management/leave-types/{id}` | Có, management |
| Hạn mức phép của tôi | GET | `/api/leaves/my/quotas?year={year}` | Có, employee |
| Hạn mức phép nhân viên | GET | `/api/management/employees/{employeeId}/leave-quotas?year={year}` | Có, management |
| Cập nhật hạn mức phép | PATCH | `/api/management/employees/{employeeId}/leave-quotas?year={year}` | Có, management |
| Tạo ca | POST | `/api/shifts` | Có, management |
| Danh sách ca | GET | `/api/shifts` | Có |
| Cập nhật ca | PUT | `/api/shifts` | Có, management |
| Lịch làm việc tuần | GET | `/api/shifts/schedules?startDate={date}&endDate={date}&keyword={text}` | Có |
| Gán ca lẻ | POST | `/api/shifts/schedules` | Có, management |
| Gán ca hàng loạt | POST | `/api/shifts/schedules/bulk` | Có, management |
| Sao chép lịch tuần | POST | `/api/shifts/schedules/copy-week` | Có, management |
| Chấm công | POST | `/api/attendance/check-in` | Có, employee |
| Báo cáo chấm công | GET | `/api/attendance/report?startDate={date}&endDate={date}&page=0&size=10` | Có |
| Gửi yêu cầu nghỉ phép | POST | `/api/leaves` | Có, employee |
| Lịch sử nghỉ phép của tôi | GET | `/api/leaves/my` | Có, employee |
| Gửi yêu cầu đổi ca | POST | `/api/schedules/swaps` | Có, employee |
| Lịch sử đổi ca của tôi | GET | `/api/schedules/swaps/my` | Có, employee |
| Gửi yêu cầu chỉnh công | POST | `/api/attendance/adjustments` | Có, employee |
| Lịch sử chỉnh công của tôi | GET | `/api/attendance/adjustments/my` | Có, employee |
| Danh sách nghỉ phép chờ duyệt | GET | `/api/management/leaves/pending` | Có, management |
| Duyệt nghỉ phép | POST | `/api/management/leaves/{id}/approve?status={APPROVED|REJECTED}` | Có, management |
| Danh sách đổi ca chờ duyệt | GET | `/api/management/schedules/swaps/pending` | Có, management |
| Duyệt đổi ca | POST | `/api/management/schedules/swaps/{id}/approve?status={APPROVED|REJECTED}` | Có, management |
| Danh sách chỉnh công chờ duyệt | GET | `/api/management/attendance/adjustments/pending` | Có, management |
| Duyệt chỉnh công | POST | `/api/management/attendance/adjustments/{id}/approve?status={APPROVED|REJECTED}&rejectionReason={text}` | Có, management |
| Danh sách kỳ công | GET | `/api/management/pay-periods` | Có, management |
| Tạo kỳ công | POST | `/api/management/pay-periods` | Có, management |
| Khóa/mở kỳ công | POST | `/api/management/pay-periods/{id}/lock?lock={true|false}` | Có, management |

## 3. Route FE liên quan

| Route FE | Màn hình |
|---|---|
| `/management/work-schedules` | Lịch làm việc |
| `/management/attendance-report` | Báo cáo chấm công |
| `/management/requests` | Yêu cầu và đề xuất của nhân viên |
| `/management/approvals` | Duyệt yêu cầu |
| `/management/leave-settings` | Quản lý loại phép và hạn mức |
| `/management/pay-periods` | Quản lý kỳ công |

## 4. Dữ liệu test đề xuất

| Biến | Giá trị mẫu | Ghi chú |
|---|---|---|
| Manager A | Role `MANAGER` hoặc `OWNER`, active | Người cấu hình và duyệt |
| Employee A | Role `EMPLOYEE`, active, có hồ sơ nhân viên | Nhân viên chính |
| Employee B | Role `EMPLOYEE`, active, có hồ sơ nhân viên | Nhân viên dùng test đổi ca |
| Customer user | Role `CUSTOMER` | Test chặn quyền |
| Ca sáng | `08:00` - `12:00`, màu xanh | Dùng gán lịch |
| Ca chiều | `13:00` - `17:00`, màu cam | Dùng đổi ca |
| Loại phép ngày | `NGHI`, unit `DAY`, active | Hạn mức mặc định 12 ngày nếu là loại thường |
| Loại phép giờ | `AFK`, unit `HOUR`, active | Dùng test xin phép theo giờ |
| Kỳ công mở | `2026-07-01` đến `2026-07-31`, `locked = false` | Cho phép thay đổi dữ liệu |
| Kỳ công khóa | Cùng khoảng ngày, `locked = true` | Chặn chấm công/yêu cầu/chỉnh lịch |
| Face descriptor hợp lệ | Mảng 128 số, khớp khuôn mặt đã đăng ký | Dùng chấm công pass |
| Face descriptor sai | Mảng 128 số nhưng khoảng cách > threshold | Dùng chấm công fail |
| Vị trí hợp lệ | Trong vùng chính sách chấm công | Dùng chấm công pass |
| Vị trí ngoài vùng | Ngoài vùng chính sách | Dùng chấm công fail |

## 5. Test cases quy trình end-to-end

### WFP-E2E-001 - Cấu hình phép, lập lịch, chấm công, duyệt nghỉ phép, xem báo cáo và khóa kỳ công thành công

Tiền điều kiện: Manager A và Employee A đăng nhập được; Employee A đã đăng ký khuôn mặt.

Các bước:
1. Manager A mở `/management/leave-settings`.
2. Tạo hoặc kích hoạt loại phép `NGHI`.
3. Chọn Employee A, năm 2026, cập nhật hạn mức `NGHI = 12`.
4. Mở `/management/work-schedules`, tạo ca sáng nếu chưa có.
5. Gán ca sáng cho Employee A ngày `2026-07-02`.
6. Employee A chấm công vào/ra trong ngày bằng khuôn mặt và vị trí hợp lệ.
7. Employee A gửi yêu cầu nghỉ phép ngày `2026-07-03`.
8. Manager A mở `/management/approvals`, duyệt yêu cầu nghỉ phép.
9. Manager A mở `/management/attendance-report`, lọc `2026-07-01` đến `2026-07-31`.
10. Manager A mở `/management/pay-periods`, tạo kỳ công tháng 07/2026 và khóa kỳ công.

Kết quả mong đợi:
- Loại phép và hạn mức được lưu đúng.
- Lịch làm việc hiển thị ca của Employee A.
- Chấm công tạo lần lượt sự kiện `CHECK_IN` và `CHECK_OUT`.
- Yêu cầu nghỉ phép chuyển từ `PENDING` sang `APPROVED`.
- Báo cáo ghi nhận ngày đi làm theo sự kiện chấm công và ngày nghỉ phép là nghỉ có phép.
- Kỳ công khóa thành công, có `lockedBy` và `lockedAt`.

### WFP-E2E-002 - Kỳ công đã khóa chặn mọi thay đổi trong khoảng ngày

Tiền điều kiện: Kỳ công chứa ngày `2026-07-10` đã bị khóa.

Các bước:
1. Employee A chấm công ngày hiện tại thuộc kỳ công đã khóa.
2. Employee A gửi yêu cầu nghỉ phép ngày `2026-07-10`.
3. Employee A gửi yêu cầu đổi ca ngày `2026-07-10`.
4. Employee A gửi yêu cầu chỉnh công ngày `2026-07-10`.
5. Manager A gán lại ca cho Employee A ngày `2026-07-10`.
6. Manager A duyệt một yêu cầu đang chờ có ngày thuộc kỳ công đã khóa.

Kết quả mong đợi:
- Tất cả thao tác bị từ chối với thông báo kỳ công đã khóa.
- Không tạo thêm sự kiện chấm công, yêu cầu, lịch hoặc thay đổi trạng thái duyệt.
- Báo cáo chấm công trong kỳ đã khóa không bị thay đổi ngoài ý muốn.

## 6. Test cases quản lý loại phép và hạn mức

### WFP-LEAVE-SET-001 - Tạo loại phép theo ngày thành công

Các bước:
1. Mở `/management/leave-settings`.
2. Chọn tạo loại phép.
3. Nhập tên `Nghỉ bù`, mã `NGHI_BU`, unit `DAY`, active bật.
4. Lưu.

Kết quả mong đợi:
- FE gọi `POST /api/management/leave-types`.
- Backend chuẩn hóa mã thành chữ hoa/ký tự hợp lệ.
- Loại phép mới xuất hiện trong danh sách quản lý.
- Hạn mức năm hiện tại được backfill cho nhân viên hiện có.

### WFP-LEAVE-SET-002 - Tạo loại phép theo giờ thành công

Các bước:
1. Tạo loại phép tên `Ra ngoài cá nhân`, mã `AFK_CUSTOM`, unit `HOUR`.
2. Lưu.

Kết quả mong đợi:
- Loại phép được tạo với unit `HOUR`.
- Khi nhân viên chọn loại này ở màn hình yêu cầu, form yêu cầu nhập giờ bắt đầu và giờ kết thúc.

### WFP-LEAVE-SET-003 - Không cho tạo loại phép trùng mã

Tiền điều kiện: Đã có loại phép mã `NGHI`.

Các bước:
1. Tạo loại phép mới với mã `NGHI`.
2. Lưu.

Kết quả mong đợi:
- Backend trả lỗi mã loại phép đã tồn tại.
- Không tạo bản ghi trùng.

### WFP-LEAVE-SET-004 - Không cho lưu tên loại phép rỗng

Các bước:
1. Tạo hoặc sửa loại phép.
2. Nhập tên chỉ gồm khoảng trắng.
3. Lưu.

Kết quả mong đợi:
- Backend trả lỗi tên loại phép không được để trống.
- FE hiển thị lỗi và giữ dữ liệu cũ.

### WFP-LEAVE-SET-005 - Không cho đổi mã và đơn vị của loại phép hệ thống

Tiền điều kiện: Có loại phép hệ thống `NGHI`, `WFH` hoặc `AFK`.

Các bước:
1. Sửa loại phép hệ thống.
2. Gửi payload có `code` và `unit` khác giá trị cũ.
3. Lưu.

Kết quả mong đợi:
- Backend chỉ cập nhật các trường được phép như tên, mô tả, active, sortOrder.
- `code` và `unit` của loại phép hệ thống không đổi.

### WFP-LEAVE-SET-006 - Lấy hạn mức của nhân viên theo năm thành công

Các bước:
1. Mở `/management/leave-settings`.
2. Chọn Employee A và năm 2026.

Kết quả mong đợi:
- FE gọi `GET /api/management/employees/{employeeId}/leave-quotas?year=2026`.
- Backend tự tạo hạn mức còn thiếu cho các loại phép active.
- Danh sách trả về có `entitlement`, `approvedUsed`, `pendingUsed`, `remaining`.

### WFP-LEAVE-SET-007 - Cập nhật hạn mức phép thành công

Các bước:
1. Chọn Employee A.
2. Đổi hạn mức `NGHI` thành `10.5`.
3. Lưu.

Kết quả mong đợi:
- FE gọi `PATCH /api/management/employees/{employeeId}/leave-quotas`.
- Backend lưu `entitlement = 10.50`.
- UI cập nhật tổng hạn mức và còn lại đúng.

### WFP-LEAVE-SET-008 - Hạn mức cập nhật ảnh hưởng ngay đến yêu cầu nghỉ phép

Tiền điều kiện: Employee A còn 1 ngày phép `NGHI`.

Các bước:
1. Employee A gửi yêu cầu nghỉ 2 ngày.
2. Manager A tăng hạn mức thêm 2 ngày.
3. Employee A gửi lại yêu cầu nghỉ 2 ngày.

Kết quả mong đợi:
- Lần 1 bị từ chối vì vượt hạn mức.
- Lần 2 tạo yêu cầu `PENDING` thành công.
- `pendingUsed` tăng theo số ngày yêu cầu.

## 7. Test cases lịch làm việc

### WFP-SCH-001 - Tạo ca làm việc thành công

Các bước:
1. Manager A mở phần cài đặt ca trong `/management/work-schedules`.
2. Nhập tên ca, giờ bắt đầu, giờ kết thúc và màu.
3. Lưu.

Kết quả mong đợi:
- FE gọi `POST /api/shifts`.
- Ca mới xuất hiện trong danh sách ca.
- Ca có thể được chọn khi gán lịch.

### WFP-SCH-002 - Xem lịch làm việc theo tuần thành công

Các bước:
1. Mở `/management/work-schedules`.
2. Chọn tuần có ngày `2026-07-01`.

Kết quả mong đợi:
- FE gọi `GET /api/shifts/schedules` với `startDate` và `endDate`.
- Bảng hiển thị nhân viên theo trang và ca theo từng ngày.
- Ô chưa gán ca hiển thị trạng thái trống.

### WFP-SCH-003 - Tìm kiếm nhân viên trên lịch làm việc

Các bước:
1. Nhập keyword theo tên hoặc email Employee A.
2. Áp dụng bộ lọc.

Kết quả mong đợi:
- FE gửi `keyword`.
- Danh sách chỉ hiển thị nhân viên khớp tìm kiếm.
- Phân trang tính theo kết quả lọc.

### WFP-SCH-004 - Gán ca lẻ cho nhân viên thành công

Các bước:
1. Chọn ô lịch của Employee A ngày `2026-07-02`.
2. Chọn ca sáng.
3. Lưu.

Kết quả mong đợi:
- FE gọi `POST /api/shifts/schedules`.
- Backend xóa ca cũ trong cùng ngày nếu có và lưu ca mới.
- Nhân viên nhận thông báo cập nhật lịch làm việc.

### WFP-SCH-005 - Gán ca hàng loạt cho nhiều nhân viên thành công

Các bước:
1. Chọn Employee A và Employee B.
2. Chọn khoảng ngày `2026-07-01` đến `2026-07-05`.
3. Chọn ca sáng và lưu.

Kết quả mong đợi:
- FE gọi `POST /api/shifts/schedules/bulk`.
- Mỗi nhân viên được gán ca cho toàn bộ khoảng ngày.
- Các ca cũ trong khoảng ngày bị thay thế đúng.
- Các nhân viên bị ảnh hưởng nhận thông báo.

### WFP-SCH-006 - Sao chép lịch tuần thành công

Tiền điều kiện: Tuần nguồn đã có lịch.

Các bước:
1. Chọn sao chép lịch tuần.
2. Chọn tuần nguồn và tuần đích.
3. Lưu.

Kết quả mong đợi:
- FE gọi `POST /api/shifts/schedules/copy-week`.
- Lịch tuần đích giống lịch tuần nguồn theo đúng độ lệch ngày.
- Lịch cũ ở tuần đích của các nhân viên bị ảnh hưởng được thay thế.

### WFP-SCH-007 - Chỉnh lịch ngày quá khứ cần nhập lý do

Tiền điều kiện: Ngày cần chỉnh là ngày trong quá khứ.

Các bước:
1. Manager A gán lại ca cho Employee A ở ngày quá khứ nhưng để trống lý do.
2. Lưu.

Kết quả mong đợi:
- Backend từ chối và yêu cầu nhập lý do điều chỉnh lịch.
- Không thay đổi lịch.

### WFP-SCH-008 - Chỉnh lịch ngày hiện tại đã phát sinh chấm công cần nhập lý do

Tiền điều kiện: Employee A đã có sự kiện chấm công trong ngày hiện tại.

Các bước:
1. Manager A gán lại ca hôm nay cho Employee A, không nhập lý do.
2. Lưu.
3. Nhập lý do hợp lệ và lưu lại.

Kết quả mong đợi:
- Lần 1 bị từ chối.
- Lần 2 thành công.
- Backend ghi nhận bản ghi điều chỉnh lịch với trạng thái `APPROVED`.

### WFP-SCH-009 - Không cho chỉnh lịch trong kỳ công đã khóa

Tiền điều kiện: Ngày cần gán ca thuộc kỳ công đã khóa.

Các bước:
1. Manager A gán ca lẻ hoặc gán hàng loạt cho ngày thuộc kỳ khóa.
2. Lưu.

Kết quả mong đợi:
- Backend trả lỗi kỳ công đã khóa.
- Không thay đổi lịch và không tạo thông báo cập nhật lịch.

## 8. Test cases chấm công và báo cáo chấm công

### WFP-ATT-001 - Chấm công vào thành công

Tiền điều kiện: Employee A có khuôn mặt đã đăng ký, vị trí hợp lệ, kỳ công hôm nay chưa khóa.

Các bước:
1. Employee A mở chức năng chấm công.
2. Quét khuôn mặt hợp lệ.
3. Gửi vị trí hợp lệ.

Kết quả mong đợi:
- FE gọi `POST /api/attendance/check-in`.
- Backend tạo `AttendanceEvent` loại `CHECK_IN`, source `FACE`.
- Response trả thông tin Employee A và thông báo chấm công thành công.

### WFP-ATT-002 - Chấm công ra thành công sau lần chấm công vào

Tiền điều kiện: Employee A đã có sự kiện `CHECK_IN` trong ngày.

Các bước:
1. Employee A chấm công lần tiếp theo trong cùng ngày.

Kết quả mong đợi:
- Backend tạo `AttendanceEvent` loại `CHECK_OUT`.
- Không ghi đè sự kiện `CHECK_IN` trước đó.

### WFP-ATT-003 - Từ chối chấm công khi face descriptor không đủ 128 phần tử

Các bước:
1. Gửi request chấm công với `faceDescriptor` rỗng hoặc không đủ 128 số.

Kết quả mong đợi:
- Backend trả lỗi đặc trưng khuôn mặt không hợp lệ.
- Không tạo `AttendanceEvent`.

### WFP-ATT-004 - Từ chối chấm công khi không khớp khuôn mặt

Các bước:
1. Gửi `faceDescriptor` hợp lệ về kích thước nhưng không khớp khuôn mặt đã đăng ký.

Kết quả mong đợi:
- Backend trả lỗi không nhận diện được khuôn mặt.
- Ghi audit log xác thực khuôn mặt thất bại.
- Không tạo `AttendanceEvent`.

### WFP-ATT-005 - Khóa chức năng chấm công sau 5 lần sai trong 15 phút

Các bước:
1. Gửi sai khuôn mặt 5 lần liên tiếp trong vòng 15 phút.
2. Gửi lại lần thứ 6.

Kết quả mong đợi:
- Lần thứ 6 bị chặn bởi rate limit.
- Thông báo nêu thời gian còn lại trước khi được thử lại.

### WFP-ATT-006 - Từ chối chấm công ngoài vùng vị trí hợp lệ

Tiền điều kiện: Chính sách vị trí đang bật.

Các bước:
1. Gửi request chấm công với latitude/longitude ngoài vùng cho phép.

Kết quả mong đợi:
- Backend trả lỗi vị trí hiện tại nằm ngoài phạm vi check-in hợp lệ.
- Không tạo `AttendanceEvent`.

### WFP-ATT-007 - Từ chối chấm công khi nhân viên chưa đăng ký khuôn mặt

Tiền điều kiện: Employee A chưa có `faceDescriptors`.

Các bước:
1. Employee A gửi request chấm công.

Kết quả mong đợi:
- Backend trả lỗi nhân viên chưa đăng ký khuôn mặt.
- Không tạo sự kiện.

### WFP-ATT-008 - Từ chối chấm công khi kỳ công hôm nay đã khóa

Tiền điều kiện: Kỳ công chứa ngày hiện tại đã khóa.

Các bước:
1. Employee A gửi request chấm công hợp lệ.

Kết quả mong đợi:
- Backend trả lỗi kỳ công đã bị khóa.
- Không kiểm tra tiếp khuôn mặt/vị trí và không tạo sự kiện.

### WFP-ATT-009 - Manager xem báo cáo toàn bộ nhân viên

Các bước:
1. Manager A mở `/management/attendance-report`.
2. Lọc từ `2026-07-01` đến `2026-07-31`.

Kết quả mong đợi:
- FE gọi `GET /api/attendance/report`.
- Backend tính báo cáo cho toàn bộ nhân viên.
- Response có `statistics` và `records`.
- Records được sắp xếp ngày giảm dần, tên nhân viên tăng dần.

### WFP-ATT-010 - Employee chỉ xem báo cáo của chính mình

Các bước:
1. Employee A gọi `GET /api/attendance/report`.

Kết quả mong đợi:
- Backend chỉ tính báo cáo của Employee A.
- Không trả dữ liệu Employee B.

### WFP-ATT-011 - Customer không được xem báo cáo chấm công

Các bước:
1. Đăng nhập bằng Customer user.
2. Gọi `GET /api/attendance/report`.

Kết quả mong đợi:
- Backend trả lỗi không có quyền truy cập báo cáo.
- Không lộ dữ liệu nhân viên.

### WFP-ATT-012 - Báo cáo ghi nhận đi trễ

Tiền điều kiện: Employee A được gán ca bắt đầu `08:00`.

Các bước:
1. Tạo sự kiện `CHECK_IN` lúc `08:30`.
2. Tạo sự kiện `CHECK_OUT` đúng giờ hoặc sau giờ kết thúc.
3. Xem báo cáo ngày đó.

Kết quả mong đợi:
- Record có trạng thái `LATE` hoặc trạng thái tương ứng nếu có thêm lỗi khác.
- `totalLate` tăng.

### WFP-ATT-013 - Báo cáo ghi nhận thiếu check-out

Tiền điều kiện: Employee A có ca làm.

Các bước:
1. Chỉ tạo sự kiện `CHECK_IN`.
2. Xem báo cáo ngày đó.

Kết quả mong đợi:
- Record có trạng thái `MISSING_CHECK_OUT`.
- `totalMissingCheckOut` tăng.

### WFP-ATT-014 - Báo cáo ghi nhận nghỉ có phép khi đơn nghỉ đã duyệt

Tiền điều kiện: Employee A có ca ngày `2026-07-03`.

Các bước:
1. Employee A gửi yêu cầu nghỉ ngày `2026-07-03`.
2. Manager A duyệt `APPROVED`.
3. Xem báo cáo ngày `2026-07-03`.

Kết quả mong đợi:
- Record có trạng thái nghỉ có phép.
- `totalLeave` tăng.
- Không tính là nghỉ không phép.

### WFP-ATT-015 - Báo cáo áp dụng chỉnh công đã duyệt

Tiền điều kiện: Employee A thiếu `CHECK_OUT` ngày `2026-07-04`.

Các bước:
1. Employee A gửi yêu cầu chỉnh công bổ sung giờ ra.
2. Manager A duyệt yêu cầu.
3. Xem báo cáo ngày `2026-07-04`.

Kết quả mong đợi:
- Báo cáo dùng dữ liệu chỉnh công đã duyệt khi tính record.
- Trạng thái thiếu check-out được cập nhật phù hợp.

### WFP-ATT-016 - Báo cáo không áp dụng yêu cầu đang chờ hoặc bị từ chối

Các bước:
1. Employee A gửi yêu cầu chỉnh công hoặc nghỉ phép.
2. Giữ trạng thái `PENDING` hoặc duyệt `REJECTED`.
3. Xem báo cáo.

Kết quả mong đợi:
- Báo cáo không tính yêu cầu đó như dữ liệu chính thức.
- Statistics không tăng sai.

## 9. Test cases yêu cầu và đề xuất của nhân viên

### WFP-REQ-001 - Nhân viên xem loại phép active và hạn mức của mình

Các bước:
1. Employee A mở `/management/requests`.
2. Chọn tab hoặc form nghỉ phép.

Kết quả mong đợi:
- FE gọi `GET /api/leave-types` và `GET /api/leaves/my/quotas`.
- Chỉ loại phép active hiển thị cho nhân viên.
- UI hiển thị hạn mức, đã duyệt, đang chờ và còn lại.

### WFP-REQ-002 - Gửi yêu cầu nghỉ phép theo ngày thành công

Tiền điều kiện: Employee A còn đủ hạn mức loại phép `DAY`.

Các bước:
1. Chọn loại phép theo ngày.
2. Chọn ngày bắt đầu và kết thúc hợp lệ.
3. Nhập lý do.
4. Gửi yêu cầu.

Kết quả mong đợi:
- FE gọi `POST /api/leaves`.
- Backend tạo yêu cầu `PENDING`.
- Quản lý nhận thông báo yêu cầu nghỉ phép mới.
- Yêu cầu xuất hiện ở lịch sử của Employee A và danh sách chờ duyệt.

### WFP-REQ-003 - Không cho gửi nghỉ phép khi ngày kết thúc trước ngày bắt đầu

Các bước:
1. Chọn ngày bắt đầu `2026-07-05`.
2. Chọn ngày kết thúc `2026-07-04`.
3. Gửi.

Kết quả mong đợi:
- Backend trả lỗi ngày kết thúc phải sau hoặc bằng ngày bắt đầu.
- Không tạo yêu cầu.

### WFP-REQ-004 - Gửi yêu cầu nghỉ theo giờ thành công

Tiền điều kiện: Loại phép unit `HOUR` đang active và Employee A còn đủ hạn mức.

Các bước:
1. Chọn loại phép theo giờ.
2. Chọn cùng một ngày, giờ bắt đầu `09:00`, giờ kết thúc `11:00`.
3. Nhập lý do và gửi.

Kết quả mong đợi:
- Backend tính amount = 2.00 giờ.
- Yêu cầu được tạo `PENDING`.
- `pendingUsed` tăng 2 giờ.

### WFP-REQ-005 - Không cho gửi nghỉ theo giờ qua nhiều ngày

Các bước:
1. Chọn loại phép unit `HOUR`.
2. Chọn ngày bắt đầu khác ngày kết thúc.
3. Gửi.

Kết quả mong đợi:
- Backend trả lỗi loại phép theo giờ chỉ được đăng ký trong cùng một ngày.
- Không tạo yêu cầu.

### WFP-REQ-006 - Không cho gửi nghỉ theo giờ thiếu giờ bắt đầu/kết thúc

Các bước:
1. Chọn loại phép unit `HOUR`.
2. Bỏ trống `startTime` hoặc `endTime`.
3. Gửi.

Kết quả mong đợi:
- Backend trả lỗi vui lòng nhập giờ bắt đầu và kết thúc.
- Không tạo yêu cầu.

### WFP-REQ-007 - Không cho gửi nghỉ vượt hạn mức, tính cả yêu cầu đang chờ

Tiền điều kiện: Employee A còn 1 ngày phép, đã có 1 yêu cầu `PENDING` dùng 1 ngày.

Các bước:
1. Employee A gửi thêm yêu cầu nghỉ 1 ngày cùng loại phép.

Kết quả mong đợi:
- Backend từ chối vì hạn mức không đủ khi tính cả pending.
- Không tạo thêm yêu cầu.

### WFP-REQ-008 - Không cho gửi nghỉ phép bằng loại phép inactive

Tiền điều kiện: Loại phép đã bị tắt active.

Các bước:
1. Gửi trực tiếp API `POST /api/leaves` với `leaveTypeId` inactive.

Kết quả mong đợi:
- Backend trả lỗi loại phép đã bị tắt.
- Không tạo yêu cầu.

### WFP-REQ-009 - Gửi yêu cầu đổi ca thành công

Tiền điều kiện: Employee A và Employee B đều có lịch trong ngày tương ứng.

Các bước:
1. Employee A chọn yêu cầu đổi ca.
2. Chọn ngày làm việc của mình, Employee B và ngày/ca mục tiêu.
3. Gửi yêu cầu.

Kết quả mong đợi:
- FE gọi `POST /api/schedules/swaps`.
- Backend gán requester là Employee A, status `PENDING`.
- Employee B và nhóm quản lý nhận thông báo.
- Yêu cầu xuất hiện ở lịch sử của Employee A.

### WFP-REQ-010 - Không cho gửi đổi ca trong kỳ công đã khóa

Tiền điều kiện: Ngày đổi ca hoặc ngày mục tiêu thuộc kỳ công đã khóa.

Các bước:
1. Employee A gửi yêu cầu đổi ca.

Kết quả mong đợi:
- Backend trả lỗi kỳ công chứa ngày liên quan đã khóa.
- Không tạo yêu cầu.

### WFP-REQ-011 - Gửi yêu cầu chỉnh công thành công

Tiền điều kiện: Employee A có ngày chấm công bị thiếu hoặc sai.

Các bước:
1. Employee A chọn chỉnh công.
2. Nhập ngày làm việc, giờ/chi tiết cần chỉnh và lý do.
3. Gửi yêu cầu.

Kết quả mong đợi:
- FE gọi `POST /api/attendance/adjustments`.
- Backend gán employee theo user hiện tại, status `PENDING`, requestedAt hiện tại.
- Quản lý nhận thông báo yêu cầu chỉnh sửa công mới.

### WFP-REQ-012 - Không cho gửi chỉnh công trong kỳ công đã khóa

Tiền điều kiện: Ngày cần chỉnh thuộc kỳ công đã khóa.

Các bước:
1. Employee A gửi yêu cầu chỉnh công cho ngày đó.

Kết quả mong đợi:
- Backend trả lỗi kỳ công đã khóa.
- Không tạo yêu cầu.

### WFP-REQ-013 - Người dùng không có hồ sơ nhân viên không được gửi yêu cầu

Các bước:
1. Đăng nhập bằng Customer user hoặc account không map Employee.
2. Gửi `POST /api/leaves`, `POST /api/schedules/swaps` hoặc `POST /api/attendance/adjustments`.

Kết quả mong đợi:
- Backend trả lỗi không tìm thấy thông tin nhân viên.
- Không tạo dữ liệu.

## 10. Test cases duyệt yêu cầu

### WFP-APR-001 - Quản lý xem danh sách yêu cầu chờ duyệt

Các bước:
1. Manager A mở `/management/approvals`.

Kết quả mong đợi:
- FE gọi các API pending tương ứng:
  - `GET /api/management/leaves/pending`
  - `GET /api/management/schedules/swaps/pending`
  - `GET /api/management/attendance/adjustments/pending`
- Chỉ hiển thị yêu cầu `PENDING`.

### WFP-APR-002 - Duyệt yêu cầu nghỉ phép thành công

Tiền điều kiện: Có yêu cầu nghỉ phép `PENDING`, hạn mức còn đủ khi duyệt.

Các bước:
1. Manager A chọn duyệt yêu cầu.
2. Gửi trạng thái `APPROVED`.

Kết quả mong đợi:
- FE gọi `POST /api/management/leaves/{id}/approve?status=APPROVED`.
- Backend cập nhật `status`, `approvedBy`, `approvedAt`.
- Nhân viên nhận thông báo kết quả duyệt.
- Hạn mức `approvedUsed` tăng và `pendingUsed` giảm.

### WFP-APR-003 - Từ chối yêu cầu nghỉ phép thành công

Các bước:
1. Manager A chọn từ chối yêu cầu nghỉ phép.
2. Gửi trạng thái `REJECTED`.

Kết quả mong đợi:
- Yêu cầu chuyển sang `REJECTED`.
- Không cộng vào `approvedUsed`.
- Nhân viên nhận thông báo bị từ chối.

### WFP-APR-004 - Khi duyệt lại, nếu hạn mức không còn đủ thì không cho duyệt

Tiền điều kiện: Yêu cầu nghỉ phép `PENDING` được tạo khi còn đủ hạn mức, sau đó quản lý giảm hạn mức xuống thấp hơn lượng cần dùng.

Các bước:
1. Manager A duyệt `APPROVED`.

Kết quả mong đợi:
- Backend kiểm tra lại hạn mức.
- Yêu cầu bị từ chối xử lý với lỗi hạn mức không đủ.
- Trạng thái vẫn là `PENDING`.

### WFP-APR-005 - Duyệt yêu cầu đổi ca thành công

Tiền điều kiện: Có yêu cầu đổi ca `PENDING`.

Các bước:
1. Manager A duyệt `APPROVED`.

Kết quả mong đợi:
- FE gọi `POST /api/management/schedules/swaps/{id}/approve?status=APPROVED`.
- Yêu cầu chuyển `APPROVED`, có `approvedBy`, `approvedAt`.
- Requester nhận thông báo duyệt.
- Target employee nhận thông báo lịch làm việc thay đổi.

### WFP-APR-006 - Từ chối yêu cầu đổi ca thành công

Các bước:
1. Manager A từ chối yêu cầu đổi ca.

Kết quả mong đợi:
- Yêu cầu chuyển `REJECTED`.
- Không phát thông báo lịch thay đổi cho target employee.
- Requester nhận thông báo bị từ chối.

### WFP-APR-007 - Duyệt yêu cầu chỉnh công thành công

Tiền điều kiện: Có yêu cầu chỉnh công `PENDING`.

Các bước:
1. Manager A duyệt `APPROVED`.
2. Mở báo cáo chấm công ngày liên quan.

Kết quả mong đợi:
- Yêu cầu chuyển `APPROVED`.
- Nhân viên nhận thông báo kết quả.
- Báo cáo chấm công tính theo dữ liệu chỉnh công đã duyệt.

### WFP-APR-008 - Từ chối yêu cầu chỉnh công với lý do

Các bước:
1. Manager A từ chối yêu cầu chỉnh công.
2. Nhập `rejectionReason`.

Kết quả mong đợi:
- Backend lưu `rejectionReason`.
- Nhân viên nhận thông báo kèm lý do.
- Báo cáo không áp dụng yêu cầu bị từ chối.

### WFP-APR-009 - Không cho duyệt yêu cầu thuộc kỳ công đã khóa

Tiền điều kiện: Yêu cầu đang `PENDING` có ngày thuộc kỳ công đã khóa.

Các bước:
1. Manager A duyệt hoặc từ chối yêu cầu.

Kết quả mong đợi:
- Backend trả lỗi kỳ công đã khóa.
- Trạng thái yêu cầu không đổi.

### WFP-APR-010 - Không cho duyệt yêu cầu không tồn tại

Các bước:
1. Gọi API duyệt với `{id}` không tồn tại.

Kết quả mong đợi:
- Backend trả lỗi yêu cầu không tồn tại.
- Không phát sinh thông báo.

## 11. Test cases quản lý kỳ công

### WFP-PAY-001 - Tạo kỳ công thành công

Các bước:
1. Manager A mở `/management/pay-periods`.
2. Nhập tên `Kỳ công 07/2026`, ngày bắt đầu `2026-07-01`, ngày kết thúc `2026-07-31`.
3. Lưu.

Kết quả mong đợi:
- FE gọi `POST /api/management/pay-periods`.
- Backend tạo kỳ công với `locked = false`.
- Kỳ công xuất hiện trong danh sách.

### WFP-PAY-002 - Không cho tạo kỳ công trùng thời gian

Tiền điều kiện: Đã có kỳ công `2026-07-01` đến `2026-07-31`.

Các bước:
1. Tạo kỳ công mới `2026-07-15` đến `2026-08-15`.

Kết quả mong đợi:
- Backend trả lỗi kỳ công mới trùng lặp thời gian với kỳ công đã có.
- Không tạo kỳ công mới.

### WFP-PAY-003 - Khóa kỳ công thành công

Tiền điều kiện: Kỳ công đang mở.

Các bước:
1. Manager A bấm khóa kỳ công.
2. Xác nhận thao tác.

Kết quả mong đợi:
- FE gọi `POST /api/management/pay-periods/{id}/lock?lock=true`.
- Backend set `locked = true`, `lockedBy = Manager A`, `lockedAt` có giá trị.
- UI hiển thị trạng thái đã khóa.

### WFP-PAY-004 - Mở khóa kỳ công thành công

Tiền điều kiện: Kỳ công đang khóa.

Các bước:
1. Manager A bấm mở khóa.
2. Xác nhận thao tác.

Kết quả mong đợi:
- FE gọi `POST /api/management/pay-periods/{id}/lock?lock=false`.
- Backend set `locked = false`, xóa `lockedBy`, xóa `lockedAt`.
- Các thao tác trong kỳ được phép trở lại.

### WFP-PAY-005 - Không cho thao tác với kỳ công không tồn tại

Các bước:
1. Gọi `POST /api/management/pay-periods/{id}/lock?lock=true` với id không tồn tại.

Kết quả mong đợi:
- Backend trả lỗi không tìm thấy kỳ công.
- Không thay đổi dữ liệu kỳ công khác.

### WFP-PAY-006 - Kỳ công khóa chặn gán lịch

Tiền điều kiện: Ngày `2026-07-12` thuộc kỳ công đã khóa.

Các bước:
1. Manager A gán lại ca cho Employee A ngày `2026-07-12`.

Kết quả mong đợi:
- Backend trả lỗi kỳ công chứa ngày đã khóa.
- Lịch giữ nguyên.

### WFP-PAY-007 - Kỳ công khóa chặn chấm công

Tiền điều kiện: Ngày hiện tại thuộc kỳ công đã khóa.

Các bước:
1. Employee A chấm công bằng dữ liệu hợp lệ.

Kết quả mong đợi:
- Backend trả lỗi kỳ công đã bị khóa.
- Không tạo sự kiện `AttendanceEvent`.

### WFP-PAY-008 - Kỳ công khóa chặn gửi và duyệt yêu cầu

Tiền điều kiện: Ngày trong yêu cầu thuộc kỳ công đã khóa.

Các bước:
1. Employee A gửi nghỉ phép/đổi ca/chỉnh công.
2. Manager A duyệt một yêu cầu cũ thuộc kỳ đó.

Kết quả mong đợi:
- Tất cả thao tác bị từ chối.
- Không đổi trạng thái yêu cầu.

## 12. Test cases phân quyền và bảo mật dữ liệu

### WFP-SEC-001 - Guest không truy cập được các màn hình management

Các bước:
1. Đăng xuất.
2. Mở `/management/work-schedules`, `/management/attendance-report`, `/management/approvals`, `/management/pay-periods`.

Kết quả mong đợi:
- FE điều hướng về trang đăng nhập hoặc bị guard chặn.
- Không gọi API management bằng token rỗng.

### WFP-SEC-002 - Employee không được gọi API quản lý loại phép và kỳ công

Các bước:
1. Đăng nhập bằng Employee A.
2. Gọi trực tiếp các API `/api/management/leave-types`, `/api/management/pay-periods`.

Kết quả mong đợi:
- Backend trả lỗi không đủ quyền.
- Không lộ dữ liệu cấu hình quản lý.

### WFP-SEC-003 - Employee chỉ xem được lịch sử yêu cầu của mình

Các bước:
1. Employee A gọi `GET /api/leaves/my`, `GET /api/schedules/swaps/my`, `GET /api/attendance/adjustments/my`.

Kết quả mong đợi:
- Response chỉ chứa yêu cầu liên quan Employee A.
- Không chứa yêu cầu riêng của Employee B.

### WFP-SEC-004 - Customer không gửi được yêu cầu nhân sự

Các bước:
1. Đăng nhập bằng Customer user.
2. Gọi API gửi nghỉ phép, đổi ca, chỉnh công.

Kết quả mong đợi:
- Backend trả lỗi không tìm thấy thông tin nhân viên hoặc không đủ quyền.
- Không tạo request.

## 13. Test cases kiểm tra đồng bộ dữ liệu giữa các module

### WFP-INTEG-001 - Loại phép inactive không còn hiển thị cho nhân viên nhưng vẫn giữ lịch sử cũ

Tiền điều kiện: Employee A đã có yêu cầu cũ dùng loại phép X.

Các bước:
1. Manager A tắt active loại phép X.
2. Employee A mở màn hình yêu cầu mới.
3. Employee A xem lịch sử yêu cầu.

Kết quả mong đợi:
- Loại phép X không hiển thị trong danh sách tạo yêu cầu mới.
- Lịch sử yêu cầu cũ vẫn hiển thị đúng thông tin loại phép.

### WFP-INTEG-002 - Cập nhật lịch sau khi đã có chấm công làm thay đổi kết quả báo cáo

Tiền điều kiện: Employee A đã chấm công ngày hôm nay.

Các bước:
1. Manager A điều chỉnh ca hôm nay từ ca sáng sang ca chiều, nhập lý do.
2. Mở báo cáo ngày hôm nay.

Kết quả mong đợi:
- Lịch mới được lưu.
- Có bản ghi điều chỉnh lịch.
- Báo cáo tính trạng thái dựa theo ca hiệu lực mới.

### WFP-INTEG-003 - Nghỉ phép pending giữ chỗ hạn mức, từ chối trả lại hạn mức

Tiền điều kiện: Employee A còn 3 ngày phép.

Các bước:
1. Employee A gửi yêu cầu nghỉ 2 ngày.
2. Xem hạn mức của Employee A.
3. Manager A từ chối yêu cầu.
4. Xem lại hạn mức.

Kết quả mong đợi:
- Sau bước 1, `pendingUsed = 2`, `remaining` giảm tương ứng.
- Sau khi từ chối, `pendingUsed` giảm về 0, `remaining` tăng lại.

### WFP-INTEG-004 - Nghỉ phép approved ảnh hưởng báo cáo và hạn mức

Tiền điều kiện: Employee A có lịch làm ngày xin nghỉ.

Các bước:
1. Employee A gửi nghỉ phép 1 ngày.
2. Manager A duyệt.
3. Xem hạn mức và báo cáo ngày đó.

Kết quả mong đợi:
- `approvedUsed` tăng 1.
- `remaining` giảm 1.
- Báo cáo ghi nhận nghỉ có phép.

### WFP-INTEG-005 - Mở khóa kỳ công cho phép sửa dữ liệu rồi khóa lại

Tiền điều kiện: Kỳ công tháng 07/2026 đã khóa.

Các bước:
1. Manager A mở khóa kỳ công.
2. Sửa lịch hoặc duyệt yêu cầu trong kỳ.
3. Xem báo cáo cập nhật.
4. Khóa kỳ công lại.

Kết quả mong đợi:
- Bước 2 thành công sau khi mở khóa.
- Báo cáo phản ánh dữ liệu mới.
- Sau khi khóa lại, các thay đổi tiếp theo trong kỳ bị chặn.
