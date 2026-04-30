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
        productCategoryService.applyDefaultPriorities();

        // Kiểm tra nếu database đã có category thì skip luôn
        if (productCategoryService.count() > 0) {
            log.info("=== Dữ liệu Category đã tồn tại. Bỏ qua khởi tạo. ===");
            return;
        }

        log.info("=== Bắt đầu tiến trình khởi tạo Category Data ===");

        // Add keyboards:
        ProductCategory rootKeyboardsCategory = productCategoryService.addCategory("Keyboards", null, null, 1);
        productCategoryService.addCategory("Hall Effect Keyboard", "HE Keyboard", rootKeyboardsCategory.getId(), 1);
        productCategoryService.addCategory("Mechanical Keyboards for Gaming", "Mechanical Keyboard", rootKeyboardsCategory.getId(), 2);

        // Add Mice:
        productCategoryService.addCategory("Mercury Gaming Mouse", "Mice", null, 2);

        // Add Speakers:
        productCategoryService.addCategory("Bluetooth Speaker", "Speakers", null, 3);

        // Add Earbuds:
        productCategoryService.addCategory("Earbuds", "Earbuds", null, 4);

        // Add Chargers:
        productCategoryService.addCategory("Chargers", "Chargers", null, 5);

        // Add Accessories:
        productCategoryService.addCategory("Accessories", "Accessories", null, 6);

        log.info("=== Hoàn tất tiến trình khởi tạo Category Data ===");
    }
}
