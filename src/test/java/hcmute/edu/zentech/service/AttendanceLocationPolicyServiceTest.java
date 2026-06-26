package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.attendance.GeoPointDto;
import hcmute.edu.zentech.model.AttendanceLocationPolicy;
import hcmute.edu.zentech.model.AttendanceLocationShapeType;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.AttendanceLocationPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceLocationPolicyServiceTest {
    @Mock
    private AttendanceLocationPolicyRepository policyRepository;
    @Mock
    private AccountUserRepository accountUserRepository;

    private ObjectMapper objectMapper;
    private AttendanceLocationPolicyService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AttendanceLocationPolicyService(policyRepository, accountUserRepository, objectMapper);
    }

    @Test
    void circleAllowsInsideAndBoundaryButRejectsOutside() {
        AttendanceLocationPolicy policy = new AttendanceLocationPolicy();
        policy.setEnabled(true);
        policy.setShapeType(AttendanceLocationShapeType.CIRCLE);
        policy.setCenterLatitude(10.0);
        policy.setCenterLongitude(106.0);
        policy.setRadiusMeters(120.0);

        when(policyRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(policy));

        assertTrue(service.isLocationAllowed(10.0005, 106.0));
        assertTrue(service.isLocationAllowed(10.00107, 106.0));
        assertFalse(service.isLocationAllowed(10.002, 106.0));
    }

    @Test
    void polygonAllowsInsideAndEdgeButRejectsOutside() throws Exception {
        AttendanceLocationPolicy policy = new AttendanceLocationPolicy();
        policy.setEnabled(true);
        policy.setShapeType(AttendanceLocationShapeType.POLYGON);
        policy.setPolygonPointsJson(objectMapper.writeValueAsString(List.of(
                new GeoPointDto(10.0, 106.0),
                new GeoPointDto(10.0, 106.01),
                new GeoPointDto(10.01, 106.01),
                new GeoPointDto(10.01, 106.0)
        )));

        when(policyRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(policy));

        assertTrue(service.isLocationAllowed(10.005, 106.005));
        assertTrue(service.isLocationAllowed(10.0, 106.005));
        assertFalse(service.isLocationAllowed(10.02, 106.005));
    }

    @Test
    void polygonWithTooFewPointsRejectsLocation() throws Exception {
        AttendanceLocationPolicy policy = new AttendanceLocationPolicy();
        policy.setEnabled(true);
        policy.setShapeType(AttendanceLocationShapeType.POLYGON);
        policy.setPolygonPointsJson(objectMapper.writeValueAsString(List.of(
                new GeoPointDto(10.0, 106.0),
                new GeoPointDto(10.0, 106.01)
        )));

        when(policyRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.of(policy));

        assertFalse(service.isLocationAllowed(10.0, 106.0));
    }
}
