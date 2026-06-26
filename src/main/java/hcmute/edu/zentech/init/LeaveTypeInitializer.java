package hcmute.edu.zentech.init;

import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.LeaveType;
import hcmute.edu.zentech.model.LeaveTypeUnit;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.repository.LeaveTypeRepository;
import hcmute.edu.zentech.service.LeaveManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LeaveTypeInitializer implements ApplicationRunner {
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveManagementService leaveManagementService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LeaveType nghi = ensureType(LeaveManagementService.DEFAULT_NGHI_CODE, "Nghỉ", "Nghỉ phép theo ngày", LeaveTypeUnit.DAY, 10);
        ensureType(LeaveManagementService.DEFAULT_WFH_CODE, "WFH", "Làm việc tại nhà", LeaveTypeUnit.DAY, 20);
        ensureType(LeaveManagementService.DEFAULT_AFK_CODE, "AFK", "Xin phép rời ca/về sớm", LeaveTypeUnit.HOUR, 30);

        int currentYear = LocalDate.now().getYear();
        for (Employee employee : employeeRepository.findAll()) {
            leaveManagementService.ensureQuotas(employee, currentYear);
        }
        migrateLegacyLeaveRequests(nghi.getId());
        deleteUnsupportedRequestData();
    }

    private LeaveType ensureType(String code, String name, String description, LeaveTypeUnit unit, int sortOrder) {
        return leaveTypeRepository.findByCode(code)
                .map(type -> {
                    type.setName(name);
                    type.setDescription(description);
                    type.setUnit(unit);
                    type.setActive(true);
                    type.setSystemDefault(true);
                    type.setSortOrder(sortOrder);
                    return leaveTypeRepository.save(type);
                })
                .orElseGet(() -> leaveTypeRepository.save(LeaveType.builder()
                        .code(code)
                        .name(name)
                        .description(description)
                        .unit(unit)
                        .active(true)
                        .systemDefault(true)
                        .sortOrder(sortOrder)
                        .build()));
    }

    private void migrateLegacyLeaveRequests(UUID nghiTypeId) {
        if (!tableExists("leave_requests") || !columnExists("leave_requests", "leave_type")) {
            return;
        }

        if (columnExists("leave_requests", "leave_type_id")) {
            jdbcTemplate.update(
                    "UPDATE leave_requests SET leave_type_id = UNHEX(REPLACE(?, '-', '')) WHERE leave_type = 'PAID' AND leave_type_id IS NULL",
                    nghiTypeId.toString()
            );
        }
        jdbcTemplate.update("DELETE FROM leave_requests WHERE leave_type IN ('UNPAID', 'SICK')");
    }

    private void deleteUnsupportedRequestData() {
        if (tableExists("shift_swap_requests")) {
            jdbcTemplate.update("DELETE FROM shift_swap_requests");
        }
        if (tableExists("attendance_adjustments")) {
            jdbcTemplate.update("DELETE FROM attendance_adjustments");
        }
    }

    private boolean tableExists(String tableName) {
        return !jdbcTemplate.queryForList("SHOW TABLES LIKE ?", String.class, tableName).isEmpty();
    }

    private boolean columnExists(String tableName, String columnName) {
        List<java.util.Map<String, Object>> columns = jdbcTemplate.queryForList("SHOW COLUMNS FROM " + tableName + " LIKE ?", columnName);
        return !columns.isEmpty();
    }
}
