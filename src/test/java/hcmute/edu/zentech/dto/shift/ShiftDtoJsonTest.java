package hcmute.edu.zentech.dto.shift;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import hcmute.edu.zentech.model.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftDtoJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shiftDtoSerializesLocalTimeAsWallClockString() throws Exception {
        ShiftDto dto = new ShiftDto();
        dto.setName("AM");
        dto.setType(ShiftType.NORMAL);
        dto.setStartTime(LocalTime.of(9, 0));
        dto.setEndTime(LocalTime.of(12, 0));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertEquals("09:00:00", json.get("startTime").asText());
        assertEquals("12:00:00", json.get("endTime").asText());
        assertTrue(json.get("startTime").isTextual());
    }

    @Test
    void weeklyScheduleDtoSerializesLocalTimeAsWallClockString() throws Exception {
        EmployeeWeeklyScheduleDto.DailyShiftDto dto = new EmployeeWeeklyScheduleDto.DailyShiftDto();
        dto.setShiftName("PM");
        dto.setWorkDate(LocalDate.of(2026, 7, 7));
        dto.setShiftType(ShiftType.NORMAL);
        dto.setStartTime(LocalTime.of(13, 30));
        dto.setEndTime(LocalTime.of(18, 0));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertEquals("13:30:00", json.get("startTime").asText());
        assertEquals("18:00:00", json.get("endTime").asText());
        assertTrue(json.get("startTime").isTextual());
    }
}
