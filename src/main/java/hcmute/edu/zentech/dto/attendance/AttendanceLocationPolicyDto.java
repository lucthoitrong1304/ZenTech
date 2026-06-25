package hcmute.edu.zentech.dto.attendance;

import hcmute.edu.zentech.model.AttendanceLocationShapeType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class AttendanceLocationPolicyDto {
    private UUID id;
    private boolean enabled;
    private AttendanceLocationShapeType shapeType;
    private Double centerLatitude;
    private Double centerLongitude;
    private Double radiusMeters;
    private List<GeoPointDto> polygonPoints = new ArrayList<>();
    private LocalDateTime updatedAt;
    private UUID updatedBy;
}
