package hcmute.edu.zentech.exception;

public class ResourceNotFoundException extends RuntimeException {
    // Constructor cho phép truyền tên resource, tên field và giá trị để tạo message lỗi chi tiết.
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s không tìm thấy với %s : '%s'", resourceName, fieldName, fieldValue));
    }

    // Constructor nhận message tùy chỉnh (dùng khi muốn tự định nghĩa nội dung lỗi)
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
