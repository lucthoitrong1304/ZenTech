package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.service.ProductCategoryService;
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
    private static final String PRODUCT_NAME = "Alpha65 GaN 65W Wall Charger - War Damaged Yellow";

    private final ProductCategoryService productCategoryService;
    private final ProductService productService;
    private final R2StorageService r2StorageService;

    @Override
    public String getCategoryName() {
        return CATEGORY_NAME;
    }

    @Override
    public boolean hasData() {
        return productService.existsByProductName(PRODUCT_NAME);
    }

    @Override
    public void initialize() throws Exception {
        log.info("Bắt đầu khởi tạo data cho: {}", CATEGORY_NAME);

        ProductCategory chargersCategory = productCategoryService.findCategoryByShortName(CATEGORY_NAME);

        Product alpha65Charger = createAlpha65Charger(chargersCategory);

        addVariant(
                alpha65Charger,
                "War Damaged Yellow",
                "#FFD700",
                1_500_000.0,
                1_250_000.0,
                50,
                "Alpha65 GaN 65W Wall Charger - War Damaged Yellow - Image/"
        );



        addVariant(
                alpha65Charger,
                "Charcoal Gray",
                "#4B4B4B",
                1_150_000.0,
                950_000.0,
                50,
                "Alpha65 GaN 65W Wall Charger - Charcoal Gray - Image/"
        );

        addVariant(
                alpha65Charger,
                "War Damaged Blaze Blue",
                "#0047AB",
                1_500_000.0,
                1_250_000.0,
                50,
                "Alpha65 GaN 65W Wall Charger - War Damaged Blaze Blue - Image/"
        );

        addVariant(
                alpha65Charger,
                "Alpha65 & Power Strip Bundle",
                "#1A1A1A",
                2_500_000.0,
                2_200_000.0,
                30,
                "Alpha65 & Power Strip Bundle - Image/"
        );

        productService.save(alpha65Charger);

        log.info("Hoàn tất khởi tạo data cho: {}", CATEGORY_NAME);
    }

    private Product createAlpha65Charger(ProductCategory category) {
        return Product.builder()
                .productName("Alpha65 GaN 65W Wall Charger - War Damaged Yellow")
                .category(category)
                .description(buildAlpha65Description())
                .createdAt(Instant.now())
                .build();
    }

    private void addVariant(
            Product product,
            String nameColor,
            String colorCode,
            Double originalPrice,
            Double salePrice,
            Integer stockQuantity,
            String imageFolder
    ) {
        List<String> imageKeys = getImageKeys(imageFolder, nameColor);
        product.getImageKeys().addAll(imageKeys);
        if (product.getRepresentativeImageKey() == null && !imageKeys.isEmpty()) {
            product.setRepresentativeImageKey(imageKeys.getFirst());
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .nameColor(nameColor)
                .colorCode(colorCode)
                .originalPrice(originalPrice)
                .salePrice(salePrice)
                .stockQuantity(stockQuantity)
                .build();

        product.getVariants().add(variant);
    }

    private List<String> getImageKeys(String folder, String variantName) {
        List<String> imageKeys = r2StorageService.getAllObjectKeysInFolder(folder);

        if (imageKeys.isEmpty()) {
            log.warn("Thư mục R2 đang trống hoặc sai tên. Variant: {}, Folder: {}", variantName, folder);
            return List.of();
        }

        log.info("Đã quét được {} ảnh cho variant: {}", imageKeys.size(), variantName);
        return imageKeys;
    }

    private String buildAlpha65Description() {
        return """
                ## Forge Your Charging Legend with Battle-Tested Power

                Step into the arena with the **Alpha65 GaN Wall Charger - 65W War-Damaged Yellow Special Edition**. This isn't just a charger – it's your battle-tested power weapon, meticulously handcrafted with cutting-edge GaN technology and authentic war-damaged aesthetics that tell a story of countless charging battles.

                ### ⚔️ Why War-Damaged Yellow Dominates
                - **65W Fast Charging Power:** Charge laptops, tablets, and phones at maximum speed
                - **GaN Technology:** 50% smaller than traditional chargers with same power
                - **War-Damaged Yellow Finish:** Unique weathered look that tells a story
                - **Handcrafted Quality:** Each piece individually finished by skilled artisans
                - **Authentic Weathering:** No two chargers are exactly alike

                ### 🚀 GaN Technology Forged in Battle
                Gallium Nitride (GaN) technology represents the future of charging warfare. Unlike traditional silicon chargers, GaN delivers **more power in a smaller package** while running cooler and more efficiently. The Alpha65 harnesses this advanced technology to provide 65W of charging power that's been tested in the heat of battle.

                ### 🌟 War-Damaged Yellow Artistry
                The war-damaged yellow finish isn't just a color – it's a story of epic charging campaigns. Each scratch, scuff, and weathered mark is carefully applied by hand, creating an authentic look that speaks of countless devices powered through legendary charging sessions. Your charger looks like it's been through epic tech battles.

                ### 🏆 Built for Charging Warriors
                - **Fast Charging:** 65W power delivery for rapid device charging
                - **Universal Compatibility:** USB-C PD for laptops, tablets, phones
                - **Compact Design:** 50% smaller than traditional 65W chargers
                - **Safety Features:** Built-in protection against overcharging and overheating
                - **Handcrafted Finish:** Unique weathering makes each piece one-of-a-kind
                - **Energy Efficient:** GaN technology for reduced power consumption

                ### ⚙️ Technical Specifications
                - **Power Output:** 65W maximum power delivery
                - **Technology:** Gallium Nitride (GaN) for efficiency
                - **Connectivity:** USB-C Power Delivery port
                - **Compatibility:** MacBook, iPad, iPhone, Android devices, laptops
                - **Safety:** Overcurrent, overvoltage, and temperature protection
                - **Finish:** Hand-applied war-damaged weathering

                ### 📦 Complete Warrior Package Includes
                - Alpha65 GaN Wall Charger (War-Damaged Yellow Special Edition)
                - User Manual
                - Certificate of Authenticity
                - Safety Information Guide
                - Exclusive War-Damaged Sticker Pack

                **Ready to wield legendary charging power?** Join the elite warriors who've chosen handcrafted quality over mass production. Your war-damaged charger will be the envy of every tech enthusiast who sees it.
                """;
    }
}
