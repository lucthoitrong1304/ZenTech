package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductGroup;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.service.ProductCategoryService;
import hcmute.edu.zentech.service.ProductGroupService;
import hcmute.edu.zentech.service.ProductService;
import hcmute.edu.zentech.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChargesInitializer implements ProductCategoryInitializer {

    private static final String CATEGORY_NAME = "Chargers";
    private static final String GROUP_NAME = "Alpha65";

    private final ProductCategoryService productCategoryService;
    private final ProductService productService;
    private final ProductGroupService productGroupService;
    private final R2StorageService r2StorageService;

    @Override
    public String getCategoryName() {
        return CATEGORY_NAME;
    }

    @Override
    public boolean hasData() {
        // Kiểm tra Group Alpha65 để quyết định có chạy initializer hay không
        return productGroupService.existsByGroupName(GROUP_NAME);
    }

    @Override
    public void initialize() throws Exception {
        log.info("Bắt đầu tiến trình khởi tạo dữ liệu cho: {}", CATEGORY_NAME);

        ProductCategory chargersCategory = productCategoryService.findCategoryByShortName(CATEGORY_NAME);
        ProductGroup alpha65Group = productGroupService.getOrCreateGroup(GROUP_NAME, "GravaStar Alpha65 GaN 65W Wall Charger Series");

        // 1. War Damaged Yellow (Trong Group)
        Product yellowCharger = createProductItem(
                "Alpha65 GaN 65W Wall Charger - War Damaged Yellow",
                chargersCategory,
                alpha65Group,
                "War Damaged Yellow",
                "#FFD700",
                1_500_000.0,
                1_250_000.0,
                50,
                "Alpha65 GaN 65W Wall Charger - War Damaged Yellow - Image/",
                buildYellowDescription()
        );

        // 2. Charcoal Gray (Trong Group)
        Product grayCharger = createProductItem(
                "Alpha65 GaN 65W Wall Charger - Charcoal Gray",
                chargersCategory,
                alpha65Group,
                "Charcoal Gray",
                "#4B4B4B",
                1_150_000.0,
                950_000.0,
                50,
                "Alpha65 GaN 65W Wall Charger - Charcoal Gray - Image/",
                buildGrayDescription()
        );

        // 3. War Damaged Blaze Blue (Trong Group)
        Product blueCharger = createProductItem(
                "Alpha65 GaN 65W Wall Charger - War Damaged Blaze Blue",
                chargersCategory,
                alpha65Group,
                "War Damaged Blaze Blue",
                "#0047AB",
                1_500_000.0,
                1_250_000.0,
                50,
                "Alpha65 GaN 65W Wall Charger - War Damaged Blaze Blue - Image/",
                buildBlueDescription()
        );

        // 4. Alpha65 & Power Strip Bundle (KHÔNG thuộc Group, KHÔNG có Description)
        Product bundleCharger = createProductItem(
                "Alpha65 & Power Strip Bundle",
                chargersCategory,
                null,
                "Power Strip Bundle",
                "#1A1A1A",
                2_500_000.0,
                2_200_000.0,
                30,
                "Alpha65 & Power Strip Bundle - Image/",
                null
        );

        // 5. Power Strip (KHÔNG thuộc Group, KHÔNG có Description)
        Product powerStrip = createProductItem(
                "Power Strip",
                chargersCategory,
                null,
                "Power Strip",
                "#1A1A1A",
                1_200_000.0, // Giá demo, ông có thể sửa lại
                990_000.0,
                50,
                "Power Strip - Image/", // Map đúng folder R2 trên ảnh
                null
        );

        // Lưu tất cả vào Database
        productService.save(yellowCharger);
        productService.save(grayCharger);
        productService.save(blueCharger);
        productService.save(bundleCharger);
        productService.save(powerStrip);

        log.info("Hoàn tất khởi tạo dữ liệu cho: {}", CATEGORY_NAME);
    }

    private Product createProductItem(
            String productName,
            ProductCategory category,
            ProductGroup group,
            String nameColor,
            String colorCode,
            Double originalPrice,
            Double salePrice,
            Integer stockQuantity,
            String imageFolder,
            String description
    ) {
        // Build các trường cơ bản
        Product product = Product.builder()
                .productName(productName)
                .description(description)
                .productGroup(group)
                .createdAt(Instant.now())
                .build();

        product.setCategories(new java.util.HashSet<>());
        product.getCategories().add(category);

        List<String> imageKeys = getImageKeys(imageFolder, nameColor);
        product.setImageKeys(new java.util.ArrayList<>(imageKeys));
        if (product.getRepresentativeImageKey() == null && !imageKeys.isEmpty()) {
            product.setRepresentativeImageKey(imageKeys.getFirst());
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .name("US Plug")
                .nameColor(nameColor)
                .colorCode(colorCode)
                .originalPrice(originalPrice)
                .salePrice(salePrice)
                .stockQuantity(stockQuantity)
                .build();

        product.setVariants(new java.util.HashSet<>());
        product.getVariants().add(variant);

        return product;
    }

    private List<String> getImageKeys(String folder, String variantName) {
        List<String> imageKeys = r2StorageService.getAllObjectKeysInFolder(folder);
        if (imageKeys.isEmpty()) {
            log.warn("Thư mục R2 trống hoặc sai đường dẫn. Variant: {}, Folder: {}", variantName, folder);
            return List.of();
        }
        return imageKeys;
    }

    // --- MÔ TẢ CHI TIẾT SẢN PHẨM ---

    private String buildYellowDescription() {
        return """
                ## Forge Your Charging Legend with Battle-Tested Power
                
                Step into the arena with the **Alpha65 GaN Wall Charger - 65W War-Damaged Yellow Special Edition**. This isn't just a charger – it's your battle-tested power weapon, meticulously handcrafted with cutting-edge GaN technology and authentic war-damaged aesthetics that tell a story of countless charging battles.
                
                ## ⚔️ Why War-Damaged Yellow Dominates
                * **65W Fast Charging Power:** Charge laptops, tablets, and phones at maximum speed
                * **GaN Technology:** 50% smaller than traditional chargers with same power
                * **War-Damaged Yellow Finish:** Unique weathered look that tells a story
                * **Handcrafted Quality:** Each piece individually finished by skilled artisans
                * **Authentic Weathering:** No two chargers are exactly alike
                
                ## 🚀 GaN Technology Forged in Battle
                Gallium Nitride (GaN) technology represents the future of charging warfare. Unlike traditional silicon chargers, GaN delivers **more power in a smaller package** while running cooler and more efficiently. The Alpha65 harnesses this advanced technology to provide 65W of charging power that's been tested in the heat of battle.
                
                ## 🌟 War-Damaged Yellow Artistry
                The war-damaged yellow finish isn't just a color – it's a story of epic charging campaigns. Each scratch, scuff, and weathered mark is carefully applied by hand, creating an authentic look that speaks of countless devices powered through legendary charging sessions. Your charger looks like it's been through epic tech battles.
                
                ## 🏆 Built for Charging Warriors
                * **Fast Charging:** 65W power delivery for rapid device charging
                * **Universal Compatibility:** USB-C PD for laptops, tablets, phones
                * **Compact Design:** 50% smaller than traditional 65W chargers
                * **Safety Features:** Built-in protection against overcharging and overheating
                * **Handcrafted Finish:** Unique weathering makes each piece one-of-a-kind
                * **Energy Efficient:** GaN technology for reduced power consumption
                
                ## ⚙️ Technical Specifications
                * **Power Output:** 65W maximum power delivery
                * **Technology:** Gallium Nitride (GaN) for efficiency
                * **Connectivity:** USB-C Power Delivery port
                * **Compatibility:** MacBook, iPad, iPhone, Android devices, laptops
                * **Safety:** Overcurrent, overvoltage, and temperature protection
                * **Finish:** Hand-applied war-damaged weathering
                
                ## 📦 Complete Warrior Package Includes
                * Alpha65 GaN Wall Charger (War-Damaged Yellow Special Edition)
                * User Manual
                * Certificate of Authenticity
                * Safety Information Guide
                * Exclusive War-Damaged Sticker Pack
                
                Ready to wield legendary charging power? **Join the elite warriors who've chosen handcrafted quality over mass production.** Your war-damaged charger will be the envy of every tech enthusiast who sees it.
                """;
    }

    private String buildGrayDescription() {
        return """
                ## Power Up Your Life with Next-Gen GaN Technology
                
                Experience the future of charging with the **Alpha65 GaN Wall Charger - 65W Fast Charging (Charcoal Gray)**. This isn't just a charger – it's your compact power solution, engineered with cutting-edge GaN technology to deliver maximum power in a sleek, travel-friendly design.
                
                ## ⚡ Why Alpha65 GaN Dominates
                * **65W Fast Charging Power:** Charge laptops, tablets, and phones at maximum speed
                * **GaN Technology:** 50% smaller than traditional chargers with same power
                * **Universal Compatibility:** Works with MacBook, iPad, iPhone, Android, and more
                * **Charcoal Gray Design:** Professional aesthetics that complement any workspace
                * **Compact & Portable:** Perfect for travel, office, and home use
                
                ## 🚀 GaN Technology Revolution
                Gallium Nitride (GaN) technology represents the future of charging. Unlike traditional silicon chargers, GaN delivers **more power in a smaller package** while running cooler and more efficiently. The Alpha65 harnesses this advanced technology to provide 65W of charging power in a remarkably compact form factor.
                
                ## 🌟 Professional Charcoal Gray Aesthetics
                The sophisticated charcoal gray finish embodies modern professionalism with a sleek design that looks at home in any environment. Whether you're in a boardroom, coffee shop, or home office, this charger complements your professional image.
                
                ## 🏆 Built for Modern Life
                * **Fast Charging:** 65W power delivery for rapid device charging
                * **Universal Compatibility:** USB-C PD for laptops, tablets, phones
                * **Compact Design:** 50% smaller than traditional 65W chargers
                * **Safety Features:** Built-in protection against overcharging and overheating
                * **Travel-Friendly:** Lightweight and portable for on-the-go charging
                * **Energy Efficient:** GaN technology for reduced power consumption
                
                ## ⚙️ Technical Specifications
                * **Power Output:** 65W maximum power delivery
                * **Technology:** Gallium Nitride (GaN) for efficiency
                * **Connectivity:** USB-C Power Delivery port
                * **Compatibility:** MacBook, iPad, iPhone, Android devices, laptops
                * **Safety:** Overcurrent, overvoltage, and temperature protection
                * **Design:** Compact form factor with charcoal gray finish
                
                ## 📦 Complete Package Includes
                * Alpha65 GaN Wall Charger (Charcoal Gray)
                * User Manual
                * Safety Information Guide
                * Warranty Documentation
                
                Ready to experience the future of charging? **Join thousands of professionals who've upgraded to Alpha65 GaN technology for faster, more efficient charging in a compact design.** Your devices will thank you.
                """;
    }

    private String buildBlueDescription() {
        return """
                ## Forge Your Charging Legend with Battle-Tested Power
                
                Step into the arena with the **Alpha65 GaN Wall Charger - 65W War-Damaged Blaze Blue Special Edition**. This isn't just a charger – it's your battle-tested power weapon, meticulously handcrafted with cutting-edge GaN technology and authentic war-damaged aesthetics that tell a story of countless charging battles.
                
                ## ⚔️ Why War-Damaged Blaze Blue Dominates
                * **65W Fast Charging Power:** Charge laptops, tablets, and phones at maximum speed
                * **GaN Technology:** 50% smaller than traditional chargers with same power
                * **War-Damaged Blaze Blue Finish:** Unique weathered look with blazing blue accents
                * **Handcrafted Quality:** Each piece individually finished by skilled artisans
                * **Authentic Weathering:** No two chargers are exactly alike
                
                ## 🚀 GaN Technology Forged in Battle
                Gallium Nitride (GaN) technology represents the future of charging warfare. Unlike traditional silicon chargers, GaN delivers **more power in a smaller package** while running cooler and more efficiently. The Alpha65 harnesses this advanced technology to provide 65W of charging power that's been tested in the heat of battle.
                
                ## 🌟 War-Damaged Blaze Blue Artistry
                The war-damaged blaze blue finish isn't just a color – it's a story of epic charging campaigns. Each scratch, scuff, and weathered mark is carefully applied by hand, creating an authentic look that speaks of countless devices powered through legendary charging sessions. The blazing blue accents add intensity to the battle-worn aesthetic.
                
                ## 🏆 Built for Charging Warriors
                * **Fast Charging:** 65W power delivery for rapid device charging
                * **Universal Compatibility:** USB-C PD for laptops, tablets, phones
                * **Compact Design:** 50% smaller than traditional 65W chargers
                * **Safety Features:** Built-in protection against overcharging and overheating
                * **Handcrafted Finish:** Unique weathering makes each piece one-of-a-kind
                * **Energy Efficient:** GaN technology for reduced power consumption
                
                ## ⚙️ Technical Specifications
                * **Power Output:** 65W maximum power delivery
                * **Technology:** Gallium Nitride (GaN) for efficiency
                * **Connectivity:** USB-C Power Delivery port
                * **Compatibility:** MacBook, iPad, iPhone, Android devices, laptops
                * **Safety:** Overcurrent, overvoltage, and temperature protection
                * **Finish:** Hand-applied war-damaged weathering with blaze blue accents
                
                ## 📦 Complete Warrior Package Includes
                * Alpha65 GaN Wall Charger (War-Damaged Blaze Blue Special Edition)
                * User Manual
                * Certificate of Authenticity
                * Safety Information Guide
                * Exclusive War-Damaged Sticker Pack
                
                Ready to wield legendary charging power? **Join the elite warriors who've chosen handcrafted quality over mass production.** Your war-damaged charger will be the envy of every tech enthusiast who sees it.
                """;
    }
}