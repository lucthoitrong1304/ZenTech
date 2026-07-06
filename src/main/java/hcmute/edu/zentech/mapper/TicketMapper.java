package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.Ticket;
import hcmute.edu.zentech.dto.response.AffectedUserDetailDto;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketMapper {
    private final hcmute.edu.zentech.service.R2StorageService r2StorageService;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final AccountUserRepository accountUserRepository;

    private String resolvePublicUrls(String imagesKeyStr) {
        if (imagesKeyStr == null || imagesKeyStr.isBlank() || r2StorageService == null) {
            return imagesKeyStr;
        }
        return java.util.Arrays.stream(imagesKeyStr.split(","))
                .map(String::trim)
                .map(r2StorageService::getPublicUrl)
                .filter(url -> url != null && !url.isBlank())
                .collect(java.util.stream.Collectors.joining(","));
    }

    public TicketResponseDto toResponseDto(Ticket ticket) {
        return toResponseDto(ticket, java.util.Collections.emptyList());
    }

    public TicketResponseDto toResponseDto(Ticket ticket, java.util.List<String> affectedUserEmails) {
        if (ticket == null) {
            return null;
        }

        java.util.List<AffectedUserDetailDto> affectedUsers = new java.util.ArrayList<>();
        if (affectedUserEmails != null) {
            for (String email : affectedUserEmails) {
                if (email == null || email.isBlank()) continue;
                String fullName = email.split("@")[0];
                String avatarUrl = null;
                String userIdStr = null;

                java.util.Optional<AccountUser> userOpt = accountUserRepository.findByEmailIgnoreCase(email);
                if (userOpt.isPresent()) {
                    AccountUser user = userOpt.get();
                    userIdStr = user.getId().toString();
                    java.util.Optional<Customer> customerOpt = customerRepository.findByUserInfo_Id(user.getId());
                    if (customerOpt.isPresent()) {
                        Customer customer = customerOpt.get();
                        fullName = customer.getFullName();
                        avatarUrl = resolveStoredImageUrl(customer.getImageUrl());
                    }
                }

                affectedUsers.add(AffectedUserDetailDto.builder()
                        .userId(userIdStr)
                        .email(email)
                        .fullName(fullName)
                        .avatarUrl(avatarUrl)
                        .build());
            }
        }

        return TicketResponseDto.builder()
                .id(ticket.getId())
                .code(ticket.getCode())
                .incidentId(ticket.getIncident() != null ? ticket.getIncident().getId() : null)
                .incidentCode(ticket.getIncident() != null ? ticket.getIncident().getCode() : null)
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .assigneeId(ticket.getAssignee() != null ? ticket.getAssignee().getId() : null)
                .assigneeName(resolveDisplayName(ticket.getAssignee()))
                .assigneeEmail(ticket.getAssignee() != null ? ticket.getAssignee().getEmail() : null)
                .assigneeImageUrl(resolveImageUrl(ticket.getAssignee()))
                .createdById(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getId() : null)
                .createdByName(resolveDisplayName(ticket.getCreatedBy()))
                .createdByEmail(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getEmail() : null)
                .createdByImageUrl(resolveImageUrl(ticket.getCreatedBy()))
                .affectedUserEmails(affectedUserEmails)
                .affectedUsers(affectedUsers)
                .createdAt(ticket.getCreatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .images(resolvePublicUrls(ticket.getImages()))
                .build();
    }

    public String resolveDisplayName(AccountUser account) {
        if (account == null) {
            return null;
        }

        return employeeRepository.findByUserInfo_Id(account.getId())
                .map(Employee::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .or(() -> customerRepository.findByUserInfo_Id(account.getId())
                        .map(Customer::getFullName)
                        .filter(name -> name != null && !name.isBlank()))
                .orElse(account.getEmail());
    }

    private String resolveImageUrl(AccountUser account) {
        if (account == null) {
            return null;
        }

        return employeeRepository.findByUserInfo_Id(account.getId())
                .map(Employee::getImageUrl)
                .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
                .or(() -> customerRepository.findByUserInfo_Id(account.getId())
                        .map(Customer::getImageUrl)
                        .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank()))
                .map(this::resolveStoredImageUrl)
                .orElse(null);
    }

    private String resolveStoredImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.startsWith("http")) {
            return imageUrl;
        }
        return r2StorageService.getPresignedGetUrl(imageUrl);
    }
}
