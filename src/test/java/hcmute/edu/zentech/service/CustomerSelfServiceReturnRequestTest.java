package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ReturnRequestCreateRequest;
import hcmute.edu.zentech.dto.response.ReturnRequestResponse;
import hcmute.edu.zentech.event.ReturnEvidenceCleanupEvent;
import hcmute.edu.zentech.mapper.CustomerSelfMapper;
import hcmute.edu.zentech.mapper.ReturnRequestMapper;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.ReturnRequest;
import hcmute.edu.zentech.model.ReturnRequestStatus;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.CustomerVoucherRepository;
import hcmute.edu.zentech.repository.OrderDetailRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ReturnRequestRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSelfServiceReturnRequestTest {
    @Mock private CustomerRepository customerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderDetailRepository orderDetailRepository;
    @Mock private CustomerVoucherRepository customerVoucherRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private R2StorageService r2StorageService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CustomerSelfMapper customerSelfMapper;
    @Mock private ReturnRequestMapper returnRequestMapper;

    @InjectMocks
    private CustomerSelfService service;

    private MockedStatic<SecurityContextUtils> securityContext;
    private UUID accountId;
    private UUID orderId;
    private Order order;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder().id(customerId).build();
        order = new Order();
        order.setId(orderId);
        order.setCustomer(customer);
        order.setOrderStatus(OrderStatus.COMPLETED);

        securityContext = org.mockito.Mockito.mockStatic(SecurityContextUtils.class);
        securityContext.when(SecurityContextUtils::getCurrentUserId).thenReturn(accountId);
        when(customerRepository.findByUserInfo_Id(accountId)).thenReturn(Optional.of(customer));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(returnRequestRepository.existsByOrder_IdAndStatus(orderId, ReturnRequestStatus.PENDING))
                .thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        securityContext.close();
    }

    @Test
    void flushesDatabaseThenPublishesCleanupEvent() {
        String tempKey = "temp/returns/" + accountId + "/proof.png";
        String permanentKey = "evidence/returns/" + accountId + "/proof.png";
        ReturnRequestCreateRequest request = requestWith(tempKey);
        ReturnRequestResponse response = ReturnRequestResponse.builder().orderId(orderId).build();
        when(r2StorageService.promoteReturnEvidence(tempKey, accountId)).thenReturn(permanentKey);
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(returnRequestRepository.saveAndFlush(any(ReturnRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(returnRequestMapper.toResponse(any(ReturnRequest.class))).thenReturn(response);

        assertEquals(response, service.createReturnRequest(orderId, request));
        assertEquals(OrderStatus.RETURN_REQUESTED, order.getOrderStatus());

        ArgumentCaptor<ReturnRequest> requestCaptor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).saveAndFlush(requestCaptor.capture());
        assertEquals(permanentKey, requestCaptor.getValue().getProofFileKeys());

        ArgumentCaptor<ReturnEvidenceCleanupEvent> eventCaptor =
                ArgumentCaptor.forClass(ReturnEvidenceCleanupEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(java.util.List.of(tempKey), eventCaptor.getValue().getTempKeys());
    }

    @Test
    void doesNotPublishCleanupWhenDatabaseFlushFails() {
        String tempKey = "temp/returns/" + accountId + "/proof.png";
        ReturnRequestCreateRequest request = requestWith(tempKey);
        when(r2StorageService.promoteReturnEvidence(tempKey, accountId))
                .thenReturn("evidence/returns/" + accountId + "/proof.png");
        when(orderRepository.saveAndFlush(order)).thenThrow(new RuntimeException("DB failure"));

        assertThrows(RuntimeException.class, () -> service.createReturnRequest(orderId, request));
        verify(eventPublisher, never()).publishEvent(any(ReturnEvidenceCleanupEvent.class));
    }

    @Test
    void allowsAnotherRequestWhenPreviousRequestWasRejected() {
        String tempKey = "temp/returns/" + accountId + "/retry.png";
        ReturnRequestCreateRequest request = requestWith(tempKey);
        when(r2StorageService.promoteReturnEvidence(tempKey, accountId))
                .thenReturn("evidence/returns/" + accountId + "/retry.png");
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(returnRequestRepository.saveAndFlush(any(ReturnRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(returnRequestMapper.toResponse(any(ReturnRequest.class)))
                .thenReturn(ReturnRequestResponse.builder().orderId(orderId).build());

        service.createReturnRequest(orderId, request);

        verify(returnRequestRepository).saveAndFlush(any(ReturnRequest.class));
        assertEquals(OrderStatus.RETURN_REQUESTED, order.getOrderStatus());
    }

    @Test
    void blocksAnotherPendingRequestBeforePromotingEvidence() {
        order.setOrderStatus(OrderStatus.COMPLETED);
        when(returnRequestRepository.existsByOrder_IdAndStatus(orderId, ReturnRequestStatus.PENDING))
                .thenReturn(true);
        ReturnRequestCreateRequest request = requestWith(
                "temp/returns/" + accountId + "/duplicate.png");

        assertThrows(RuntimeException.class, () -> service.createReturnRequest(orderId, request));

        verify(r2StorageService, never()).promoteReturnEvidence(any(), any());
        verify(returnRequestRepository, never()).saveAndFlush(any(ReturnRequest.class));
    }

    private ReturnRequestCreateRequest requestWith(String proofFileKeys) {
        ReturnRequestCreateRequest request = new ReturnRequestCreateRequest();
        request.setReason("Defective product");
        request.setDetails("");
        request.setProofFileKeys(proofFileKeys);
        return request;
    }
}
