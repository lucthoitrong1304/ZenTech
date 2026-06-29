package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.EmployeeLeaveQuotaUpdateRequest;
import hcmute.edu.zentech.dto.request.LeaveTypeUpsertRequest;
import hcmute.edu.zentech.dto.response.EmployeeLeaveQuotaResponse;
import hcmute.edu.zentech.dto.response.LeaveTypeResponse;
import hcmute.edu.zentech.model.ApprovalStatus;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.EmployeeLeaveQuota;
import hcmute.edu.zentech.model.LeaveRequest;
import hcmute.edu.zentech.model.LeaveType;
import hcmute.edu.zentech.model.LeaveTypeUnit;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveManagementService {
    public static final String DEFAULT_NGHI_CODE = "NGHI";
    public static final String DEFAULT_WFH_CODE = "WFH";
    public static final String DEFAULT_AFK_CODE = "AFK";

    private static final BigDecimal DEFAULT_DAY_ENTITLEMENT = BigDecimal.valueOf(12);
    private static final BigDecimal DEFAULT_AFK_ENTITLEMENT = BigDecimal.valueOf(16);

    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeLeaveQuotaRepository employeeLeaveQuotaRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeShiftRepository employeeShiftRepository;

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getActiveTypes() {
        return leaveTypeRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(this::toTypeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getAllTypes() {
        return leaveTypeRepository.findAllByOrderBySortOrderAscNameAsc()
                .stream()
                .map(this::toTypeResponse)
                .toList();
    }

    @Transactional
    public LeaveTypeResponse createType(LeaveTypeUpsertRequest request) {
        String code = normalizeCode(request.getCode(), request.getName());
        if (leaveTypeRepository.existsByCode(code)) {
            throw new RuntimeException("Mã loại phép đã tồn tại.");
        }

        LeaveType type = LeaveType.builder()
                .code(code)
                .name(normalizeRequiredText(request.getName(), "Tên loại phép không được để trống."))
                .description(normalizeNullableText(request.getDescription()))
                .unit(request.getUnit())
                .active(request.getActive() == null || request.getActive())
                .systemDefault(false)
                .sortOrder(request.getSortOrder() == null ? nextSortOrder() : request.getSortOrder())
                .build();

        LeaveType saved = leaveTypeRepository.save(type);
        backfillQuotaForType(saved, LocalDate.now().getYear());
        return toTypeResponse(saved);
    }

    @Transactional
    public LeaveTypeResponse updateType(UUID id, LeaveTypeUpsertRequest request) {
        LeaveType type = leaveTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Loại phép không tồn tại."));

        type.setName(normalizeRequiredText(request.getName(), "Tên loại phép không được để trống."));
        type.setDescription(normalizeNullableText(request.getDescription()));
        if (!type.isSystemDefault() && request.getCode() != null && !request.getCode().isBlank()) {
            String code = normalizeCode(request.getCode(), request.getName());
            leaveTypeRepository.findByCode(code)
                    .filter(existing -> !existing.getId().equals(type.getId()))
                    .ifPresent(existing -> {
                        throw new RuntimeException("Mã loại phép đã tồn tại.");
                    });
            type.setCode(code);
        }
        if (!type.isSystemDefault()) {
            type.setUnit(request.getUnit());
        }
        if (request.getActive() != null) {
            type.setActive(request.getActive());
        }
        if (request.getSortOrder() != null) {
            type.setSortOrder(request.getSortOrder());
        }

        return toTypeResponse(leaveTypeRepository.save(type));
    }

    @Transactional
    public List<EmployeeLeaveQuotaResponse> getEmployeeQuotas(UUID employeeId, int year) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Nhân viên không tồn tại."));
        ensureQuotas(employee, year);
        return employeeLeaveQuotaRepository.findByEmployeeIdAndYear(employeeId, year)
                .stream()
                .sorted(Comparator
                        .comparing((EmployeeLeaveQuota quota) -> quota.getLeaveType().getSortOrder())
                        .thenComparing(quota -> quota.getLeaveType().getName()))
                .map(this::toQuotaResponse)
                .toList();
    }

    @Transactional
    public List<EmployeeLeaveQuotaResponse> updateEmployeeQuotas(UUID employeeId, int year, EmployeeLeaveQuotaUpdateRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Nhân viên không tồn tại."));
        ensureQuotas(employee, year);

        for (EmployeeLeaveQuotaUpdateRequest.Item item : request.getQuotas()) {
            LeaveType leaveType = leaveTypeRepository.findById(item.getLeaveTypeId())
                    .orElseThrow(() -> new RuntimeException("Loại phép không tồn tại."));
            EmployeeLeaveQuota quota = employeeLeaveQuotaRepository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), year)
                    .orElseGet(() -> EmployeeLeaveQuota.builder()
                            .employee(employee)
                            .leaveType(leaveType)
                            .year(year)
                            .entitlement(defaultEntitlement(leaveType))
                            .build());
            quota.setEntitlement(item.getEntitlement().setScale(2, RoundingMode.HALF_UP));
            employeeLeaveQuotaRepository.save(quota);
        }

        return getEmployeeQuotas(employeeId, year);
    }

    @Transactional
    public void ensureQuotas(Employee employee, int year) {
        List<LeaveType> activeTypes = leaveTypeRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
        for (LeaveType leaveType : activeTypes) {
            employeeLeaveQuotaRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), year)
                    .orElseGet(() -> employeeLeaveQuotaRepository.save(EmployeeLeaveQuota.builder()
                            .employee(employee)
                            .leaveType(leaveType)
                            .year(year)
                            .entitlement(defaultEntitlement(leaveType))
                            .build()));
        }
    }

    @Transactional
    public void backfillQuotaForType(LeaveType leaveType, int year) {
        employeeRepository.findAll().forEach(employee ->
                employeeLeaveQuotaRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), year)
                        .orElseGet(() -> employeeLeaveQuotaRepository.save(EmployeeLeaveQuota.builder()
                                .employee(employee)
                                .leaveType(leaveType)
                                .year(year)
                                .entitlement(defaultEntitlement(leaveType))
                                .build()))
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateAmount(LeaveRequest request) {
        LeaveType leaveType = request.getLeaveType();
        if (leaveType == null || leaveType.getUnit() == null) {
            return BigDecimal.ZERO;
        }
        if (leaveType.getUnit() == LeaveTypeUnit.DAY 
                && request.getTargetShifts() != null 
                && !request.getTargetShifts().isEmpty()) {
            long totalShifts = employeeShiftRepository.findByEmployeeIdAndWorkDate(
                    request.getEmployee().getId(), 
                    request.getStartDate()
            ).size();
            if (totalShifts > 0) {
                double fraction = (double) request.getTargetShifts().size() / totalShifts;
                return BigDecimal.valueOf(fraction).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return calculateAmount(leaveType, request.getStartDate(), request.getEndDate(), request.getStartTime(), request.getEndTime());
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateAmount(LeaveType leaveType, LocalDate startDate, LocalDate endDate, LocalTime startTime, LocalTime endTime) {
        return switch (leaveType.getUnit()) {
            case DAY -> BigDecimal.valueOf(Math.max(0, ChronoUnit.DAYS.between(startDate, endDate) + 1));
            case HOUR -> BigDecimal.valueOf(Math.max(0, Duration.between(startTime, endTime).toMinutes()))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        };
    }

    @Transactional(readOnly = true)
    public void assertWithinQuota(Employee employee, LeaveType leaveType, int year, BigDecimal requestedAmount, UUID excludingRequestId, boolean includePending) {
        EmployeeLeaveQuota quota = employeeLeaveQuotaRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), year)
                .orElseThrow(() -> new RuntimeException("Chưa cấu hình hạn mức cho loại phép này."));
        BigDecimal used = sumUsage(employee.getId(), leaveType.getId(), year, excludingRequestId, includePending);
        BigDecimal remaining = quota.getEntitlement().subtract(used);
        if (remaining.compareTo(requestedAmount) < 0) {
            throw new RuntimeException("Hạn mức " + leaveType.getName() + " không đủ. Còn " + remaining.stripTrailingZeros().toPlainString() + " " + unitLabel(leaveType) + ".");
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal sumUsage(UUID employeeId, UUID leaveTypeId, int year, UUID excludingRequestId, boolean includePending) {
        List<ApprovalStatus> statuses = includePending
                ? List.of(ApprovalStatus.APPROVED, ApprovalStatus.PENDING, ApprovalStatus.CANCEL_PENDING)
                : List.of(ApprovalStatus.APPROVED, ApprovalStatus.CANCEL_PENDING);
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        return leaveRequestRepository
                .findByEmployeeIdAndLeaveTypeIdAndStatusInAndStartDateBetween(employeeId, leaveTypeId, statuses, start, end)
                .stream()
                .filter(request -> excludingRequestId == null || !excludingRequestId.equals(request.getId()))
                .map(this::calculateAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public LeaveTypeResponse toTypeResponse(LeaveType leaveType) {
        return LeaveTypeResponse.builder()
                .id(leaveType.getId())
                .code(leaveType.getCode())
                .name(leaveType.getName())
                .description(leaveType.getDescription())
                .unit(leaveType.getUnit())
                .active(leaveType.isActive())
                .systemDefault(leaveType.isSystemDefault())
                .sortOrder(leaveType.getSortOrder())
                .build();
    }

    public BigDecimal defaultEntitlement(LeaveType leaveType) {
        if (DEFAULT_AFK_CODE.equals(leaveType.getCode())) {
            return DEFAULT_AFK_ENTITLEMENT;
        }
        return DEFAULT_DAY_ENTITLEMENT;
    }

    private EmployeeLeaveQuotaResponse toQuotaResponse(EmployeeLeaveQuota quota) {
        BigDecimal approved = sumUsage(quota.getEmployee().getId(), quota.getLeaveType().getId(), quota.getYear(), null, false);
        BigDecimal pendingAndApproved = sumUsage(quota.getEmployee().getId(), quota.getLeaveType().getId(), quota.getYear(), null, true);
        BigDecimal pending = pendingAndApproved.subtract(approved);
        return EmployeeLeaveQuotaResponse.builder()
                .employeeId(quota.getEmployee().getId())
                .leaveTypeId(quota.getLeaveType().getId())
                .leaveType(toTypeResponse(quota.getLeaveType()))
                .year(quota.getYear())
                .entitlement(quota.getEntitlement())
                .approvedUsed(approved)
                .pendingUsed(pending)
                .remaining(quota.getEntitlement().subtract(pendingAndApproved))
                .build();
    }

    private String unitLabel(LeaveType leaveType) {
        return leaveType.getUnit() == LeaveTypeUnit.HOUR ? "giờ" : "ngày";
    }

    private int nextSortOrder() {
        return leaveTypeRepository.findAllByOrderBySortOrderAscNameAsc()
                .stream()
                .max(Comparator.comparingInt(LeaveType::getSortOrder))
                .map(type -> type.getSortOrder() + 10)
                .orElse(40);
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeCode(String code, String fallbackName) {
        String source = code == null || code.isBlank() ? fallbackName : code;
        String ascii = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String normalized = ascii.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new RuntimeException("Mã loại phép không hợp lệ.");
        }
        return normalized;
    }
}
