package hcmute.edu.zentech.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.TimeZone;

@Configuration
public class ApplicationTimeZoneConfig {
    private static final String DEFAULT_TIME_ZONE = "Asia/Ho_Chi_Minh";

    @Value("${app.time-zone:" + DEFAULT_TIME_ZONE + "}")
    private String appTimeZone;

    @PostConstruct
    public void configureDefaultTimeZone() {
        TimeZone.setDefault(resolveTimeZone(appTimeZone));
    }

    static TimeZone resolveTimeZone(String configuredTimeZone) {
        String zoneId = configuredTimeZone == null || configuredTimeZone.isBlank()
                ? DEFAULT_TIME_ZONE
                : configuredTimeZone.trim();

        try {
            return TimeZone.getTimeZone(ZoneId.of(zoneId));
        } catch (ZoneRulesException | IllegalArgumentException ignored) {
            return TimeZone.getTimeZone(ZoneId.of(DEFAULT_TIME_ZONE));
        }
    }
}
