package hcmute.edu.zentech.model;

public enum OrderStatus {
    CREATED, // Đơn hàng vừa được tạo
    CONFIRMED, // Đơn hàng đã được xác nhận
    SHIPPED, // Đơn hàng đã được giao cho đơn vị vận chuyển
    COMPLETED, // Đơn hàng kết thúc
    CANCELLED // Đơn bị hủy
}
