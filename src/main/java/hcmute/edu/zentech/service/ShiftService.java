package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.shift.*;
import hcmute.edu.zentech.mapper.ShiftMapper;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.EmployeeShift;
import hcmute.edu.zentech.model.Shift;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.repository.EmployeeShiftRepository;
import hcmute.edu.zentech.repository.ShiftRepository;
import hcmute.edu.zentech.repository.projection.EmployeeWeeklyScheduleProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShiftService {
    private final ShiftRepository shiftRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftMapper shiftMapper;

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
                    shift.setStartTime(dto.getStartTime());
                    shift.setEndTime(dto.getEndTime());
                    updatedShifts.add(shiftRepository.save(shift));
                }
            }
        }
        return shiftMapper.toDtoList(updatedShifts);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeWeeklyScheduleDto> getWeeklySchedules(LocalDate startDate, LocalDate endDate, String keyword, Pageable pageable) {
        Page<Employee> employeePage = employeeRepository.searchEmployees(keyword, null, null, pageable);
        
        if (employeePage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, employeePage.getTotalElements());
        }
        
        List<UUID> employeeIds = employeePage.getContent().stream().map(Employee::getId).collect(Collectors.toList());
        
        List<EmployeeWeeklyScheduleProjection> projections = employeeShiftRepository.findProjectionsByEmployeeIdsAndDateRange(employeeIds, startDate, endDate);
        
        Map<UUID, List<EmployeeWeeklyScheduleProjection>> schedulesByEmployee = projections.stream()
                .collect(Collectors.groupingBy(EmployeeWeeklyScheduleProjection::getEmployeeId));
                
        List<EmployeeWeeklyScheduleDto> dtos = employeePage.getContent().stream().map(emp -> {
            EmployeeWeeklyScheduleDto dto = new EmployeeWeeklyScheduleDto();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getFullName());
            
            List<EmployeeWeeklyScheduleDto.DailyShiftDto> dailyShifts = new ArrayList<>();
            List<EmployeeWeeklyScheduleProjection> empSchedules = schedulesByEmployee.getOrDefault(emp.getId(), Collections.emptyList());
            
            for (EmployeeWeeklyScheduleProjection p : empSchedules) {
                EmployeeWeeklyScheduleDto.DailyShiftDto dailyDto = new EmployeeWeeklyScheduleDto.DailyShiftDto();
                dailyDto.setShiftId(p.getShiftId());
                dailyDto.setShiftName(p.getShiftName());
                dailyDto.setColorCode(p.getColorCode());
                dailyDto.setWorkDate(p.getWorkDate());
                dailyDto.setStartTime(p.getStartTime());
                dailyDto.setEndTime(p.getEndTime());
                dailyDto.setShiftType(p.getShiftType());
                dailyShifts.add(dailyDto);
            }
            
            dto.setShifts(dailyShifts);
            return dto;
        }).collect(Collectors.toList());
        
        return new PageImpl<>(dtos, pageable, employeePage.getTotalElements());
    }

    @Transactional
    public void assignSingleShift(EmployeeShiftDto dto) {
        employeeShiftRepository.deleteByEmployeeIdAndWorkDate(dto.getEmployeeId(), dto.getWorkDate());
        
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        Shift shift = shiftRepository.findById(dto.getShiftId())
                .orElseThrow(() -> new RuntimeException("Shift not found"));
                
        EmployeeShift es = new EmployeeShift();
        es.setEmployee(employee);
        es.setShift(shift);
        es.setWorkDate(dto.getWorkDate());
        employeeShiftRepository.save(es);
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
        
        // Remove old shifts in range for these employees
        employeeShiftRepository.deleteByEmployeeIdInAndWorkDateBetween(targetEmployeeIds, dto.getStartDate(), dto.getEndDate());
        
        Shift shift = shiftRepository.findById(dto.getShiftId())
                .orElseThrow(() -> new RuntimeException("Shift not found"));
                
        List<Employee> employees = employeeRepository.findAllById(targetEmployeeIds);
        List<EmployeeShift> newShifts = new ArrayList<>();
        
        LocalDate currentDate = dto.getStartDate();
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
    }

    @Transactional
    public void copyWeeklySchedule(CopyWeekDto dto) {
        List<EmployeeShift> prevShifts = employeeShiftRepository.findAll().stream()
                .filter(es -> !es.getWorkDate().isBefore(dto.getFromWeekStartDate()) && !es.getWorkDate().isAfter(dto.getFromWeekEndDate()))
                .collect(Collectors.toList());
                
        List<UUID> employeeIdsToCopy = prevShifts.stream().map(es -> es.getEmployee().getId()).distinct().collect(Collectors.toList());
        if (employeeIdsToCopy.isEmpty()) return;
        
        employeeShiftRepository.deleteByEmployeeIdInAndWorkDateBetween(employeeIdsToCopy, dto.getToWeekStartDate(), dto.getToWeekEndDate());
        
        List<EmployeeShift> newShifts = new ArrayList<>();
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(dto.getFromWeekStartDate(), dto.getToWeekStartDate());
        
        for (EmployeeShift prevEs : prevShifts) {
            EmployeeShift newEs = new EmployeeShift();
            newEs.setEmployee(prevEs.getEmployee());
            newEs.setShift(prevEs.getShift());
            newEs.setWorkDate(prevEs.getWorkDate().plusDays(daysDiff));
            newShifts.add(newEs);
        }
        
        employeeShiftRepository.saveAll(newShifts);
    }
}
