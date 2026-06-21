package hcmute.edu.zentech.init;

public interface ProductCategoryInitializer {
    // Trả về tên category để log hoặc kiểm tra
    String getCategoryName();

    // Kiểm tra xem dữ liệu đã được khởi tạo chưa
    boolean hasData();

    // Hàm thực thi việc tạo data
    void initialize() throws Exception;

    default void synchronizeExistingData() throws Exception {
        // Optional hook for non-destructive seed data backfills.
    }
}
