package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.ProductCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class ProductDataInitializer implements CommandLineRunner {
    private final List<ProductCategoryInitializer> categoryInitializers;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Bắt đầu tiến trình khởi tạo Product Data ===");

        for (ProductCategoryInitializer initializer : categoryInitializers) {
            try {
                if (initializer.hasData()) {
                    initializer.synchronizeExistingData();
                    log.info("Dữ liệu cho category [{}] đã tồn tại. Bỏ qua khởi tạo.", initializer.getCategoryName());
                    continue;
                }

                initializer.initialize();
            } catch (Exception e) {
                log.error("Lỗi khi khởi tạo data cho category [{}]: {}", initializer.getCategoryName(), e.getMessage());
            }
        }

        log.info("=== Hoàn tất tiến trình khởi tạo Product Data ===");
    }
}
