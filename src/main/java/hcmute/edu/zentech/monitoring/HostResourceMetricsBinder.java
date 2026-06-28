package hcmute.edu.zentech.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HostResourceMetricsBinder {
    private final MeterRegistry meterRegistry;
    private final HostResourceMetricsProvider provider;

    @PostConstruct
    void bindMetrics() {
        registerGauge("zentech.host.cpu.usage.percent", "CPU usage of the host running ZenTech", "percent", Metric.CPU_USAGE);
        registerGauge("zentech.host.cpu.cores", "CPU cores available to the host running ZenTech", "cores", Metric.CPU_CORES);
        registerGauge("zentech.host.ram.usage.percent", "RAM usage of the host running ZenTech", "percent", Metric.RAM_USAGE);
        registerGauge("zentech.host.ram.used.bytes", "RAM used by the host running ZenTech", "bytes", Metric.RAM_USED);
        registerGauge("zentech.host.ram.total.bytes", "Total RAM available to the host running ZenTech", "bytes", Metric.RAM_TOTAL);
        registerGauge("zentech.host.disk.usage.percent", "Disk usage of the application partition", "percent", Metric.DISK_USAGE);
        registerGauge("zentech.host.disk.used.bytes", "Disk used on the application partition", "bytes", Metric.DISK_USED);
        registerGauge("zentech.host.disk.total.bytes", "Total disk space on the application partition", "bytes", Metric.DISK_TOTAL);
    }

    private void registerGauge(String name, String description, String baseUnit, Metric metric) {
        Gauge.builder(name, provider, source -> value(source.snapshot(), metric))
                .description(description)
                .baseUnit(baseUnit)
                .register(meterRegistry);
    }

    private double value(HostResourceMetricsProvider.HostResourceSnapshot snapshot, Metric metric) {
        if (!snapshot.available()) return Double.NaN;
        Number value = switch (metric) {
            case CPU_USAGE -> snapshot.cpuUsagePercent();
            case CPU_CORES -> snapshot.cpuCoreCount();
            case RAM_USAGE -> snapshot.ramUsagePercent();
            case RAM_USED -> snapshot.ramUsedBytes();
            case RAM_TOTAL -> snapshot.ramTotalBytes();
            case DISK_USAGE -> snapshot.diskUsagePercent();
            case DISK_USED -> snapshot.diskUsedBytes();
            case DISK_TOTAL -> snapshot.diskTotalBytes();
        };
        return value == null ? Double.NaN : value.doubleValue();
    }

    private enum Metric { CPU_USAGE, CPU_CORES, RAM_USAGE, RAM_USED, RAM_TOTAL, DISK_USAGE, DISK_USED, DISK_TOTAL }
}