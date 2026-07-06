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
    private final ShiftOverlapService shiftOverlapService;
    private final ScheduleMutationPolicy scheduleMutationPolicy;


    private void createAdjustmentSnapshot(Employee employee, LocalDate workDate, List<Shift> desiredShifts, String reason) {
        scheduleMutationPolicy.assertPayPeriodUnlocked(workDate);
        String normalizedReason = requireReason(reason, workDate);
        AccountUser adjuster = resolveAdjuster();

        List<Shift> originalShifts = resolveCurrentEffectiveShifts(employee.getId(), workDate);
        List<ScheduleAdjustment> existingApproved = scheduleMutationPolicy.findApprovedScheduleAdjustments(employee.getId(), workDate);
        existingApproved.forEach(adjustment -> adjustment.setStatus(ApprovalStatus.SUPERSEDED));
        scheduleAdjustmentRepository.saveAll(existingApproved);

        List<ScheduleAdjustment> snapshot = new ArrayList<>();
        if (desiredShifts.isEmpty()) {
            snapshot.add(buildScheduleAdjustment(
                    employee,
                    workDate,
                    originalShifts.isEmpty() ? null : originalShifts.get(0),
                    null,
                    adjuster,
                    normalizedReason
            ));
        } else {
            for (Shift desiredShift : desiredShifts) {
                snapshot.add(buildScheduleAdjustment(
                        employee,
                        workDate,
                        findMatchingOriginalShift(originalShifts, desiredShift),
                        desiredShift,
                        adjuster,
                        normalizedReason
                ));
            }
        }
        scheduleAdjustmentRepository.saveAll(snapshot);
    }

    private ScheduleAdjustment buildScheduleAdjustment(Employee employee,
                                                       LocalDate workDate,
                                                       Shift originalShift,
                                                       Shift adjustedShift,
                                                       AccountUser adjuster,
                                                       String reason) {
        ScheduleAdjustment adjustment = new ScheduleAdjustment();
        adjustment.setEmployee(employee);
        adjustment.setWorkDate(workDate);
        adjustment.setOriginalShift(originalShift);
        adjustment.setAdjustedShift(adjustedShift);
        adjustment.setAdjustedBy(adjuster);
        adjustment.setAdjustedAt(LocalDateTime.now());
        adjustment.setReason(reason);
        adjustment.setStatus(ApprovalStatus.APPROVED);
        return adjustment;
    }

    private String requireReason(String reason, LocalDate workDate) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Cần lý do điều chỉnh lịch cho ngày " + workDate + ".");
        }
        return reason.trim();
    }

    private AccountUser resolveAdjuster() {
        UUID adjusterId = SecurityContextUtils.getCurrentUserId();
        AccountUser adjuster = adjusterId != null ? accountUserRepository.findById(adjusterId).orElse(null) : null;
        if (adjuster == null) {
            throw new RuntimeException("Không tìm thấy thông tin người điều chỉnh lịch.");
        }
        return adjuster;
    }

    private Shift findMatchingOriginalShift(List<Shift> originalShifts, Shift desiredShift) {
        if (desiredShift == null || desiredShift.getId() == null) {
            return null;
        }
        return originalShifts.stream()
                .filter(original -> original != null && desiredShift.getId().equals(original.getId()))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public ShiftDto createShift(ShiftCreateDto dto) {
        Shift shift = shiftMapper.toEntity(dto);
        if (shift.getType() == ShiftType.OFF) {
            clearOffShiftFields(shift);
        }
        shift = shiftRepository.save(shift);
        return shiftMapper.toDto(shift);
    }

    private void clearOffShiftFields(Shift shift) {
        shift.setStartTime(null);
        shift.setEndTime(null);
        shift.setEarlyCheckInMinutes(null);
        shift.setLateCheckOutMinutes(null);
        shift.setOnTimeCheckInStartMinutes(null);
        shift.setOnTimeCheckInEndMinutes(null);
        shift.setOnTimeCheckOutStartMinutes(null);
        shift.setOnTimeCheckOutEndMinutes(null);
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
                    if (shift.getType() == ShiftType.OFF) {
                        clearOffShiftFields(shift);
                    }
                    updatedShifts.add(shiftRepository.save(shift));
                }
            }
        }
        return shiftMapper.toDtoList(updatedShifts);
    }

    public Page<EmployeeWeeklyScheduleDto> getWeeklySchedules(LocalDate startDate, LocalDate endDate, String keyword, UUID employeeId, Pageable pageable) {
        Page<Employee> employeePage;
        if (employeeId != null) {
            Optional<Employee> empOpt = employeeRepository.findById(employeeId);
            List<Employee> list = empOpt.map(List::of).orElse(Collections.emptyList());
            employeePage = new PageImpl<>(list, pageable, list.size());
        } else {
            employeePage = employeeRepository.searchEmployees(keyword, null, null, pageable);
        }
        
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

        scheduleMutationPolicy.assertPayPeriodUnlocked(dto.getWorkDate());
        scheduleMutationPolicy.assertTodayShiftStillUseful(dto.getWorkDate(), shift);

        if (scheduleMutationPolicy.requiresAdjustment(employee, dto.getWorkDate())) {
            List<Shift> desiredShifts = new ArrayList<>(resolveCurrentEffectiveShifts(employee.getId(), dto.getWorkDate()));
            desiredShifts.add(shift);
            validateNoInternalOverlap(toScheduleCandidates(employee, dto.getWorkDate(), desiredShifts));
            createAdjustmentSnapshot(employee, dto.getWorkDate(), desiredShifts, dto.getReason());
        } else {
            validateNoOverlap(dto.getEmployeeId(), dto.getWorkDate(), shift, null);

            EmployeeShift es = new EmployeeShift();
            es.setEmployee(employee);
            es.setShift(shift);
            es.setWorkDate(dto.getWorkDate());
            employeeShiftRepository.save(es);
        }

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

        List<EmployeeShift> newShifts = new ArrayList<>();
        List<ScheduleAdjustmentPlan> adjustmentPlans = new ArrayList<>();
        LocalDate currentDate = dto.getStartDate();
        while (!currentDate.isAfter(dto.getEndDate())) {
            for (Employee emp : employees) {
                scheduleMutationPolicy.assertPayPeriodUnlocked(currentDate);
                scheduleMutationPolicy.assertTodayShiftStillUseful(currentDate, shift);

                if (scheduleMutationPolicy.requiresAdjustment(emp, currentDate)) {
                    List<Shift> desiredShifts = new ArrayList<>(resolveCurrentEffectiveShifts(emp.getId(), currentDate));
                    desiredShifts.add(shift);
                    validateNoInternalOverlap(toScheduleCandidates(emp, currentDate, desiredShifts));
                    adjustmentPlans.add(new ScheduleAdjustmentPlan(emp, currentDate, desiredShifts));
                } else {
                    validateNoOverlap(emp.getId(), currentDate, shift, null);
                    EmployeeShift es = new EmployeeShift();
                    es.setEmployee(emp);
                    es.setShift(shift);
                    es.setWorkDate(currentDate);
                    newShifts.add(es);
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        for (ScheduleAdjustmentPlan plan : adjustmentPlans) {
            createAdjustmentSnapshot(plan.employee(), plan.workDate(), plan.desiredShifts(), dto.getReason());
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

        List<ScheduleCandidate> copyCandidates = prevShifts.stream()
                .map(prevEs -> new ScheduleCandidate(
                        prevEs.getEmployee(),
                        prevEs.getWorkDate().plusDays(daysDiff),
                        prevEs.getShift()
                ))
                .toList();
        validateNoInternalOverlap(copyCandidates);

        Map<UUID, Employee> employeesById = prevShifts.stream()
                .map(EmployeeShift::getEmployee)
                .collect(Collectors.toMap(Employee::getId, employee -> employee, (first, second) -> first, LinkedHashMap::new));
        Map<ScheduleGroupKey, ScheduleAdjustmentPlan> copyPlansByDay = new LinkedHashMap<>();
        for (Employee employee : employeesById.values()) {
            for (LocalDate date = dto.getToWeekStartDate(); !date.isAfter(dto.getToWeekEndDate()); date = date.plusDays(1)) {
                copyPlansByDay.put(
                        new ScheduleGroupKey(employee.getId(), date),
                        new ScheduleAdjustmentPlan(employee, date, new ArrayList<>())
                );
            }
        }
        for (ScheduleCandidate candidate : copyCandidates) {
            ScheduleGroupKey key = new ScheduleGroupKey(candidate.employee().getId(), candidate.workDate());
            copyPlansByDay.computeIfAbsent(
                    key,
                    ignored -> new ScheduleAdjustmentPlan(candidate.employee(), candidate.workDate(), new ArrayList<>())
            ).desiredShifts().add(candidate.shift());
        }

        List<ScheduleAdjustmentPlan> adjustmentPlans = new ArrayList<>();
        List<ScheduleAdjustmentPlan> directPlans = new ArrayList<>();
        for (ScheduleAdjustmentPlan plan : copyPlansByDay.values()) {
            scheduleMutationPolicy.assertPayPeriodUnlocked(plan.workDate());
            for (Shift copiedShift : plan.desiredShifts()) {
                scheduleMutationPolicy.assertTodayShiftStillUseful(plan.workDate(), copiedShift);
            }
            if (scheduleMutationPolicy.requiresAdjustment(plan.employee(), plan.workDate())) {
                adjustmentPlans.add(plan);
            } else {
                directPlans.add(plan);
            }
        }

        for (ScheduleAdjustmentPlan plan : adjustmentPlans) {
            createAdjustmentSnapshot(plan.employee(), plan.workDate(), plan.desiredShifts(), dto.getReason());
        }

        List<EmployeeShift> newShifts = new ArrayList<>();
        for (ScheduleAdjustmentPlan plan : directPlans) {
            employeeShiftRepository.deleteByEmployeeIdAndWorkDate(plan.employee().getId(), plan.workDate());
            for (Shift copiedShift : plan.desiredShifts()) {
                EmployeeShift newEs = new EmployeeShift();
                newEs.setEmployee(plan.employee());
                newEs.setShift(copiedShift);
                newEs.setWorkDate(plan.workDate());
                newShifts.add(newEs);
            }
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

        if (scheduleMutationPolicy != null) {
            scheduleMutationPolicy.assertPayPeriodUnlocked(assignment.getWorkDate());
            if (scheduleMutationPolicy.shouldDeleteByAdjustment(assignment)) {
                List<Shift> desiredShifts = new ArrayList<>(resolveCurrentEffectiveShifts(
                        assignment.getEmployee().getId(),
                        assignment.getWorkDate()
                ));
                removeOneShift(desiredShifts, assignment.getShift());
                validateNoInternalOverlap(toScheduleCandidates(assignment.getEmployee(), assignment.getWorkDate(), desiredShifts));
                createAdjustmentSnapshot(assignment.getEmployee(), assignment.getWorkDate(), desiredShifts, reason);
            } else {
                employeeShiftRepository.delete(assignment);
            }
            return;
        }

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
        if (!shiftOverlapService.hasCaptureWindow(newShift)) {
            return;
        }

        ShiftOverlapService.CaptureWindow newWindow = shiftOverlapService.captureWindow(workDate, newShift);
        List<EmployeeShift> existingAssignments = employeeShiftRepository.findByEmployeeIdAndWorkDate(employeeId, workDate);
        for (EmployeeShift existing : existingAssignments) {
            if (excludeEmployeeShiftId != null && excludeEmployeeShiftId.equals(existing.getId())) {
                continue;
            }
            Shift existingShift = existing.getShift();
            if (!shiftOverlapService.hasCaptureWindow(existingShift)) {
                continue;
            }
            ShiftOverlapService.CaptureWindow existingWindow = shiftOverlapService.captureWindow(workDate, existingShift);
            if (shiftOverlapService.overlapsInclusive(newWindow, existingWindow)) {
                throw new RuntimeException(String.format(
                        "Ca %s (%s) bị trùng vùng chấm công với ca %s (%s) trong ngày %s.",
                        newShift.getName(),
                        shiftOverlapService.format(newWindow),
                        existingShift.getName(),
                        shiftOverlapService.format(existingWindow),
                        workDate
                ));
            }
        }
    }

    private List<Shift> resolveCurrentEffectiveShifts(UUID employeeId, LocalDate workDate) {
        List<ScheduleAdjustment> approvedAdjustments = scheduleMutationPolicy.findApprovedScheduleAdjustments(employeeId, workDate);
        if (!approvedAdjustments.isEmpty()) {
            return approvedAdjustments.stream()
                    .map(ScheduleAdjustment::getAdjustedShift)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Shift::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        return employeeShiftRepository.findByEmployeeIdAndWorkDate(employeeId, workDate).stream()
                .map(EmployeeShift::getShift)
                .filter(Objects::nonNull)
                .filter(shift -> shift.getType() != ShiftType.OFF)
                .sorted(Comparator.comparing(Shift::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<ScheduleCandidate> toScheduleCandidates(Employee employee, LocalDate workDate, List<Shift> shifts) {
        return shifts.stream()
                .map(shift -> new ScheduleCandidate(employee, workDate, shift))
                .toList();
    }

    private void removeOneShift(List<Shift> shifts, Shift shiftToRemove) {
        if (shiftToRemove == null) {
            return;
        }
        for (Iterator<Shift> iterator = shifts.iterator(); iterator.hasNext(); ) {
            Shift shift = iterator.next();
            if (shift != null && Objects.equals(shift.getId(), shiftToRemove.getId())) {
                iterator.remove();
                return;
            }
        }
    }

    private void validateNoInternalOverlap(List<ScheduleCandidate> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            ScheduleCandidate first = candidates.get(i);
            if (!shiftOverlapService.hasCaptureWindow(first.shift())) {
                continue;
            }
            ShiftOverlapService.CaptureWindow firstWindow = shiftOverlapService.captureWindow(first.workDate(), first.shift());
            for (int j = i + 1; j < candidates.size(); j++) {
                ScheduleCandidate second = candidates.get(j);
                if (!Objects.equals(first.employee().getId(), second.employee().getId())
                        || !Objects.equals(first.workDate(), second.workDate())
                        || !shiftOverlapService.hasCaptureWindow(second.shift())) {
                    continue;
                }
                ShiftOverlapService.CaptureWindow secondWindow = shiftOverlapService.captureWindow(second.workDate(), second.shift());
                if (shiftOverlapService.overlapsInclusive(firstWindow, secondWindow)) {
                    throw new RuntimeException(String.format(
                            "Không thể sao chép tuần vì nhân viên %s ngày %s có ca %s (%s) trùng vùng chấm công với ca %s (%s).",
                            first.employee().getFullName(),
                            first.workDate(),
                            first.shift().getName(),
                            shiftOverlapService.format(firstWindow),
                            second.shift().getName(),
                            shiftOverlapService.format(secondWindow)
                    ));
                }
            }
        }
    }

    private void validateNoAdjustmentOverlap(List<ScheduleCandidate> candidates) {
        for (ScheduleCandidate candidate : candidates) {
            if (!shiftOverlapService.hasCaptureWindow(candidate.shift())) {
                continue;
            }
            List<ScheduleAdjustment> adjustments = scheduleAdjustmentRepository.findByEmployeeIdAndWorkDateBetween(
                    candidate.employee().getId(),
                    candidate.workDate(),
                    candidate.workDate()
            );
            ShiftOverlapService.CaptureWindow candidateWindow = shiftOverlapService.captureWindow(candidate.workDate(), candidate.shift());
            for (ScheduleAdjustment adjustment : adjustments) {
                if (adjustment.getStatus() != ApprovalStatus.APPROVED
                        || !shiftOverlapService.hasCaptureWindow(adjustment.getAdjustedShift())) {
                    continue;
                }
                Shift adjustedShift = adjustment.getAdjustedShift();
                ShiftOverlapService.CaptureWindow adjustmentWindow = shiftOverlapService.captureWindow(adjustment.getWorkDate(), adjustedShift);
                if (shiftOverlapService.overlapsInclusive(candidateWindow, adjustmentWindow)) {
                    throw new RuntimeException(String.format(
                            "Không thể sao chép tuần vì nhân viên %s ngày %s có ca %s (%s) trùng vùng chấm công với ca điều chỉnh %s (%s).",
                            candidate.employee().getFullName(),
                            candidate.workDate(),
                            candidate.shift().getName(),
                            shiftOverlapService.format(candidateWindow),
                            adjustedShift.getName(),
                            shiftOverlapService.format(adjustmentWindow)
                    ));
                }
            }
        }
    }

    private record ScheduleCandidate(Employee employee, LocalDate workDate, Shift shift) {
    }

    private record ScheduleAdjustmentPlan(Employee employee, LocalDate workDate, List<Shift> desiredShifts) {
    }

    private record ScheduleGroupKey(UUID employeeId, LocalDate workDate) {
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
