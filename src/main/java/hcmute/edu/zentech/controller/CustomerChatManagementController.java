package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.ChatConversationListQueryRequest;
import hcmute.edu.zentech.dto.request.TransferRequestCreateRequest;
import hcmute.edu.zentech.dto.request.TransferRequestUpdateRequest;
import hcmute.edu.zentech.dto.request.ChatConversationTransferRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.ChatStaffResponse;
import hcmute.edu.zentech.dto.response.ConversationResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.TransferRequestResponse;
import hcmute.edu.zentech.service.ChatConversationService;
import hcmute.edu.zentech.service.TransferRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/chat")
@RequiredArgsConstructor
public class CustomerChatManagementController {
    private final ChatConversationService chatConversationService;
    private final TransferRequestService transferRequestService;

    // Lấy danh sách hội thoại (flow riêng cho nhân viên)
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<PageResponse<ConversationResponse>>> getConversations(
            @Valid @ModelAttribute ChatConversationListQueryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.getManagementConversations(request)));
    }

    // Logic tiếp nhận yêu cầu
    @PostMapping("/conversations/{conversationId}/claim")
    public ResponseEntity<ApiResponse<ConversationResponse>> claimConversation(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.claimConversation(conversationId)));
    }

    // Tham gia ẩn danh hoặc theo dõi cuộc hội thoại ngầm
    @PostMapping("/conversations/{conversationId}/participants/silent")
    public ResponseEntity<ApiResponse<ConversationResponse>> joinSilent(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.joinSilent(conversationId)));
    }

    // Chuyển tiếp hồi thoại
    @PostMapping("/conversations/{conversationId}/transfer-requests")
    public ResponseEntity<ApiResponse<TransferRequestResponse>> createTransferRequest(
            @PathVariable UUID conversationId,
            @Valid @RequestBody TransferRequestCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                transferRequestService.createTransferRequest(conversationId, request)
        ));
    }

    // update yêu cầu chuyển tiếp => Dùng để tiếp nhận hay từ chối yêu cầu.
    @PutMapping("/transfer-requests/{requestId}")
    public ResponseEntity<ApiResponse<TransferRequestResponse>> updateTransferRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody TransferRequestUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(transferRequestService.updateTransferRequest(requestId, request)));
    }

    @PostMapping("/conversations/{conversationId}/transfer")
    public ResponseEntity<ApiResponse<ConversationResponse>> transferConversation(
            @PathVariable UUID conversationId,
            @RequestBody(required = false) ChatConversationTransferRequest request
    ) {
        UUID targetAccountId = (request != null) ? request.getTargetAccountId() : null;
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.transferConversation(conversationId, targetAccountId)));
    }

    @GetMapping("/staffs/active")
    public ResponseEntity<ApiResponse<List<ChatStaffResponse>>> getActiveStaffList() {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.getActiveStaffList()));
    }
}
