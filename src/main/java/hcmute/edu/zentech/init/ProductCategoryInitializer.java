package hcmute.edu.zentech.init;

public interface ProductCategoryInitializer {
    // Trả về tên category để log hoặc kiểm tra
    String getCategoryName();

    // Hàm thực thi việc tạo data
    void initialize() throws Exception;
}
