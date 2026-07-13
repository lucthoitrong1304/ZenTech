package hcmute.edu.zentech.repository;
import hcmute.edu.zentech.model.AttendanceReportPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AttendanceReportPreferenceRepository extends JpaRepository<AttendanceReportPreference, UUID> {}
