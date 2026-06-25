package hcmute.edu.zentech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.attendance.AttendanceLocationPolicyDto;
import hcmute.edu.zentech.dto.attendance.GeoPointDto;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.AttendanceLocationPolicy;
import hcmute.edu.zentech.model.AttendanceLocationShapeType;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.AttendanceLocationPolicyRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceLocationPolicyService {
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private final AttendanceLocationPolicyRepository policyRepository;
    private final AccountUserRepository accountUserRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AttendanceLocationPolicyDto getPolicy() {
        return policyRepository.findTopByOrderByUpdatedAtDesc()
                .map(this::toDto)
                .orElseGet(this::defaultPolicy);
    }

    @Transactional
    public AttendanceLocationPolicyDto updatePolicy(AttendanceLocationPolicyDto dto) {
        validatePolicy(dto);

        AttendanceLocationPolicy policy = policyRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(AttendanceLocationPolicy::new);
        policy.setEnabled(dto.isEnabled());
        policy.setShapeType(dto.getShapeType());
        policy.setCenterLatitude(dto.getCenterLatitude());
        policy.setCenterLongitude(dto.getCenterLongitude());
        policy.setRadiusMeters(dto.getRadiusMeters());
        policy.setPolygonPointsJson(writePolygonPoints(dto.getPolygonPoints()));
        policy.setUpdatedAt(LocalDateTime.now());

        UUID accountId = SecurityContextUtils.getCurrentUserId();
        AccountUser updater = accountId == null ? null : accountUserRepository.findById(accountId).orElse(null);
        policy.setUpdatedBy(updater);

        return toDto(policyRepository.save(policy));
    }

    @Transactional(readOnly = true)
    public boolean isLocationAllowed(Double latitude, Double longitude) {
        Optional<AttendanceLocationPolicy> policyOpt = policyRepository.findTopByOrderByUpdatedAtDesc();

        if (policyOpt.isEmpty() || !policyOpt.get().isEnabled()) {
            return true;
        }

        if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) {
            return false;
        }

        AttendanceLocationPolicy policy = policyOpt.get();
        if (policy.getShapeType() == AttendanceLocationShapeType.CIRCLE) {
            return isInsideCircle(policy, latitude, longitude);
        }
        if (policy.getShapeType() == AttendanceLocationShapeType.POLYGON) {
            return isInsidePolygon(readPolygonPoints(policy.getPolygonPointsJson()), latitude, longitude);
        }

        return false;
    }

    @Transactional(readOnly = true)
    public boolean isPolicyEnabled() {
        return policyRepository.findTopByOrderByUpdatedAtDesc()
                .map(AttendanceLocationPolicy::isEnabled)
                .orElse(false);
    }

    private AttendanceLocationPolicyDto defaultPolicy() {
        AttendanceLocationPolicyDto dto = new AttendanceLocationPolicyDto();
        dto.setEnabled(false);
        dto.setShapeType(AttendanceLocationShapeType.CIRCLE);
        dto.setRadiusMeters(100.0);
        return dto;
    }

    private AttendanceLocationPolicyDto toDto(AttendanceLocationPolicy policy) {
        AttendanceLocationPolicyDto dto = new AttendanceLocationPolicyDto();
        dto.setId(policy.getId());
        dto.setEnabled(policy.isEnabled());
        dto.setShapeType(policy.getShapeType());
        dto.setCenterLatitude(policy.getCenterLatitude());
        dto.setCenterLongitude(policy.getCenterLongitude());
        dto.setRadiusMeters(policy.getRadiusMeters());
        dto.setPolygonPoints(readPolygonPoints(policy.getPolygonPointsJson()));
        dto.setUpdatedAt(policy.getUpdatedAt());
        dto.setUpdatedBy(policy.getUpdatedBy() == null ? null : policy.getUpdatedBy().getId());
        return dto;
    }

    private void validatePolicy(AttendanceLocationPolicyDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Cấu hình vị trí không hợp lệ.");
        }

        if (!dto.isEnabled()) {
            return;
        }

        if (dto.getShapeType() == null) {
            throw new IllegalArgumentException("Vui lòng chọn kiểu vùng check-in.");
        }

        if (dto.getShapeType() == AttendanceLocationShapeType.CIRCLE) {
            if (!isValidLatitude(dto.getCenterLatitude()) || !isValidLongitude(dto.getCenterLongitude())) {
                throw new IllegalArgumentException("Tâm vùng check-in không hợp lệ.");
            }
            if (dto.getRadiusMeters() == null || dto.getRadiusMeters() <= 0) {
                throw new IllegalArgumentException("Bán kính vùng check-in phải lớn hơn 0.");
            }
        }

        if (dto.getShapeType() == AttendanceLocationShapeType.POLYGON) {
            List<GeoPointDto> points = dto.getPolygonPoints() == null ? List.of() : dto.getPolygonPoints();
            if (points.size() < 3) {
                throw new IllegalArgumentException("Vùng polygon cần ít nhất 3 điểm.");
            }
            for (GeoPointDto point : points) {
                if (point == null || !isValidLatitude(point.getLat()) || !isValidLongitude(point.getLng())) {
                    throw new IllegalArgumentException("Tọa độ polygon không hợp lệ.");
                }
            }
        }
    }

    private boolean isInsideCircle(AttendanceLocationPolicy policy, double latitude, double longitude) {
        if (!isValidLatitude(policy.getCenterLatitude()) || !isValidLongitude(policy.getCenterLongitude())
                || policy.getRadiusMeters() == null) {
            return false;
        }

        return haversineMeters(policy.getCenterLatitude(), policy.getCenterLongitude(), latitude, longitude)
                <= policy.getRadiusMeters();
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private boolean isInsidePolygon(List<GeoPointDto> points, double latitude, double longitude) {
        if (points == null || points.size() < 3) {
            return false;
        }

        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            GeoPointDto current = points.get(i);
            GeoPointDto previous = points.get(j);

            if (isPointOnSegment(previous, current, latitude, longitude)) {
                return true;
            }

            boolean intersects = ((current.getLat() > latitude) != (previous.getLat() > latitude))
                    && (longitude < (previous.getLng() - current.getLng()) * (latitude - current.getLat())
                    / (previous.getLat() - current.getLat()) + current.getLng());
            if (intersects) {
                inside = !inside;
            }
        }

        return inside;
    }

    private boolean isPointOnSegment(GeoPointDto a, GeoPointDto b, double latitude, double longitude) {
        double cross = (longitude - a.getLng()) * (b.getLat() - a.getLat())
                - (latitude - a.getLat()) * (b.getLng() - a.getLng());
        if (Math.abs(cross) > 1e-10) {
            return false;
        }

        double minLat = Math.min(a.getLat(), b.getLat()) - 1e-10;
        double maxLat = Math.max(a.getLat(), b.getLat()) + 1e-10;
        double minLng = Math.min(a.getLng(), b.getLng()) - 1e-10;
        double maxLng = Math.max(a.getLng(), b.getLng()) + 1e-10;
        return latitude >= minLat && latitude <= maxLat && longitude >= minLng && longitude <= maxLng;
    }

    private boolean isValidLatitude(Double latitude) {
        return latitude != null && latitude >= -90 && latitude <= 90;
    }

    private boolean isValidLongitude(Double longitude) {
        return longitude != null && longitude >= -180 && longitude <= 180;
    }

    private String writePolygonPoints(List<GeoPointDto> points) {
        try {
            return objectMapper.writeValueAsString(points == null ? List.of() : points);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Không thể lưu vùng polygon.", e);
        }
    }

    private List<GeoPointDto> readPolygonPoints(String pointsJson) {
        if (pointsJson == null || pointsJson.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(pointsJson, new TypeReference<List<GeoPointDto>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
