package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.PaymentGateway;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.model.PaymentTransaction;
import hcmute.edu.zentech.model.PaymentTransactionStatus;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.PaymentTransactionRepository;
import hcmute.edu.zentech.service.payment.MomoGatewayClient;
import hcmute.edu.zentech.service.payment.VnpayGatewayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceVnpayTest {
    private static final String REQUEST_ID = UUID.randomUUID().toString();
    private static final long ORDER_AMOUNT = 1_015_000L;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private VnpayGatewayClient vnpayGatewayClient;

    @Mock
    private MomoGatewayClient momoGatewayClient;

    @Mock
    private AccountUserRepository accountUserRepository;

    @Mock
    private NotificationService notificationService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentTransactionRepository,
                vnpayGatewayClient,
                momoGatewayClient,
                new ObjectMapper(),
                accountUserRepository,
                notificationService
        );
        ReflectionTestUtils.setField(paymentService, "frontendBaseUrl", "http://localhost:4200");
    }

    @Test
    void handleVnpayIpnMarksSuccessOnlyWhenResponseAndTransactionStatusAreSuccessful() {
        PaymentTransaction transaction = pendingTransaction();
        Map<String, String> params = vnpayParams("00", "00", ORDER_AMOUNT);
        when(vnpayGatewayClient.verify(params)).thenReturn(true);
        when(paymentTransactionRepository.findByGatewayAndRequestId(PaymentGateway.VNPAY, REQUEST_ID))
                .thenReturn(Optional.of(transaction));
        when(accountUserRepository.findByRoleInAndIsActiveTrue(any())).thenReturn(List.of());

        Map<String, String> response = paymentService.handleVnpayIpn(params);

        assertEquals("00", response.get("RspCode"));
        assertEquals(PaymentTransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(PaymentStatus.SUCCESS, transaction.getOrder().getPaymentStatus());
        assertEquals("VNP123", transaction.getGatewayTransactionId());
        assertNotNull(transaction.getPaidAt());
    }

    @Test
    void handleVnpayIpnKeepsOrderPendingWhenTransactionStatusIsNotSuccessful() {
        PaymentTransaction transaction = pendingTransaction();
        Map<String, String> params = vnpayParams("00", "02", ORDER_AMOUNT);
        when(vnpayGatewayClient.verify(params)).thenReturn(true);
        when(paymentTransactionRepository.findByGatewayAndRequestId(PaymentGateway.VNPAY, REQUEST_ID))
                .thenReturn(Optional.of(transaction));

        Map<String, String> response = paymentService.handleVnpayIpn(params);

        assertEquals("00", response.get("RspCode"));
        assertEquals(PaymentTransactionStatus.FAILED, transaction.getStatus());
        assertEquals(PaymentStatus.PENDING, transaction.getOrder().getPaymentStatus());
        assertNull(transaction.getPaidAt());
    }

    @Test
    void handleVnpayIpnKeepsOrderPendingWhenResponseCodeIsNotSuccessful() {
        PaymentTransaction transaction = pendingTransaction();
        Map<String, String> params = vnpayParams("24", "00", ORDER_AMOUNT);
        when(vnpayGatewayClient.verify(params)).thenReturn(true);
        when(paymentTransactionRepository.findByGatewayAndRequestId(PaymentGateway.VNPAY, REQUEST_ID))
                .thenReturn(Optional.of(transaction));

        Map<String, String> response = paymentService.handleVnpayIpn(params);

        assertEquals("00", response.get("RspCode"));
        assertEquals(PaymentTransactionStatus.FAILED, transaction.getStatus());
        assertEquals(PaymentStatus.PENDING, transaction.getOrder().getPaymentStatus());
    }

    @Test
    void handleVnpayIpnRejectsInvalidSignatureWithoutUpdatingAnything() {
        Map<String, String> params = vnpayParams("00", "00", ORDER_AMOUNT);
        when(vnpayGatewayClient.verify(params)).thenReturn(false);

        Map<String, String> response = paymentService.handleVnpayIpn(params);

        assertEquals("97", response.get("RspCode"));
        verify(paymentTransactionRepository, never()).findByGatewayAndRequestId(any(), any());
        verifyNoInteractions(accountUserRepository, notificationService);
    }

    @Test
    void handleVnpayIpnRejectsAmountMismatchWithoutUpdatingTransaction() {
        PaymentTransaction transaction = pendingTransaction();
        Map<String, String> params = vnpayParams("00", "00", ORDER_AMOUNT + 1);
        when(vnpayGatewayClient.verify(params)).thenReturn(true);
        when(paymentTransactionRepository.findByGatewayAndRequestId(PaymentGateway.VNPAY, REQUEST_ID))
                .thenReturn(Optional.of(transaction));

        Map<String, String> response = paymentService.handleVnpayIpn(params);

        assertEquals("04", response.get("RspCode"));
        assertEquals(PaymentTransactionStatus.PENDING, transaction.getStatus());
        assertEquals(PaymentStatus.PENDING, transaction.getOrder().getPaymentStatus());
        assertNull(transaction.getRawPayload());
    }

    @Test
    void handleVnpayIpnReturnsOrderNotFoundWithoutUpdatingAnything() {
        Map<String, String> params = vnpayParams("00", "00", ORDER_AMOUNT);
        when(vnpayGatewayClient.verify(params)).thenReturn(true);
        when(paymentTransactionRepository.findByGatewayAndRequestId(PaymentGateway.VNPAY, REQUEST_ID))
                .thenReturn(Optional.empty());

        Map<String, String> response = paymentService.handleVnpayIpn(params);

        assertEquals("01", response.get("RspCode"));
        verifyNoInteractions(accountUserRepository, notificationService);
    }

    @Test
    void buildVnpayReturnUrlUpdatesOrderWhenIpnHasNotArrivedYet() {
        PaymentTransaction transaction = pendingTransaction();
        Map<String, String> params = vnpayParams("00", "00", ORDER_AMOUNT);
        when(vnpayGatewayClient.verify(params)).thenReturn(true);
        when(paymentTransactionRepository.findByGatewayAndRequestId(PaymentGateway.VNPAY, REQUEST_ID))
                .thenReturn(Optional.of(transaction));
        when(accountUserRepository.findByRoleInAndIsActiveTrue(any())).thenReturn(List.of());

        String redirectUrl = paymentService.buildVnpayReturnUrl(params);

        assertEquals(PaymentTransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(PaymentStatus.SUCCESS, transaction.getOrder().getPaymentStatus());
        org.assertj.core.api.Assertions.assertThat(redirectUrl)
                .contains("/checkout/result")
                .contains("orderId=" + REQUEST_ID)
                .contains("status=success");
    }

    @Test
    void repeatedIpnAfterReturnSuccessIsIdempotent() {
        PaymentTransaction transaction = pendingTransaction();
        Map<String, String> params = vnpayParams("00", "00", ORDER_AMOUNT);
        when(vnpayGatewayClient.verify(params)).thenReturn(true);
        when(paymentTransactionRepository.findByGatewayAndRequestId(PaymentGateway.VNPAY, REQUEST_ID))
                .thenReturn(Optional.of(transaction));
        when(accountUserRepository.findByRoleInAndIsActiveTrue(any())).thenReturn(List.of());

        paymentService.buildVnpayReturnUrl(params);
        paymentService.handleVnpayIpn(params);

        assertEquals(PaymentTransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(PaymentStatus.SUCCESS, transaction.getOrder().getPaymentStatus());
        verify(accountUserRepository).findByRoleInAndIsActiveTrue(any());
    }

    private PaymentTransaction pendingTransaction() {
        Order order = new Order();
        order.setPaymentStatus(PaymentStatus.PENDING);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(order);
        transaction.setGateway(PaymentGateway.VNPAY);
        transaction.setRequestId(REQUEST_ID);
        transaction.setAmount(ORDER_AMOUNT);
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        return transaction;
    }

    private Map<String, String> vnpayParams(String responseCode, String transactionStatus, long amount) {
        return Map.of(
                "vnp_TxnRef", REQUEST_ID,
                "vnp_ResponseCode", responseCode,
                "vnp_TransactionStatus", transactionStatus,
                "vnp_TransactionNo", "VNP123",
                "vnp_Amount", String.valueOf(amount * 100),
                "vnp_SecureHash", "signed"
        );
    }
}
