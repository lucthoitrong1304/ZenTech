package hcmute.edu.zentech.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class WorkScheduleDataResetInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Value("${zentech.work-schedule.reset-on-startup:false}")
    private boolean resetOnStartup;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!resetOnStartup) {
            return;
        }

        deleteIfTableExists("attendance_events", "employee_shift_id IS NOT NULL");
        deleteIfTableExists("shift_swap_requests", null);
        deleteIfTableExists("schedule_adjustments", null);
        deleteIfTableExists("employee_shifts", null);

        log.warn("Work schedule assignment data was reset because zentech.work-schedule.reset-on-startup=true.");
    }

    private void deleteIfTableExists(String tableName, String whereClause) {
        if (!tableExists(tableName)) {
            return;
        }
        String sql = "DELETE FROM " + tableName + (whereClause == null || whereClause.isBlank() ? "" : " WHERE " + whereClause);
        jdbcTemplate.update(sql);
    }

    private boolean tableExists(String tableName) {
        return !jdbcTemplate.queryForList("SHOW TABLES LIKE ?", String.class, tableName).isEmpty();
    }
}
