package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.service.ProductCategoryService;
import hcmute.edu.zentech.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeKeyoardInitializer implements ProductCategoryInitializer {
    private final ProductCategoryService productCategoryService;
    private final ProductService productService;

    @Override
    public String getCategoryName() {
        return "HE Keyboard";
    }

    @Override
    public boolean hasData() {
        return productService.existsByProductName("Mercury V60 Pro Deluxe Edition – Dual Keycap Gift Box Set");
    }

    @Override
    public void initialize() throws Exception {
        log.info("Bắt đầu khởi tạo data cho: {}", getCategoryName());
        ProductCategory heCategory = productCategoryService.findCategoryByShortName("HE Keyboard");

        // HE Keyboard 1:
        Product heKeyboard1 = Product.builder()
                .productName("Mercury V60 Pro Deluxe Edition – Dual Keycap Gift Box Set")
                .specifications("""
                ## Dimensions
                - **Height:** 12.8 in (325 mm)
                - **Width:** 4.95 in (125.8 mm)
                - **Depth:** 1.61 in (41 mm)
                - **Weight:** 1.9 lb (0.86 kg)

                ## Technical Specifications
                - **Controller Mapping:** Every key captures press depth for precise throttle, brake, and steering simulation.
                - **True 8kHz Polling Rate:** 8000Hz USB polling rate with 256kHz key-position scanning, reducing latency to ~0.125ms.
                - **GravaStar UFO Magnetic Gaming Switch:** 0.005mm precision with adjustable trigger depth (0.005mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.005mm increments (0.005mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Aluminum Construction:** Durable aluminum frame.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **5-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.
                - **16 Modes, Dual Systems, Multi-Zone RGB Lighting:** 16 lighting modes with dual-zone control and adjustable colors.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap Puller
                - 1 x Switch Puller
                - 1 x Cleaning Brush
                - 1 x Brand Card
                - 1 x Cleaning Cloth
                - 1 x Dust Cover
                - 4 x Extra Switches (GravaStar UFO Magnetic Gaming Switches)
                - 1 x Frosted PC Keycaps Full Set
                - 1 x Brand Sticker
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Magnetic Switch Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        // HE Keyboard 2:
        Product heKeyboard2 = Product.builder()
                .productName("Mercury V75 Pro Special Edition - Neon Graffiti")
                .specifications("""
                ## Dimensions
                - **Height:** 16.34 in (415 mm)
                - **Width:** 7.38 in (187.6 mm)
                - **Depth:** 2.27 in (57.6 mm)
                - **Weight:** 3.77 lb (1.71 kg)

                ## Technical Specifications
                - **True 8kHz Polling Rate:** 8000Hz USB polling rate with 256kHz key-position scanning, reducing latency to ~0.125ms.
                - **GravaStar x Gateron Magnetic Jade Gaming Switch:** 0.005mm precision with adjustable trigger depth (0.1mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.1mm increments (0.1mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Aluminum Construction:** Aluminum frame combined with a premium plastic base.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **4-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.
                - **16 Modes, Dual Systems, Multi-Zone RGB Lighting:** 16 lighting modes with dual-zone control and adjustable colors.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap & Switch 2-in-1 Puller
                - 1 x Cleaning Brush
                - 4 x Extra Switches (GravaStar x Gateron Magnetic Jade Gaming Switches)
                - 1 x User Manual
                - 1 x Cleaning Cloth
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Magnetic Switch Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        // HE Keyboard 3:
        Product heKeyboard3 = Product.builder()
                .productName("Mercury V60 Pro Special Edition - Cyber Frost Black")
                .specifications("""
                ## Dimensions
                - **Height:** 12.8 in (325 mm)
                - **Width:** 4.95 in (125.8 mm)
                - **Depth:** 1.61 in (41 mm)
                - **Weight:** 1.9 lb (0.86 kg)

                ## Technical Specifications
                - **Controller Mapping:** Every key captures press depth for precise throttle, brake, and steering simulation.
                - **True 8kHz Polling Rate:** 8000Hz USB polling rate with 256kHz key-position scanning, reducing latency to ~0.125ms.
                - **GravaStar UFO Magnetic Gaming Switch:** 0.005mm precision with adjustable trigger depth (0.005mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.005mm increments (0.005mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Aluminum Construction:** Durable aluminum frame.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **5-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.
                - **16 Modes, Dual Systems, Multi-Zone RGB Lighting:** 16 lighting modes with dual-zone control and adjustable colors.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap Puller
                - 1 x Switch Puller
                - 1 x Cleaning Brush
                - 1 x Cleaning Cloth
                - 1 x Dust Cover
                - 4 x Extra Switches (GravaStar UFO Magnetic Gaming Switches)
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Magnetic Switch Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        // HE Keyboard 4:
        Product heKeyboard4 = Product.builder()
                .productName("Mercury V60 - Crystal Rose")
                .specifications("""
                ## Dimensions
                - **Height:** 12.8 in (325 mm)
                - **Width:** 4.95 in (125.8 mm)
                - **Depth:** 1.61 in (41 mm)
                - **Weight:** 3.68 lbs (1.67 kg with package)

                ## Technical Specifications
                - **Controller Mapping:** Every key captures press depth for precise throttle, brake, and steering simulation.
                - **True 8kHz Polling Rate:** 8000Hz USB polling rate with 256kHz key-position scanning, reducing latency to ~0.125ms.
                - **GravaStar UFO Magnetic Gaming Switch:** 0.005mm precision with adjustable trigger depth (0.005mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.005mm increments (0.005mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **4-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.
                - **Full-Transparent Premium Plastic Build:** Durable crystal-clear plastic with a lightweight, modern design.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap Puller
                - 1 x Switch Puller
                - 1 x Cleaning Brush
                - 1 x Cleaning Cloth
                - 1 x Dust Cover Bag
                - 4 x Extra Switches (GravaStar UFO Magnetic Gaming Switches)
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Magnetic Switch Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        // HE Keyboard 5:
        Product heKeyboard5 = Product.builder()
                .productName("Mercury V75 Pro - Cyberpunk")
                .specifications("""
                ## Dimensions
                - **Height:** 16.34 in (415 mm)
                - **Width:** 7.38 in (187.6 mm)
                - **Depth:** 2.27 in (57.6 mm)
                - **Weight:** 2.40 lb (1.09 kg)

                ## Technical Specifications
                - **True 8kHz Polling Rate:** 8000Hz USB polling rate with 256kHz key-position scanning, reducing latency to ~0.125ms.
                - **GravaStar x Gateron Magnetic Jade Gaming Switch:** 0.005mm precision with adjustable trigger depth (0.1mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.1mm increments (0.1mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Semi-Aluminum Construction:** Aluminum frame combined with a premium plastic base.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **5-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.
                - **16 Modes, Dual Systems, Multi-Zone RGB Lighting:** 16 lighting modes with dual-zone control and adjustable colors.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap & Switch 2-in-1 Puller
                - 1 x Cleaning Brush
                - 4 x Extra Switches (GravaStar x Gateron Magnetic Jade Gaming Switches)
                - 1 x User Manual
                - 1 x Cleaning Cloth
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Hall Effect Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        // HE Keyboard 6:
        Product heKeyboard6 = Product.builder()
                .productName("Mercury V75 Pro - Iron Purple")
                .specifications("""
                ## Dimensions
                - **Height:** 16.34 in (415 mm)
                - **Width:** 7.38 in (187.6 mm)
                - **Depth:** 2.27 in (57.6 mm)
                - **Weight:** 2.40 lb (1.09 kg)

                ## Technical Specifications
                - **True 8kHz Polling Rate:** 8000Hz USB polling rate with 256kHz key-position scanning, reducing latency to ~0.125ms.
                - **GravaStar x Gateron Magnetic Jade Gaming Switch:** 0.005mm precision with adjustable trigger depth (0.1mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.1mm increments (0.1mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Semi-Aluminum Construction:** Aluminum frame combined with a premium plastic base.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **5-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.
                - **16 Modes, Dual Systems, Multi-Zone RGB Lighting:** 16 lighting modes with dual-zone control and adjustable colors.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap & Switch 2-in-1 Puller
                - 1 x Cleaning Brush
                - 4 x Extra Switches (GravaStar x Gateron Magnetic Jade Gaming Switches)
                - 1 x User Manual
                - 1 x Cleaning Cloth
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Hall Effect Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        // HE Keyboard 7:
        Product heKeyboard7 = Product.builder()
                .productName("Mercury V75- HE Gaming Keyboard")
                .specifications("""
                ## Dimensions
                - **Height:** 16.34 in (415 mm)
                - **Width:** 7.38 in (187.6 mm)
                - **Depth:** 2.27 in (57.6 mm)
                - **Weight:** 2.23 lb (1.01 kg)

                ## Technical Specifications
                - **True 8kHz Polling Rate:** 8000Hz USB polling rate with 256kHz key-position scanning, reducing latency to ~0.125ms.
                - **GravaStar x Gateron Magnetic Jade Pro Switch:** 0.005mm precision with adjustable trigger depth (0.1mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.1mm increments (0.1mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Semi-Aluminum Construction:** Aluminum frame combined with a premium plastic base.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **5-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.
                - **16 Modes, Dual Systems, Multi-Zone RGB Lighting:** 16 lighting modes with dual-zone control and adjustable colors.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap & Switch 2-in-1 Puller
                - 1 x Cleaning Brush
                - 4 x Extra Switches (GravaStar x Gateron Magnetic Jade Pro Switches)
                - 1 x User Manual
                - 1 x Cleaning Cloth
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Magnetic Switch Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        // HE Keyboard 8:
        Product heKeyboard8 = Product.builder()
                .productName("Mercury V75 Special Edition - Lavender Purple (US Only)")
                .specifications("""
                ## Dimensions
                - **Height:** 16.34 in (415 mm)
                - **Width:** 7.38 in (187.6 mm)
                - **Depth:** 2.27 in (57.6 mm)
                - **Weight:** 2.23 lb (1.01 kg)

                ## Technical Specifications
                - **True 8kHz Polling Rate:** 8000Hz USB polling rate with 256kHz key-position scanning, reducing latency to ~0.125ms.
                - **Custom GravaStar Glacier White Switch:** 0.005mm precision with adjustable trigger depth (0.1mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.1mm increments (0.1mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Semi-Aluminum Construction:** Aluminum frame combined with a premium plastic base.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **5-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.
                - **16 Modes, Dual Systems, Multi-Zone RGB Lighting:** 16 lighting modes with dual-zone control and adjustable colors.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap & Switch 2-in-1 Puller
                - 1 x Cleaning Brush
                - 4 x Extra Switches (Custom GravaStar Glacier White Switches)
                - 1 x User Manual
                - 1 x Cleaning Cloth
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Magnetic Switch Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        // HE Keyboard 9:
        Product heKeyboard9 = Product.builder()
                .productName("Mercury V75 Lite - Transparent Black")
                .specifications("""
                ## Dimensions
                - **Length:** 13.50 in (343 mm)
                - **Width:** 6.14 in (156 mm)
                - **Height:** 1.81 in (46 mm)
                - **Weight:** 1.96 lb (0.89 kg)

                ## Technical Specifications
                - **Custom GravaStar Blackcore Switch:** 0.01mm precision with adjustable trigger depth (0.1mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.1mm increments (0.1mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Full-Transparent Premium Plastic Build:** Durable crystal-clear plastic with a lightweight, modern design.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **5-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap & Switch 2-in-1 Puller
                - 1 x Cleaning Brush
                - 4 x Extra Switches (Custom GravaStar Blackcore Switches)
                - 1 x User Manual
                - 1 x Cleaning Cloth
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Magnetic Switch Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        // HE Keyboard 10:
        Product heKeyboard10 = Product.builder()
                .productName("Mercury V60 - Onyx Crystal")
                .specifications("""
                ## Dimensions
                - **Height:** 12.8 in (325 mm)
                - **Width:** 4.95 in (125.8 mm)
                - **Depth:** 1.61 in (41 mm)
                - **Weight:** 3.68 lbs (1.67 kg with package)

                ## Technical Specifications
                - **Controller Mapping:** Every key captures press depth for precise throttle, brake, and steering simulation.
                - **True 8kHz Polling Rate:** 8000Hz USB polling rate with 256kHz key-position scanning, reducing latency to ~0.125ms.
                - **GravaStar UFO Magnetic Gaming Switch:** 0.005mm precision with adjustable trigger depth (0.005mm–3.5mm).
                - **Adjustable Actuation Point:** Custom trigger depth in 0.005mm increments (0.005mm–3.5mm).
                - **Dynamic Rapid Trigger:** Adjusts activation/deactivation points dynamically based on key travel.
                - **Last Keystroke Prioritization (LKP) + Snap Click:** Prioritizes the last-pressed key; resolves simultaneous presses by favoring deeper inputs.
                - **Rapid Trigger Mode:** Instant reset on upward key travel for faster re-presses.
                - **Hot-Swappable Design:** Supports switch swapping (no soldering), compatible with select TTC and Gateron models.
                - **4-Layer Acoustic Foam:** Reduces vibration for better sound and typing feel.
                - **Full-Transparent Premium Plastic Build:** Durable crystal-clear plastic with a lightweight, modern design.

                ## Warranty Information
                - **12-Month Limited Hardware Warranty**
                """)
                .compatibility("""
                ## Compatibility & Connectivity
                - **Operating System:** Windows XP or above / macOS
                - **Connectivity:** Wired
                """)
                .boxContents("""
                ## In the Box
                - 1 x Keyboard
                - 1 x Type-A to Type-C Cable
                - 1 x Keycap Puller
                - 1 x Switch Puller
                - 1 x Cleaning Brush
                - 1 x Cleaning Cloth
                - 1 x Dust Cover Bag
                - 4 x Extra Switches (GravaStar UFO Magnetic Gaming Switches)
                """)
                .supportInfo("""
                ## Get Started

                Get up and running faster with engineer-approved guides:

                - **Tutorial for Keyboard** >>
                - **GravaStar Magnetic Switch Keyboard Web Driver** >>
                """)
                .category(heCategory)
                .build();

        Random random = new Random();

        addVariant(heKeyboard1, "US Plug", "GunMetal", "#5A5A5A", 5879582.0, random.nextInt(41) + 10);
        addVariant(heKeyboard1, "US Plug", "Chrome Silver", "#C0C0C0", 5879582.0, random.nextInt(41) + 10);

        addVariant(heKeyboard2, "US Plug", "Neon Graffiti", "#FF00FF", 6879000.0, random.nextInt(41) + 10);
        addVariant(heKeyboard3, "US Plug", "Cyber Frost Black", "#1A1A1A", 5879000.0, random.nextInt(41) + 10);
        addVariant(heKeyboard4, "US Plug", "Crystal Rose", "#FFC0CB", 4990000.0, random.nextInt(41) + 10);
        addVariant(heKeyboard5, "US Plug", "Cyberpunk", "#FF0055", 5990000.0, random.nextInt(41) + 10);
        addVariant(heKeyboard6, "US Plug", "Iron Purple", "#800080", 5990000.0, random.nextInt(41) + 10);
        addVariant(heKeyboard7, "US Plug", "HE Gaming Keyboard", "#000000", 5590000.0, random.nextInt(41) + 10);
        addVariant(heKeyboard8, "US Plug", "Lavender Purple", "#E6E6FA", 6290000.0, random.nextInt(41) + 10);
        addVariant(heKeyboard9, "US Plug", "Transparent Black", "#333333", 4890000.0, random.nextInt(41) + 10);
        addVariant(heKeyboard10, "US Plug", "Onyx Crystal", "#0F0F0F", 4990000.0, random.nextInt(41) + 10);

        productService.save(heKeyboard1);
        productService.save(heKeyboard2);
        productService.save(heKeyboard3);
        productService.save(heKeyboard4);
        productService.save(heKeyboard5);
        productService.save(heKeyboard6);
        productService.save(heKeyboard7);
        productService.save(heKeyboard8);
        productService.save(heKeyboard9);
        productService.save(heKeyboard10);

        log.info("Hoàn tất khởi tạo data cho: {}", getCategoryName());
    }

    private void addVariant(Product product, String variantName, String colorName, String colorCode, double price, int stock) {
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .name(variantName)
                .nameColor(colorName)
                .colorCode(colorCode)
                .originalPrice(price)
                .stockQuantity(stock)
                .build();
        product.getVariants().add(variant);
    }
}
