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
        registerGauge("zentech.host.cpu.usage.percent", "CPU usage of the host running ZenTech", Metric.CPU);
        registerGauge("zentech.host.ram.usage.percent", "RAM usage of the host running ZenTech", Metric.RAM);
        registerGauge("zentech.host.disk.usage.percent", "Disk usage of the application partition", Metric.DISK);
    }

    private void registerGauge(String name, String description, Metric metric) {
        Gauge.builder(name, provider, source -> value(source.snapshot(), metric))
                .description(description)
                .baseUnit("percent")
                .register(meterRegistry);
    }

    private double value(HostResourceMetricsProvider.HostResourceSnapshot snapshot, Metric metric) {
        if (!snapshot.available()) return Double.NaN;
        Double value = switch (metric) {
            case CPU -> snapshot.cpuUsagePercent();
            case RAM -> snapshot.ramUsagePercent();
            case DISK -> snapshot.diskUsagePercent();
        };
        return value == null ? Double.NaN : value;
    }

    private enum Metric { CPU, RAM, DISK }
}
