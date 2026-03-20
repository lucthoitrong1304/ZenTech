package hcmute.edu.zentech.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Hứng các lỗi logic nghiệp vụ do mình tự ném ra (RuntimeException)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.BAD_REQUEST.value()); // Mã 400
        errorResponse.put("error", "Bad Request");
        errorResponse.put("message", ex.getMessage()); // Lấy câu thông báo lỗi của mình

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // 2. Hứng các lỗi hệ thống không lường trước được (Exception chung)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value()); // Mã 500
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", "Đã xảy ra lỗi trên máy chủ. Vui lòng thử lại sau!");

        // Console log để dev biết mà fix
        ex.printStackTrace();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Sau này khi làm Form Validation (kiểm tra email, password độ dài bao nhiêu...),
    // tụi mình sẽ thêm hàm hứng lỗi Validation vào đây nữa là trọn bộ.
}