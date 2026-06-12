package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

// @Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class OrderDataInitializer implements CommandLineRunner {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final AccountUserRepository accountUserRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("=== Bắt đầu tiến trình khởi tạo Order & Customer Test Data ===");

        if (orderRepository.count() > 500) {
            log.info("Đã có đủ dữ liệu đơn hàng ({} đơn). Bỏ qua khởi tạo test data.", orderRepository.count());
            return;
        }

        List<ProductVariant> variants = productVariantRepository.findAll();
        if (variants.isEmpty()) {
            log.warn("Không tìm thấy biến thể sản phẩm nào trong DB. Vui lòng chạy ProductDataInitializer trước!");
            return;
        }

        // 1. Tạo các khách hàng giả lập (20 khách hàng)
        List<Customer> customers = new ArrayList<>();
        String[] firstNames = {"John", "Emma", "Michael", "Sophia", "William", "Olivia", "James", "Ava", "Alexander", "Isabella", "David", "Mia", "Joseph", "Charlotte", "Daniel", "Amelia", "Matthew", "Harper", "Andrew", "Evelyn"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez"};
        
        Random random = new Random();
        for (int i = 0; i < 20; i++) {
            String fName = firstNames[i % firstNames.length];
            String lName = lastNames[random.nextInt(lastNames.length)];
            String email = fName.toLowerCase() + "." + lName.toLowerCase() + i + "@zentech.local";
            customers.add(createCustomerIfMissing(email, fName + " " + lName));
        }

        // 2. Tạo đơn hàng trong 90 ngày qua
        log.info("Đang tạo lượng lớn đơn hàng test cho chu kỳ 90 ngày...");
        for (int day = 1; day <= 90; day++) {
            // Mỗi ngày tạo từ 10 đến 25 đơn hàng
            int ordersToday = random.nextInt(16) + 10;
            
            for (int o = 0; o < ordersToday; o++) {
                Instant orderTime = Instant.now()
                        .minus(day, ChronoUnit.DAYS)
                        .plus(random.nextInt(24), ChronoUnit.HOURS)
                        .plus(random.nextInt(60), ChronoUnit.MINUTES);

                Customer customer = customers.get(random.nextInt(customers.size()));
                
                hcmute.edu.zentech.model.Order order = new hcmute.edu.zentech.model.Order();
                order.setCustomer(customer);
                order.setCreatedAt(orderTime);
                order.setPaymentMethod(getRandomPaymentMethod(random));
                order.setPaymentStatus(PaymentStatus.SUCCESS);
                order.setOrderStatus(random.nextDouble() > 0.08 ? OrderStatus.COMPLETED : OrderStatus.CANCELLED);
                order.setShippingFee(25000.0);
                order.setDiscountAmount(0.0);

                // Thêm 1 hoặc 2 sản phẩm vào đơn hàng
                int itemCount = random.nextInt(2) + 1;
                Set<OrderDetail> items = new HashSet<>();
                double originalPriceSum = 0.0;

                for (int i = 0; i < itemCount; i++) {
                    ProductVariant variant = variants.get(random.nextInt(variants.size()));
                    int qty = random.nextInt(2) + 1;
                    double price = variant.getSalePrice() != null ? variant.getSalePrice() : variant.getOriginalPrice();

                    OrderDetail detail = new OrderDetail();
                    detail.setOrder(order);
                    detail.setProductVariant(variant);
                    detail.setQuantity(qty);
                    detail.setPriceAtPurchase(price);
                    items.add(detail);

                    originalPriceSum += price * qty;
                }

                order.setOrderItems(items);
                order.setOriginalTotalPrice(originalPriceSum);

                // Thi thoảng áp dụng mã giảm giá
                if (random.nextDouble() > 0.5) {
                    double discount = originalPriceSum * 0.1; // giảm 10%
                    order.setDiscountAmount(discount);
                    
                    OrderCoupon coupon = new OrderCoupon();
                    coupon.setOrder(order);
                    coupon.setCouponCode(random.nextDouble() > 0.5 ? "ZENTECH10" : "GAMINGMAX");
                    coupon.setCouponType(CouponType.PERCENTAGE);
                    coupon.setDiscountValue(10.0);
                    coupon.setMaxDiscount(100000.0);
                    coupon.setAppliedAmount(discount);
                    
                    Set<OrderCoupon> coupons = new HashSet<>();
                    coupons.add(coupon);
                    order.setOrderCoupons(coupons);
                }

                order.setFinalPrice(originalPriceSum - order.getDiscountAmount() + order.getShippingFee());

                orderRepository.save(order);
            }
        }

        log.info("=== Hoàn tất tiến trình khởi tạo Order & Customer Test Data thành công! ===");
    }

    private Customer createCustomerIfMissing(String email, String fullName) {
        AccountUser account = accountUserRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    AccountUser newAcc = AccountUser.builder()
                            .email(email)
                            .password(passwordEncoder.encode("Customer@123"))
                            .role(Role.CUSTOMER)
                            .isActive(true)
                            .createdAt(Instant.now().minus(20, ChronoUnit.DAYS))
                            .build();
                    return accountUserRepository.save(newAcc);
                });

        return customerRepository.findByUserInfo_Id(account.getId())
                .orElseGet(() -> {
                    Customer newCust = new Customer();
                    newCust.setFullName(fullName);
                    newCust.setUserInfo(account);
                    newCust.setImageUrl(null);
                    return customerRepository.save(newCust);
                });
    }

    private PaymentMethod getRandomPaymentMethod(Random random) {
        PaymentMethod[] methods = PaymentMethod.values();
        return methods[random.nextInt(methods.length)];
    }
}
