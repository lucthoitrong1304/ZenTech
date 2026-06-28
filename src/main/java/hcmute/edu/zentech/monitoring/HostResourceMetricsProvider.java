package hcmute.edu.zentech.monitoring;

import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Component
@Slf4j
public class HostResourceMetricsProvider {

    public HostResourceSnapshot snapshot() {
        Instant generatedAt = Instant.now();
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            if (osBean == null) {
                throw new IllegalStateException("Operating system metrics are not supported by this JVM");
            }

            long totalRam = osBean.getTotalMemorySize();
            long freeRam = osBean.getFreeMemorySize();
            long usedRam = Math.max(0, totalRam - freeRam);
            double cpuLoad = osBean.getCpuLoad();
            int cpuCores = osBean.getAvailableProcessors();

            Path applicationPath = Path.of("").toAbsolutePath().normalize();
            FileStore fileStore = Files.getFileStore(applicationPath);
            long totalDisk = fileStore.getTotalSpace();
            long usableDisk = fileStore.getUsableSpace();
            long usedDisk = Math.max(0, totalDisk - usableDisk);

            return new HostResourceSnapshot(
                    true,
                    cpuLoad >= 0 ? roundTwoDecimals(cpuLoad * 100) : null,
                    cpuCores,
                    percent(usedRam, totalRam),
                    percent(usedDisk, totalDisk),
                    usedRam,
                    totalRam,
                    usedDisk,
                    totalDisk,
                    applicationPath.getRoot() != null ? applicationPath.getRoot().toString() : applicationPath.toString(),
                    generatedAt,
                    null
            );
        } catch (Exception exception) {
            log.warn("Unable to read host resource metrics", exception);
            return new HostResourceSnapshot(
                    false, null, null, null, null, null, null, null, null, null,
                    generatedAt,
                    "Khong the doc tai nguyen may chu tai thoi diem nay."
            );
        }
    }

    private Double percent(long used, long total) {
        return total <= 0 ? null : roundTwoDecimals((used * 100.0) / total);
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record HostResourceSnapshot(
            boolean available,
            Double cpuUsagePercent,
            Integer cpuCoreCount,
            Double ramUsagePercent,
            Double diskUsagePercent,
            Long ramUsedBytes,
            Long ramTotalBytes,
            Long diskUsedBytes,
            Long diskTotalBytes,
            String diskPath,
            Instant generatedAt,
            String message
    ) {}
}
