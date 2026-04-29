package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class CategoryDataInitializer implements CommandLineRunner {

    private final ProductCategoryService productCategoryService;

    @Override
    public void run(String... args) throws Exception {
        // Kiểm tra nếu database đã có category thì skip luôn
        if (productCategoryService.count() > 0) {
            log.info("=== Dữ liệu Category đã tồn tại. Bỏ qua khởi tạo. ===");
            return;
        }

        log.info("=== Bắt đầu tiến trình khởi tạo Category Data ===");

        // Add keyboards:
        ProductCategory rootKeyboardsCategory = productCategoryService.addCategory("Keyboards", null, null);
        productCategoryService.addCategory("Hall Effect Keyboard", "HE Keyboard", rootKeyboardsCategory.getId());
        productCategoryService.addCategory("Mechanical Keyboards for Gaming", "Mechanical Keyboard", rootKeyboardsCategory.getId());

        // Add Mice:
        productCategoryService.addCategory("Mercury Gaming Mouse", "Mice", null);

        // Add Speakers:
        productCategoryService.addCategory("Bluetooth Speaker", "Speakers", null);

        // Add Earbuds:
        productCategoryService.addCategory("Earbuds", "Earbuds", null);

        // Add Chargers:
        productCategoryService.addCategory("Chargers", "Chargers", null);

        // Add Accessories:
        productCategoryService.addCategory("Accessories", "Accessories", null);

        log.info("=== Hoàn tất tiến trình khởi tạo Category Data ===");
    }
}