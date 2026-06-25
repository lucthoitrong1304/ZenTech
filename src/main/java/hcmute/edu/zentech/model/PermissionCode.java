package hcmute.edu.zentech.model;

import lombok.Getter;

@Getter
public enum PermissionCode {
    ORDER_VIEW("ORDERS", "Đơn hàng", "VIEW", "Xem danh sách và chi tiết đơn hàng"),
    ORDER_CREATE("ORDERS", "Đơn hàng", "CREATE", "Tạo đơn hàng"),
    ORDER_UPDATE("ORDERS", "Đơn hàng", "UPDATE", "Cập nhật trạng thái và thông tin đơn hàng"),
    ORDER_DELETE("ORDERS", "Đơn hàng", "DELETE", "Hủy đơn hàng"),

    RETURN_VIEW("RETURNS", "Trả hàng", "VIEW", "Xem yêu cầu trả hàng"),
    RETURN_APPROVE("RETURNS", "Trả hàng", "APPROVE", "Duyệt hoặc từ chối yêu cầu trả hàng"),

    PRODUCT_VIEW("PRODUCTS", "Sản phẩm", "VIEW", "Xem sản phẩm và nhóm sản phẩm"),
    PRODUCT_CREATE("PRODUCTS", "Sản phẩm", "CREATE", "Tạo sản phẩm và nhóm sản phẩm"),
    PRODUCT_UPDATE("PRODUCTS", "Sản phẩm", "UPDATE", "Cập nhật sản phẩm và nhóm sản phẩm"),
    PRODUCT_DELETE("PRODUCTS", "Sản phẩm", "DELETE", "Xóa sản phẩm và nhóm sản phẩm"),

    INVENTORY_VIEW("INVENTORY", "Kho hàng", "VIEW", "Xem tồn kho và lịch sử nhập xuất"),
    INVENTORY_UPDATE("INVENTORY", "Kho hàng", "UPDATE", "Điều chỉnh tồn kho"),

    CUSTOMER_VIEW("CUSTOMERS", "Khách hàng", "VIEW", "Xem danh sách và hồ sơ khách hàng"),
    CUSTOMER_UPDATE("CUSTOMERS", "Khách hàng", "UPDATE", "Cập nhật trạng thái khách hàng"),

    EMPLOYEE_VIEW("EMPLOYEES", "Nhân viên", "VIEW", "Xem danh sách nhân viên"),
    EMPLOYEE_CREATE("EMPLOYEES", "Nhân viên", "CREATE", "Tạo nhân viên"),
    EMPLOYEE_UPDATE("EMPLOYEES", "Nhân viên", "UPDATE", "Cập nhật nhân viên"),

    SCHEDULE_VIEW("SCHEDULES", "Lịch làm việc", "VIEW", "Xem lịch làm việc và kỳ công"),
    SCHEDULE_UPDATE("SCHEDULES", "Lịch làm việc", "UPDATE", "Tạo và cập nhật lịch làm việc"),

    APPROVAL_VIEW("APPROVALS", "Phê duyệt", "VIEW", "Xem yêu cầu chờ duyệt"),
    APPROVAL_APPROVE("APPROVALS", "Phê duyệt", "APPROVE", "Phê duyệt nghỉ phép, đổi ca và điều chỉnh công"),

    MARKETING_VIEW("MARKETING", "Marketing", "VIEW", "Xem coupon và voucher"),
    MARKETING_CREATE("MARKETING", "Marketing", "CREATE", "Tạo coupon và phát voucher"),
    MARKETING_UPDATE("MARKETING", "Marketing", "UPDATE", "Cập nhật coupon"),
    MARKETING_DELETE("MARKETING", "Marketing", "DELETE", "Xóa coupon hoặc thu hồi voucher"),

    REPORT_VIEW("REPORTS", "Báo cáo", "VIEW", "Xem báo cáo và phân tích kinh doanh"),
    REPORT_ANALYZE("REPORTS", "Báo cáo", "ANALYZE", "Chạy phân tích AI trên báo cáo"),

    CHAT_VIEW("CHAT", "Tư vấn khách hàng", "VIEW", "Xem hội thoại và ticket hỗ trợ"),
    CHAT_UPDATE("CHAT", "Tư vấn khách hàng", "UPDATE", "Tiếp nhận, chuyển và xử lý hội thoại"),

    AI_VIEW("AI_MANAGEMENT", "Quản lý AI", "VIEW", "Xem agent, dataset và tài liệu AI"),
    AI_CREATE("AI_MANAGEMENT", "Quản lý AI", "CREATE", "Tạo agent, dataset và tài liệu AI"),
    AI_UPDATE("AI_MANAGEMENT", "Quản lý AI", "UPDATE", "Cập nhật cấu hình AI"),
    AI_DELETE("AI_MANAGEMENT", "Quản lý AI", "DELETE", "Xóa agent, dataset và tài liệu AI"),

    PAY_PERIOD_VIEW("PAY_PERIODS", "Kỳ công", "VIEW", "Xem kỳ công"),
    PAY_PERIOD_UPDATE("PAY_PERIODS", "Kỳ công", "UPDATE", "Tạo, khóa và mở kỳ công");

    private final String module;
    private final String moduleName;
    private final String action;
    private final String description;

    PermissionCode(String module, String moduleName, String action, String description) {
        this.module = module;
        this.moduleName = moduleName;
        this.action = action;
        this.description = description;
    }
}
