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
        return dto;
    }
    
    public List<ShiftDto> toDtoList(List<Shift> shifts) {
        return shifts.stream().map(this::toDto).collect(Collectors.toList());
    }
}
