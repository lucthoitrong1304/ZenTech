package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.request.CheckInRequest;
import hcmute.edu.zentech.dto.response.EmployeeProfileResponse;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.AttendanceRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.utils.FaceEncryptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private AccountUserRepository accountUserRepository;
    @Mock
    private R2StorageService r2StorageService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private FaceEncryptionUtils faceEncryptionUtils;
    @Mock
    private AdminActivityLogService adminActivityLogService;

    @InjectMocks
    private AttendanceService attendanceService;

    private MockedStatic<SecurityContextUtils> securityContextUtilsMock;
    private UUID mockAccountId;
    private Employee mockEmployee;
    private AccountUser mockUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(attendanceService, "faceMatchThreshold", 0.5);
        mockAccountId = UUID.randomUUID();
        
        mockUser = new AccountUser();
        mockUser.setId(mockAccountId);
        mockUser.setEmail("test@zentech.com");
        mockUser.setRole(Role.EMPLOYEE);
        mockUser.setActive(true);

        mockEmployee = new Employee();
        mockEmployee.setId(UUID.randomUUID());
        mockEmployee.setFullName("Test Employee");
        mockEmployee.setUserInfo(mockUser);
        mockEmployee.setFaceDescriptors("encrypted-string");

        // Mock static SecurityContextUtils
        securityContextUtilsMock = Mockito.mockStatic(SecurityContextUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityContextUtilsMock.close();
    }

    @Test
    void testCheckInSuccess() throws Exception {
        // Arrange
        securityContextUtilsMock.when(SecurityContextUtils::getCurrentUserId).thenReturn(mockAccountId);
        
        CheckInRequest request = new CheckInRequest();
        List<Float> inputDescriptor = new ArrayList<>(Collections.nCopies(128, 0.1f));
        request.setFaceDescriptor(inputDescriptor);

        when(employeeRepository.findByUserInfo_Id(mockAccountId)).thenReturn(Optional.of(mockEmployee));
        when(faceEncryptionUtils.decrypt("encrypted-string")).thenReturn("decrypted-json");
        
        List<List<Float>> registeredDescriptors = new ArrayList<>();
        // Add a matching descriptor (euclidean distance will be 0)
        registeredDescriptors.add(new ArrayList<>(Collections.nCopies(128, 0.1f)));
        
        when(objectMapper.readValue(eq("decrypted-json"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(registeredDescriptors);

        // Act
        EmployeeProfileResponse response = attendanceService.checkIn(request);

        // Assert
        assertNotNull(response);
        assertEquals(mockEmployee.getFullName(), response.getFullName());
        verify(attendanceRepository, times(1)).save(any(Attendance.class));
        verify(adminActivityLogService, times(1)).log(
                eq(mockAccountId),
                eq(ActivityArea.MANAGEMENT),
                eq("EMPLOYEE"),
                eq(ActivityAction.FACE_VERIFICATION_SUCCESS),
                eq(ActivitySeverity.INFO),
                eq("Employee"),
                anyString(),
                anyString(),
                anyString(),
                isNull()
        );
    }

    @Test
    void testCheckInFaceNotMatch() throws Exception {
        // Arrange
        securityContextUtilsMock.when(SecurityContextUtils::getCurrentUserId).thenReturn(mockAccountId);
        
        CheckInRequest request = new CheckInRequest();
        List<Float> inputDescriptor = new ArrayList<>(Collections.nCopies(128, 0.1f));
        request.setFaceDescriptor(inputDescriptor);

        when(employeeRepository.findByUserInfo_Id(mockAccountId)).thenReturn(Optional.of(mockEmployee));
        when(faceEncryptionUtils.decrypt("encrypted-string")).thenReturn("decrypted-json");
        
        List<List<Float>> registeredDescriptors = new ArrayList<>();
        // Add a non-matching descriptor (all 0.9f, distance will be large)
        registeredDescriptors.add(new ArrayList<>(Collections.nCopies(128, 0.9f)));
        
        when(objectMapper.readValue(eq("decrypted-json"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(registeredDescriptors);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            attendanceService.checkIn(request);
        });

        assertEquals("Không nhận diện được khuôn mặt. Vui lòng thử lại.", exception.getMessage());
        verify(attendanceRepository, never()).save(any(Attendance.class));
        verify(adminActivityLogService, times(1)).log(
                eq(mockAccountId),
                eq(ActivityArea.MANAGEMENT),
                eq("EMPLOYEE"),
                eq(ActivityAction.FACE_VERIFICATION_FAILED),
                eq(ActivitySeverity.WARNING),
                eq("Employee"),
                anyString(),
                anyString(),
                anyString(),
                isNull()
        );
    }

    @Test
    void testCheckInRateLimit() throws Exception {
        // Arrange
        securityContextUtilsMock.when(SecurityContextUtils::getCurrentUserId).thenReturn(mockAccountId);
        
        CheckInRequest request = new CheckInRequest();
        List<Float> inputDescriptor = new ArrayList<>(Collections.nCopies(128, 0.1f));
        request.setFaceDescriptor(inputDescriptor);

        when(employeeRepository.findByUserInfo_Id(mockAccountId)).thenReturn(Optional.of(mockEmployee));
        when(faceEncryptionUtils.decrypt("encrypted-string")).thenReturn("decrypted-json");
        
        List<List<Float>> registeredDescriptors = new ArrayList<>();
        registeredDescriptors.add(new ArrayList<>(Collections.nCopies(128, 0.9f))); // non-matching
        
        when(objectMapper.readValue(eq("decrypted-json"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(registeredDescriptors);

        // Fail 5 times to trigger lock
        for (int i = 0; i < 5; i++) {
            assertThrows(RuntimeException.class, () -> {
                attendanceService.checkIn(request);
            });
        }

        // The 6th time should trigger lockout exception from rate limit checker directly
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            attendanceService.checkIn(request);
        });

        assertTrue(exception.getMessage().contains("Tài khoản của bạn đã bị khóa chức năng điểm danh"));
        
        // Total failures recorded and logged should be 5 (not 6, since 6th is locked out)
        verify(adminActivityLogService, times(5)).log(
                eq(mockAccountId),
                any(),
                any(),
                eq(ActivityAction.FACE_VERIFICATION_FAILED),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }
}
