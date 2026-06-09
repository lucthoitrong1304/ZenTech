package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductGroup;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.repository.ProductCategoryRepository;
import hcmute.edu.zentech.repository.ProductGroupRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import hcmute.edu.zentech.repository.ProductVariantRepository;
import hcmute.edu.zentech.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
@Order(99)
@RequiredArgsConstructor
@Slf4j
public class InventoryMockDataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductGroupRepository groupRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Bắt đầu tiến trình tạo Mock Data cho Tồn Kho ===");

        // 1. Tạo Low Stock nếu chưa có giao dịch nào phát sinh
        if (inventoryTransactionRepository.count() == 0) {
            List<ProductVariant> variants = productVariantRepository.findAll();
            if (variants.size() > 5) {
                int lowStockUpdated = 0;
                for (ProductVariant v : variants) {
                    if (v.getStockQuantity() > 5) {
                        v.setStockQuantity(2); // Set to low stock
                        productVariantRepository.save(v);
                        lowStockUpdated++;
                    }
                    if (lowStockUpdated >= 5) break; // Only mock 5 low stock items
                }
                log.info("Đã cập nhật {} biến thể thành trạng thái sắp hết hàng (Low Stock).", lowStockUpdated);
            }
        } else {
            log.info("Đã có giao dịch kho trong hệ thống. Bỏ qua cập nhật Mock Low Stock để bảo toàn dữ liệu thực tế.");
        }

        // 2. Tạo Dead Stock (sản phẩm chưa từng được bán)
        boolean hasMockProduct = productRepository.existsByProductName("Bàn phím cơ phiên bản giới hạn ZenTech Edition");
        if (!hasMockProduct) {
            ProductCategory cat = categoryRepository.findAll().stream().findFirst().orElse(null);
            ProductGroup group = groupRepository.findAll().stream().findFirst().orElse(null);

            if (cat != null && group != null) {
                Product mockProduct = Product.builder()
                        .productName("Bàn phím cơ phiên bản giới hạn ZenTech Edition")
                        .description("Sản phẩm mới nhập kho, chưa phát sinh giao dịch.")
                        .category(cat)
                        .productGroup(group)
                        .representativeImageKey("https://images.unsplash.com/photo-1595225476474-87563907a212?w=500")
                        .build();
                mockProduct = productRepository.save(mockProduct);

                ProductVariant variant = ProductVariant.builder()
                        .product(mockProduct)
                        .name("Switch Đỏ - Fullsize")
                        //.sku("MOCK-DEAD-01")
                        .originalPrice(1500000.0)
                        .salePrice(1500000.0)
                        .stockQuantity(50)
                        .build();
                productVariantRepository.save(variant);
                log.info("Đã tạo 1 sản phẩm tồn đọng (Dead Stock) để test.");
            }
        } else {
            log.info("Đã có sản phẩm Dead Stock. Bỏ qua.");
        }

        log.info("=== Hoàn tất tạo Mock Data Tồn Kho ===");
    }
}
