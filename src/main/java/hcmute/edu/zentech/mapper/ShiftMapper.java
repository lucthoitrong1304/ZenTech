package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.shift.ShiftCreateDto;
import hcmute.edu.zentech.dto.shift.ShiftDto;
import hcmute.edu.zentech.model.Shift;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ShiftMapper {
    public Shift toEntity(ShiftCreateDto dto) {
        if (dto == null) return null;
        Shift shift = new Shift();
        shift.setName(dto.getName());
        shift.setStartTime(dto.getStartTime());
        shift.setEndTime(dto.getEndTime());
        shift.setColorCode(dto.getColorCode());
        shift.setDefault(dto.isDefault());
        shift.setType(dto.getType());
        applyAttendanceWindow(shift, dto.getEarlyCheckInMinutes(), dto.getLateCheckOutMinutes(),
                dto.getOnTimeCheckInStartMinutes(), dto.getOnTimeCheckInEndMinutes(),
                dto.getOnTimeCheckOutStartMinutes(), dto.getOnTimeCheckOutEndMinutes());
        return shift;
    }

    public ShiftDto toDto(Shift shift) {
        if (shift == null) return null;
        ShiftDto dto = new ShiftDto();
        dto.setId(shift.getId());
        dto.setName(shift.getName());
        dto.setStartTime(shift.getStartTime());
        dto.setEndTime(shift.getEndTime());
        dto.setColorCode(shift.getColorCode());
        dto.setDefault(shift.isDefault());
        dto.setType(shift.getType());
        dto.setEarlyCheckInMinutes(defaultInt(shift.getEarlyCheckInMinutes(), 30));
        dto.setLateCheckOutMinutes(defaultInt(shift.getLateCheckOutMinutes(), 60));
        dto.setOnTimeCheckInStartMinutes(defaultInt(shift.getOnTimeCheckInStartMinutes(), 15));
        dto.setOnTimeCheckInEndMinutes(defaultInt(shift.getOnTimeCheckInEndMinutes(), 5));
        dto.setOnTimeCheckOutStartMinutes(defaultInt(shift.getOnTimeCheckOutStartMinutes(), 5));
        dto.setOnTimeCheckOutEndMinutes(defaultInt(shift.getOnTimeCheckOutEndMinutes(), 15));
        return dto;
    }
    
    public List<ShiftDto> toDtoList(List<Shift> shifts) {
        return shifts.stream().map(this::toDto).collect(Collectors.toList());
    }

    public void applyDto(Shift shift, ShiftDto dto) {
        shift.setName(dto.getName());
        shift.setStartTime(dto.getStartTime());
        shift.setEndTime(dto.getEndTime());
        shift.setColorCode(dto.getColorCode());
        shift.setDefault(dto.isDefault());
        shift.setType(dto.getType());
        applyAttendanceWindow(shift, dto.getEarlyCheckInMinutes(), dto.getLateCheckOutMinutes(),
                dto.getOnTimeCheckInStartMinutes(), dto.getOnTimeCheckInEndMinutes(),
                dto.getOnTimeCheckOutStartMinutes(), dto.getOnTimeCheckOutEndMinutes());
    }

    private void applyAttendanceWindow(Shift shift,
                                       Integer earlyCheckInMinutes,
                                       Integer lateCheckOutMinutes,
                                       Integer onTimeCheckInStartMinutes,
                                       Integer onTimeCheckInEndMinutes,
                                       Integer onTimeCheckOutStartMinutes,
                                       Integer onTimeCheckOutEndMinutes) {
        shift.setEarlyCheckInMinutes(defaultInt(earlyCheckInMinutes, 30));
        shift.setLateCheckOutMinutes(defaultInt(lateCheckOutMinutes, 60));
        shift.setOnTimeCheckInStartMinutes(defaultInt(onTimeCheckInStartMinutes, 15));
        shift.setOnTimeCheckInEndMinutes(defaultInt(onTimeCheckInEndMinutes, 5));
        shift.setOnTimeCheckOutStartMinutes(defaultInt(onTimeCheckOutStartMinutes, 5));
        shift.setOnTimeCheckOutEndMinutes(defaultInt(onTimeCheckOutEndMinutes, 15));
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : Math.max(0, value);
    }
}
