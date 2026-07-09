package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.dto.response.ConversationResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.service.ChatConversationService;
import hcmute.edu.zentech.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatController {
    private final ChatConversationService chatConversationService;
    private final ChatMessageService chatMessageService;

    // Tăng trải nghiệm người dùng => Lần đầu tiên vào trang chat - mở lại cuộc hội thoại chưa close của người dùng
    // Nếu đang có 1 cuộc hội thoại active => Mở lên chat tiếp
    // Ngược lại tạo sẵn 1 cuộc hội thoại mới.
    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createOrGetConversation() {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.createOrGetCurrentCustomerConversation()));
    }

    @PostMapping("/new")
    public ResponseEntity<ApiResponse<ConversationResponse>> createNewConversation() {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.createNewCustomerConversation()));
    }

    // Lấy danh sách cuộc hội thoại của người dùng hiện tại
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<ConversationResponse>>> getMyConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean archived
    ) {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.getMyConversations(page, size, archived)));
    }

    // Lấy danh sách message trong 1 cuộc hội thoại.
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.getMessagesForCurrentUser(conversationId, page, size)
        ));
    }

    @GetMapping("/{conversationId}/messages/search")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> searchMessages(
            @PathVariable UUID conversationId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.searchMessagesForCurrentUser(conversationId, keyword, page, size)
        ));
    }

    @GetMapping("/{conversationId}/messages/context")
    public ResponseEntity<ApiResponse<java.util.List<ChatMessageResponse>>> getMessageContext(
            @PathVariable UUID conversationId,
            @RequestParam UUID messageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                chatMessageService.getMessageContextForCurrentUser(conversationId, messageId)
        ));
    }

    @PostMapping("/{conversationId}/request-agent")
    public ResponseEntity<ApiResponse<ConversationResponse>> requestAgent(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.requestAgent(conversationId)));
    }

    @PostMapping("/{conversationId}/close")
    public ResponseEntity<ApiResponse<ConversationResponse>> closeConversation(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.closeConversation(conversationId)));
    }

    @PostMapping("/{conversationId}/reopen")
    public ResponseEntity<ApiResponse<ConversationResponse>> reopenConversation(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.reopenConversation(conversationId)));
    }

    @PatchMapping("/{conversationId}/archive")
    public ResponseEntity<ApiResponse<ConversationResponse>> archiveConversation(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.archiveConversation(conversationId)));
    }

    @PatchMapping("/{conversationId}/unarchive")
    public ResponseEntity<ApiResponse<ConversationResponse>> unarchiveConversation(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.success(chatConversationService.unarchiveConversation(conversationId)));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable UUID conversationId) {
        chatConversationService.deleteConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
