package hcmute.edu.zentech.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationTimeZoneConfigTest {
    private final TimeZone originalTimeZone = TimeZone.getDefault();

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void configureDefaultTimeZoneUsesConfiguredUtcPlusSeven() {
        ApplicationTimeZoneConfig config = new ApplicationTimeZoneConfig();
        ReflectionTestUtils.setField(config, "appTimeZone", "GMT+07:00");

        config.configureDefaultTimeZone();

        assertEquals("GMT+07:00", TimeZone.getDefault().getID());
    }

    @Test
    void configureDefaultTimeZoneFallsBackToUtcPlusSevenWhenInvalid() {
        ApplicationTimeZoneConfig config = new ApplicationTimeZoneConfig();
        ReflectionTestUtils.setField(config, "appTimeZone", "not-a-zone");

        config.configureDefaultTimeZone();

        assertEquals("GMT+07:00", TimeZone.getDefault().getID());
    }
}
