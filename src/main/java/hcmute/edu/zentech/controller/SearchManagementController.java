package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.GlobalSearchResponse;
import hcmute.edu.zentech.service.SearchManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/search")
@RequiredArgsConstructor
public class SearchManagementController {

    private final SearchManagementService searchManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<GlobalSearchResponse>> search(
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                searchManagementService.search(keyword)
        ));
    }
}
