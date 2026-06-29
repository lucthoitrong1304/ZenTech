package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.shift.*;
import hcmute.edu.zentech.mapper.ShiftMapper;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import hcmute.edu.zentech.repository.projection.EmployeeWeeklyScheduleProjection;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShiftService {
    private final ShiftRepository shiftRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftMapper shiftMapper;
    private final PayPeriodRepository payPeriodRepository;
    private final AttendanceEventRepository attendanceEventRepository;
    private final ScheduleAdjustmentRepository scheduleAdjustmentRepository;
    private final AccountUserRepository accountUserRepository;
    private final NotificationService notificationService;
    private final AttendanceCalculator attendanceCalculator;


    @Transactional
    public ShiftDto createShift(ShiftCreateDto dto) {
        Shift shift = shiftMapper.toEntity(dto);
        shift = shiftRepository.save(shift);
        return shiftMapper.toDto(shift);
    }

    @Transactional(readOnly = true)
    public List<ShiftDto> getAllShifts() {
        return shiftMapper.toDtoList(shiftRepository.findAll());
    }

    @Transactional
    public List<ShiftDto> updateShifts(List<ShiftDto> updateDtos) {
        List<Shift> updatedShifts = new ArrayList<>();
        for (ShiftDto dto : updateDtos) {
            if (dto.getId() != null) {
                Shift shift = shiftRepository.findById(dto.getId()).orElse(null);
                if (shift != null) {
                    shiftMapper.applyDto(shift, dto);
                    updatedShifts.add(shiftRepository.save(shift));
                }
            }
        }
        return shiftMapper.toDtoList(updatedShifts);
    }

    public Page<EmployeeWeeklyScheduleDto> getWeeklySchedules(LocalDate startDate, LocalDate endDate, String keyword, Pageable pageable) {
        Page<Employee> employeePage = employeeRepository.searchEmployees(keyword, null, null, pageable);
        
        if (employeePage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, employeePage.getTotalElements());
        }
        
        List<EmployeeWeeklyScheduleDto> dtos = employeePage.getContent().stream().map(emp -> {
            EmployeeWeeklyScheduleDto dto = new EmployeeWeeklyScheduleDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getFullName());
            
            List<EmployeeWeeklyScheduleDto.DailyShiftDto> dailyShifts = new ArrayList<>();
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                List<AttendanceCalculator.EffectiveShift> effShifts = attendanceCalculator.resolveEffectiveShifts(emp.getId(), date);
                for (AttendanceCalculator.EffectiveShift eff : effShifts) {
                    EmployeeWeeklyScheduleDto.DailyShiftDto dailyDto = new EmployeeWeeklyScheduleDto.DailyShiftDto();
                    dailyDto.setEmployeeShiftId(eff.assignment() != null ? eff.assignment().getId() : null);
                    dailyDto.setShiftId(eff.shift().getId());
                    dailyDto.setShiftName(eff.shift().getName());
                    dailyDto.setColorCode(eff.shift().getColorCode());
                    dailyDto.setWorkDate(date);
                    dailyDto.setStartTime(eff.shift().getStartTime());
                    dailyDto.setEndTime(eff.shift().getEndTime());
                    dailyDto.setShiftType(eff.shift().getType());
                    dailyDto.setEarlyCheckInMinutes(defaultInt(eff.shift().getEarlyCheckInMinutes(), 30));
                    dailyDto.setLateCheckOutMinutes(defaultInt(eff.shift().getLateCheckOutMinutes(), 60));
                    dailyDto.setOnTimeCheckInStartMinutes(defaultInt(eff.shift().getOnTimeCheckInStartMinutes(), 15));
                    dailyDto.setOnTimeCheckInEndMinutes(defaultInt(eff.shift().getOnTimeCheckInEndMinutes(), 5));
                    dailyDto.setOnTimeCheckOutStartMinutes(defaultInt(eff.shift().getOnTimeCheckOutStartMinutes(), 5));
                    dailyDto.setOnTimeCheckOutEndMinutes(defaultInt(eff.shift().getOnTimeCheckOutEndMinutes(), 15));
                    
                    dailyDto.setLeave(eff.isLeave());
                    dailyDto.setWfh(eff.isWfh());
                    dailyDto.setSwap(eff.isSwap());
                    dailyDto.setOriginalShiftName(eff.originalShift() != null ? eff.originalShift().getName() : null);
                    dailyDto.setOriginalStartTime(eff.originalShift() != null ? eff.originalShift().getStartTime() : null);
                    dailyDto.setOriginalEndTime(eff.originalShift() != null ? eff.originalShift().getEndTime() : null);
                    dailyDto.setChangeDescription(eff.changeDescription());
                    
                    if (eff.isLeave()) {
                        dailyDto.setStatusLabel("[Nghỉ phép]");
                    } else if (eff.isWfh()) {
                        dailyDto.setStatusLabel("[WFH]");
                    } else if (eff.isSwap()) {
                        dailyDto.setStatusLabel("[Đổi ca]");
                    }
                    
                    dailyShifts.add(dailyDto);
                }
            }
            
            dto.setShifts(dailyShifts);
            return dto;
        }).collect(Collectors.toList());
        
        return new PageImpl<>(dtos, pageable, employeePage.getTotalElements());
    }
    private void validateAndApplyScheduleAdjustment(Employee employee, LocalDate workDate, Shift newShift, String reason) {
        Optional<PayPeriod> periodOpt = payPeriodRepository.findPeriodActiveAt(workDate);
        if (periodOpt.isPresent() && periodOpt.get().isLocked()) {
            throw new RuntimeException("Kỳ công chứa ngày " + workDate + " đã bị khóa. Không thể điều chỉnh lịch.");
        }

        UUID adjusterId = SecurityContextUtils.getCurrentUserId();
        AccountUser adjuster = adjusterId != null ? accountUserRepository.findById(adjusterId).orElse(null) : null;

        LocalDate today = LocalDate.now();

        boolean isToday = workDate.equals(today);
        boolean hasEvents = false;

        if (isToday || workDate.isBefore(today)) {
            LocalDateTime start = workDate.atStartOfDay();
            LocalDateTime end = workDate.atTime(LocalTime.MAX);
            List<AttendanceEvent> events = attendanceEventRepository
                    .findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(employee.getId(), start, end);
            hasEvents = !events.isEmpty();
        }

        boolean requireAdjustment = (isToday && hasEvents) || workDate.isBefore(today);

        if (requireAdjustment) {
            if (reason == null || reason.trim().isEmpty()) {
                throw new RuntimeException("Cần lý do điều chỉnh lịch cho ngày " + workDate + " vì đã phát sinh sự kiện chấm công hoặc là ngày trong quá khứ.");
            }
            if (adjuster == null) {
                throw new RuntimeException("Không tìm thấy thông tin người điều chỉnh lịch.");
            }

            Shift originalShift = employeeShiftRepository.findByEmployeeIdAndWorkDate(employee.getId(), workDate).stream()
                    .findFirst()
                    .map(EmployeeShift::getShift)
                    .orElse(null);

            ScheduleAdjustment sa = new ScheduleAdjustment();
            sa.setEmployee(employee);
            sa.setWorkDate(workDate);
            sa.setOriginalShift(originalShift);
            sa.setAdjustedShift(newShift);
            sa.setAdjustedBy(adjuster);
            sa.setAdjustedAt(LocalDateTime.now());
            sa.setReason(reason);
            sa.setStatus(ApprovalStatus.APPROVED);
            scheduleAdjustmentRepository.save(sa);
        }
    }

    @Transactional
    public void assignSingleShift(EmployeeShiftDto dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        Shift shift = shiftRepository.findById(dto.getShiftId())
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        validateAndApplyScheduleAdjustment(employee, dto.getWorkDate(), shift, dto.getReason());

        validateNoOverlap(dto.getEmployeeId(), dto.getWorkDate(), shift, null);
        
        EmployeeShift es = new EmployeeShift();
        es.setEmployee(employee);
        es.setShift(shift);
        es.setWorkDate(dto.getWorkDate());
        employeeShiftRepository.save(es);

        // Notify employee
        if (employee.getUserInfo() != null) {
            String title = "Cập nhật lịch làm việc";
            String content = String.format("Lịch làm việc ngày %s của bạn đã được cập nhật thành ca: %s.", 
                    dto.getWorkDate(), shift.getName());
            notificationService.createNotification(
                    employee.getUserInfo().getId(),
                    title,
                    content,
                    NotificationType.WORK_SCHEDULE,
                    employee.getId()
            );
        }
    }

    @Transactional
    public void bulkAssignShifts(BulkShiftUpdateDto dto) {
        List<UUID> targetEmployeeIds = dto.getEmployeeIds();
        
        if (dto.isSelectAll()) {
            targetEmployeeIds = employeeRepository.findAll().stream()
                    .map(Employee::getId)
                    .collect(Collectors.toList());
        }
        
        if (targetEmployeeIds == null || targetEmployeeIds.isEmpty()) return;
        
        Shift shift = shiftRepository.findById(dto.getShiftId())
                .orElseThrow(() -> new RuntimeException("Shift not found"));
                
        List<Employee> employees = employeeRepository.findAllById(targetEmployeeIds);

        LocalDate currentDate = dto.getStartDate();
        while (!currentDate.isAfter(dto.getEndDate())) {
            for (Employee emp : employees) {
                validateAndApplyScheduleAdjustment(emp, currentDate, shift, dto.getReason());
                validateNoOverlap(emp.getId(), currentDate, shift, null);
            }
            currentDate = currentDate.plusDays(1);
        }

        List<EmployeeShift> newShifts = new ArrayList<>();
        currentDate = dto.getStartDate();
        while (!currentDate.isAfter(dto.getEndDate())) {
            for (Employee emp : employees) {
                EmployeeShift es = new EmployeeShift();
                es.setEmployee(emp);
                es.setShift(shift);
                es.setWorkDate(currentDate);
                newShifts.add(es);
            }
            currentDate = currentDate.plusDays(1);
        }
        
        employeeShiftRepository.saveAll(newShifts);

        // Notify employees
        for (Employee emp : employees) {
            if (emp.getUserInfo() != null) {
                String title = "Cập nhật lịch làm việc hàng loạt";
                String content = String.format("Lịch làm việc của bạn từ ngày %s đến ngày %s đã được cập nhật thành ca: %s.",
                        dto.getStartDate(), dto.getEndDate(), shift.getName());
                notificationService.createNotification(
                        emp.getUserInfo().getId(),
                        title,
                        content,
                        NotificationType.WORK_SCHEDULE,
                        emp.getId()
                );
            }
        }
    }

    @Transactional
    public void copyWeeklySchedule(CopyWeekDto dto) {
        List<EmployeeShift> prevShifts = employeeShiftRepository.findAll().stream()
                .filter(es -> !es.getWorkDate().isBefore(dto.getFromWeekStartDate()) && !es.getWorkDate().isAfter(dto.getFromWeekEndDate()))
                .collect(Collectors.toList());
                
        List<UUID> employeeIdsToCopy = prevShifts.stream().map(es -> es.getEmployee().getId()).distinct().collect(Collectors.toList());
        if (employeeIdsToCopy.isEmpty()) return;

        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(dto.getFromWeekStartDate(), dto.getToWeekStartDate());

        for (EmployeeShift prevEs : prevShifts) {
            LocalDate destDate = prevEs.getWorkDate().plusDays(daysDiff);
            validateAndApplyScheduleAdjustment(prevEs.getEmployee(), destDate, prevEs.getShift(), dto.getReason());
        }
        
        employeeShiftRepository.deleteByEmployeeIdInAndWorkDateBetween(employeeIdsToCopy, dto.getToWeekStartDate(), dto.getToWeekEndDate());
        
        List<EmployeeShift> newShifts = new ArrayList<>();
        for (EmployeeShift prevEs : prevShifts) {
            EmployeeShift newEs = new EmployeeShift();
            newEs.setEmployee(prevEs.getEmployee());
            newEs.setShift(prevEs.getShift());
            newEs.setWorkDate(prevEs.getWorkDate().plusDays(daysDiff));
            newShifts.add(newEs);
        }
        
        employeeShiftRepository.saveAll(newShifts);

        // Notify affected employees
        List<Employee> affectedEmployees = prevShifts.stream()
                .map(EmployeeShift::getEmployee)
                .distinct()
                .collect(Collectors.toList());
        for (Employee emp : affectedEmployees) {
            if (emp.getUserInfo() != null) {
                String title = "Lịch làm việc mới được sao chép";
                String content = String.format("Lịch làm việc của bạn cho tuần từ ngày %s đã được sao chép từ tuần trước.",
                        dto.getToWeekStartDate());
                notificationService.createNotification(
                        emp.getUserInfo().getId(),
                        title,
                        content,
                        NotificationType.WORK_SCHEDULE,
                        emp.getId()
                );
            }
        }
    }

    @Transactional
    public void deleteScheduleAssignment(UUID employeeShiftId, String reason) {
        EmployeeShift assignment = employeeShiftRepository.findByIdWithShift(employeeShiftId)
                .orElseThrow(() -> new RuntimeException("Schedule assignment not found"));

        LocalDateTime start = assignment.getWorkDate().atStartOfDay();
        LocalDateTime end = assignment.getWorkDate().atTime(LocalTime.MAX);
        List<AttendanceEvent> events = attendanceEventRepository
                .findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(assignment.getEmployee().getId(), start, end);
        boolean hasEvents = events.stream()
                .anyMatch(event -> event.getEmployeeShift() != null && event.getEmployeeShift().getId().equals(employeeShiftId));
        if (hasEvents) {
            throw new RuntimeException("Không thể gỡ ca vì đã phát sinh sự kiện chấm công (check-in/check-out) cho ca này.");
        }

        validateAndApplyScheduleAdjustment(
                assignment.getEmployee(),
                assignment.getWorkDate(),
                null,
                reason
        );

        employeeShiftRepository.delete(assignment);
    }

    private void validateNoOverlap(UUID employeeId, LocalDate workDate, Shift newShift, UUID excludeEmployeeShiftId) {
        if (newShift.getType() == ShiftType.OFF || newShift.getStartTime() == null || newShift.getEndTime() == null) {
            return;
        }

        List<EmployeeShift> existingAssignments = employeeShiftRepository.findByEmployeeIdAndWorkDate(employeeId, workDate);
        for (EmployeeShift existing : existingAssignments) {
            if (excludeEmployeeShiftId != null && excludeEmployeeShiftId.equals(existing.getId())) {
                continue;
            }
            Shift existingShift = existing.getShift();
            if (existingShift == null || existingShift.getType() == ShiftType.OFF
                    || existingShift.getStartTime() == null || existingShift.getEndTime() == null) {
                continue;
            }
            boolean overlaps = newShift.getStartTime().isBefore(existingShift.getEndTime())
                    && newShift.getEndTime().isAfter(existingShift.getStartTime());
            if (overlaps) {
                throw new RuntimeException(String.format(
                        "Ca %s bị trùng thời gian với ca %s trong ngày %s.",
                        newShift.getName(),
                        existingShift.getName(),
                        workDate
                ));
            }
        }
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    @Transactional(readOnly = true)
    public List<EmployeeWeeklyScheduleDto.DailyShiftDto> getMyDailyShifts(LocalDate startDate, LocalDate endDate) {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        Employee employee = employeeRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhân viên."));
        
        List<EmployeeWeeklyScheduleDto.DailyShiftDto> dailyShifts = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<AttendanceCalculator.EffectiveShift> effShifts = attendanceCalculator.resolveEffectiveShifts(employee.getId(), date);
            for (AttendanceCalculator.EffectiveShift eff : effShifts) {
                EmployeeWeeklyScheduleDto.DailyShiftDto dailyDto = new EmployeeWeeklyScheduleDto.DailyShiftDto();
                dailyDto.setEmployeeShiftId(eff.assignment() != null ? eff.assignment().getId() : null);
                dailyDto.setShiftId(eff.shift().getId());
                dailyDto.setShiftName(eff.shift().getName());
                dailyDto.setColorCode(eff.shift().getColorCode());
                dailyDto.setWorkDate(date);
                dailyDto.setStartTime(eff.shift().getStartTime());
                dailyDto.setEndTime(eff.shift().getEndTime());
                dailyDto.setShiftType(eff.shift().getType());
                dailyDto.setEarlyCheckInMinutes(defaultInt(eff.shift().getEarlyCheckInMinutes(), 30));
                dailyDto.setLateCheckOutMinutes(defaultInt(eff.shift().getLateCheckOutMinutes(), 60));
                dailyDto.setOnTimeCheckInStartMinutes(defaultInt(eff.shift().getOnTimeCheckInStartMinutes(), 15));
                dailyDto.setOnTimeCheckInEndMinutes(defaultInt(eff.shift().getOnTimeCheckInEndMinutes(), 5));
                dailyDto.setOnTimeCheckOutStartMinutes(defaultInt(eff.shift().getOnTimeCheckOutStartMinutes(), 5));
                dailyDto.setOnTimeCheckOutEndMinutes(defaultInt(eff.shift().getOnTimeCheckOutEndMinutes(), 15));
                
                dailyDto.setLeave(eff.isLeave());
                dailyDto.setWfh(eff.isWfh());
                dailyDto.setSwap(eff.isSwap());
                dailyDto.setOriginalShiftName(eff.originalShift() != null ? eff.originalShift().getName() : null);
                dailyDto.setOriginalStartTime(eff.originalShift() != null ? eff.originalShift().getStartTime() : null);
                dailyDto.setOriginalEndTime(eff.originalShift() != null ? eff.originalShift().getEndTime() : null);
                dailyDto.setChangeDescription(eff.changeDescription());
                
                if (eff.isLeave()) {
                    dailyDto.setStatusLabel("[Nghỉ phép]");
                } else if (eff.isWfh()) {
                    dailyDto.setStatusLabel("[WFH]");
                } else if (eff.isSwap()) {
                    dailyDto.setStatusLabel("[Đổi ca]");
                }
                
                dailyShifts.add(dailyDto);
            }
        }
        return dailyShifts;
    }
}
