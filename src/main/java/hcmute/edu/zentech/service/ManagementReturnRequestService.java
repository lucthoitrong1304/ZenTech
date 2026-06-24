package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.ReturnRequestResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ReturnRequestMapper;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagementReturnRequestService {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ReturnRequestMapper returnRequestMapper;
    private final NotificationService notificationService;

    public List<ReturnRequestResponse> getReturnRequests() {
        return returnRequestRepository.findAllWithDetails().stream()
                .map(returnRequestMapper::toResponse)
                .collect(Collectors.toList());
    }

    public ReturnRequestResponse getReturnRequest(UUID id) {
        ReturnRequest request = returnRequestRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", id));
        return returnRequestMapper.toResponse(request);
    }

    @Transactional
    public ReturnRequestResponse approveReturnRequest(UUID id, boolean resellable) {
        ReturnRequest returnRequest = returnRequestRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", id));

        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING) {
            throw new RuntimeException("Yêu cầu trả hàng này đã được xử lý.");
        }

        Order order = returnRequest.getOrder();
        order.setOrderStatus(OrderStatus.RETURNED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        orderRepository.save(order);

        returnRequest.setStatus(ReturnRequestStatus.APPROVED);
        returnRequest.setResellable(resellable);
        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        // Update product variant stock and create inventory transactions
        List<OrderDetail> orderDetails = orderDetailRepository.findByOrder_IdIn(List.of(order.getId()));
        UUID employeeId = SecurityContextUtils.getCurrentUserId();

        for (OrderDetail detail : orderDetails) {
            ProductVariant variant = detail.getProductVariant();
            if (variant != null) {
                if (resellable) {
                    variant.setStockQuantity(variant.getStockQuantity() + detail.getQuantity());
                } else {
                    variant.setFaultyQuantity(variant.getFaultyQuantity() + detail.getQuantity());
                }
                productVariantRepository.save(variant);

                // Create Inventory Transaction
                InventoryTransaction transaction = InventoryTransaction.builder()
                        .productVariant(variant)
                        .type(InventoryTransactionType.IMPORT)
                        .quantity(detail.getQuantity())
                        .reason(resellable ? InventoryTransactionReason.RETURN : InventoryTransactionReason.DAMAGED)
                        .note("Hoàn trả từ đơn hàng: " + order.getId() + (resellable ? " (Bán lại được)" : " (Hàng lỗi/hỏng)"))
                        .createdBy(employeeId)
                        .build();
                inventoryTransactionRepository.save(transaction);
            }
        }

        // Notify customer
        if (order.getCustomer() != null && order.getCustomer().getUserInfo() != null) {
            String title = "Yêu cầu trả hàng đã được duyệt";
            String content = String.format("Yêu cầu trả hàng cho đơn hàng #%s của bạn đã được duyệt.", order.getId());
            notificationService.createNotification(
                    order.getCustomer().getUserInfo().getId(),
                    title,
                    content,
                    NotificationType.ORDER_STATUS,
                    order.getId()
            );
        }

        return returnRequestMapper.toResponse(saved);
    }

    @Transactional
    public ReturnRequestResponse rejectReturnRequest(UUID id) {
        ReturnRequest returnRequest = returnRequestRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", "id", id));

        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING) {
            throw new RuntimeException("Yêu cầu trả hàng này đã được xử lý.");
        }

        Order order = returnRequest.getOrder();
        order.setOrderStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        returnRequest.setStatus(ReturnRequestStatus.REJECTED);
        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        // Notify customer
        if (order.getCustomer() != null && order.getCustomer().getUserInfo() != null) {
            String title = "Yêu cầu trả hàng bị từ chối";
            String content = String.format("Yêu cầu trả hàng cho đơn hàng #%s của bạn đã bị từ chối.", order.getId());
            notificationService.createNotification(
                    order.getCustomer().getUserInfo().getId(),
                    title,
                    content,
                    NotificationType.ORDER_STATUS,
                    order.getId()
            );
        }

        return returnRequestMapper.toResponse(saved);
    }
}
