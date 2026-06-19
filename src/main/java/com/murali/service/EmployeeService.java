package com.murali.service;

import com.murali.entity.*;
import com.murali.exception.HodConflictException;
import com.murali.exception.HodDeleteConflictException;
import com.murali.exception.ManagerDeleteConflictException;
import com.murali.exception.ManagerPromotionConflictException;
import com.murali.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final UserService userService;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final AuditLogService auditLoggingService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    @Transactional(readOnly = true)
    public List<Employee> findAvailableReportingManagers(Long departmentId, Role selectedRole) {
        if (selectedRole == null) return Collections.emptyList();

        String roleName = selectedRole.getName();

        if ("ROLE_SUPER_ADMIN".equals(roleName)) {
            return Collections.emptyList();
        }

        if ("ROLE_HR_ADMIN".equals(roleName)) {
            return employeeRepository.findByActiveTrue().stream().filter(e -> e.getUser() != null && e.getUser().getRole().getHierarchyWeight() > selectedRole.getHierarchyWeight()).collect(Collectors.toList());
        }

        if ("ROLE_DEPT_HEAD".equals(roleName)) {
            return employeeRepository.findByActiveTrue().stream().filter(e -> e.getUser() != null && e.getUser().getRole().getHierarchyWeight() > selectedRole.getHierarchyWeight()).collect(Collectors.toList());
        }

        if (departmentId == null) return Collections.emptyList();

        if ("ROLE_MANAGER".equals(roleName)) {
            Department dept = departmentRepository.findById(departmentId).orElse(null);
            return (dept != null && dept.getHod() != null && dept.getHod().getUser().getActive()) ? List.of(dept.getHod()) : Collections.emptyList();
        }

        if ("ROLE_EMPLOYEE".equals(roleName)) {
            // Show Managers in the same department OR the HOD of the same department
            return employeeRepository.findByActiveTrue().stream().filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(departmentId)).filter(e -> {
                boolean isManager = "ROLE_MANAGER".equals(e.getUser().getRole().getName());
                boolean isHod = departmentRepository.existsByHodId(e.getId());
                return isManager || isHod;
            }).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Transactional
    public void createOrUpdateEmployeeWithUser(Employee currentEmployee, User currentUser, boolean isExistingUserLinked, Set<LeaveType> selectedLeaves) {

        validateSuperAdminRule(currentUser);
        validateRoleHierarchy(currentEmployee, currentUser);

        Employee savedEmployee = executeStandardSave(currentEmployee, currentUser, isExistingUserLinked, selectedLeaves);

        syncDepartmentHodStatus(savedEmployee, currentUser.getRole().getName());
    }

    private void syncDepartmentHodStatus(Employee savedEmployee, String newRole) {
        if ("ROLE_DEPT_HEAD".equals(newRole)) {
            Department dept = departmentRepository.findById(savedEmployee.getDepartment().getId()).orElseThrow();

            if (dept.getHod() == null || !dept.getHod().getId().equals(savedEmployee.getId())) {
                dept.setHod(savedEmployee);
                departmentRepository.save(dept);

                if (savedEmployee.getManager() != null) {
                    savedEmployee.setManager(null);
                    employeeRepository.save(savedEmployee);
                }
            }
        } else {
            if (savedEmployee.getId() != null) {
                departmentRepository.findByHodId(savedEmployee.getId()).ifPresent(dept -> {
                    dept.setHod(null);
                    departmentRepository.save(dept);
                });
            }
        }
    }

    private void validateSuperAdminRule(User currentUser) {
        if ("ROLE_SUPER_ADMIN".equals(currentUser.getRole().getName()) && currentUser.getId() == null) {
            long superAdminCount = userRepository.countByRoleName("ROLE_SUPER_ADMIN");
            if (superAdminCount > 0) {
                throw new IllegalStateException("A Super Admin already exists in the system.");
            }
        }
    }

    private void validateRoleHierarchy(Employee employee, User user) {
        String newRole = user.getRole().getName();
        Long deptId = employee.getDepartment().getId();
        boolean isExistingEmployee = employee.getId() != null;

        if ("ROLE_DEPT_HEAD".equals(newRole)) {
            if (isExistingEmployee && employeeRepository.existsByManagerId(employee.getId())) {
                throw new ManagerPromotionConflictException("This manager currently has subordinates. Select a replacement manager before promoting to HOD.", employee.getId());
            }

            Department dept = departmentRepository.findById(deptId).orElseThrow();
            if (dept.getHod() != null) {
                Long currentHodId = dept.getHod().getId();
                if (!isExistingEmployee || !currentHodId.equals(employee.getId())) {
                    throw new HodConflictException("Department already has an HOD. Do you want to swap them?", deptId, currentHodId);
                }
            }
        }
    }

    @Transactional
    public void swapHodAndSave(Employee newHod, User newUser, boolean isExistingUserLinked, Set<LeaveType> selectedLeaves, Long oldHodId) {

        demoteEmployeeToStandard(oldHodId);

        Employee savedNewHod = executeStandardSave(newHod, newUser, isExistingUserLinked, selectedLeaves);

        Department dept = departmentRepository.findById(savedNewHod.getDepartment().getId()).orElseThrow();
        dept.setHod(savedNewHod);
        departmentRepository.save(dept);
    }

    @Transactional
    public void reassignSubordinatesAndPromoteToHod(Employee managerToPromote, User newUser, boolean isExistingUserLinked, Set<LeaveType> selectedLeaves, Long replacementManagerId) {

        employeeRepository.reassignManager(managerToPromote.getId(), replacementManagerId);

        Employee replacement = employeeRepository.findById(replacementManagerId).orElseThrow();
        if ("ROLE_EMPLOYEE".equals(replacement.getUser().getRole().getName())) {
            Role managerRole = roleRepository.findByName("ROLE_MANAGER");
            replacement.getUser().setRole(managerRole);
            userRepository.save(replacement.getUser());
        }

        Department dept = departmentRepository.findById(managerToPromote.getDepartment().getId()).orElseThrow();
        if (dept.getHod() != null && !dept.getHod().getId().equals(managerToPromote.getId())) {
            demoteEmployeeToStandard(dept.getHod().getId());
        }

        Employee savedHod = executeStandardSave(managerToPromote, newUser, isExistingUserLinked, selectedLeaves);
        dept.setHod(savedHod);
        departmentRepository.save(dept);
    }

    @Transactional
    public void deactivateEmployee(Employee employee) {
        Optional<Department> managedDept = departmentRepository.findByHodId(employee.getId());
        if (managedDept.isPresent()) {
            throw new HodDeleteConflictException("Cannot deactivate current HOD. Select a replacement.", employee.getId(), managedDept.get().getId());
        }

        if (employeeRepository.existsByManagerId(employee.getId())) {
            throw new ManagerDeleteConflictException("Cannot deactivate manager with active subordinates. Select a replacement manager.", employee.getId());
        }

        executeStandardDeactivation(employee);
    }

    @Transactional
    public void replaceHodAndDeactivate(Employee oldHod, Long replacementHodId) {
        Employee replacement = employeeRepository.findById(replacementHodId).orElseThrow();

        Role hodRole = roleRepository.findByName("ROLE_DEPT_HEAD");
        replacement.getUser().setRole(hodRole);
        userRepository.save(replacement.getUser());

        Department dept = oldHod.getDepartment();
        dept.setHod(replacement);
        departmentRepository.save(dept);

        executeStandardDeactivation(oldHod);
    }

    @Transactional
    public void reassignSubordinatesAndDeactivate(Employee oldManager, Long replacementManagerId) {
        Employee replacement = employeeRepository.findById(replacementManagerId).orElseThrow();

        if ("ROLE_EMPLOYEE".equals(replacement.getUser().getRole().getName())) {
            Role managerRole = roleRepository.findByName("ROLE_MANAGER");
            replacement.getUser().setRole(managerRole);
            userRepository.save(replacement.getUser());
        }

        employeeRepository.reassignManager(oldManager.getId(), replacementManagerId);

        executeStandardDeactivation(oldManager);
    }

    private Employee executeStandardSave(Employee currentEmployee, User currentUser, boolean isExistingUserLinked, Set<LeaveType> selectedLeaves) {
        boolean isNew = (currentEmployee.getId() == null);
        String oldState = isNew ? null : "{}";

        User finalUser = currentUser;
        if (isExistingUserLinked && currentUser != null && currentUser.getUsername() != null) {
            finalUser = userService.findByUsername(currentUser.getUsername());
            finalUser.setRole(currentUser.getRole());
        } else if (!isExistingUserLinked && currentUser != null) {
            finalUser = userService.save(currentUser);
        }
        currentEmployee.setUser(finalUser);

        Employee savedEmployee = employeeRepository.save(currentEmployee);
        Integer currentYear = LocalDate.now().getYear();
        Set<LeaveType> finalLeavesToSync = selectedLeaves;
        if (isNew && (selectedLeaves == null || selectedLeaves.isEmpty())) {
            finalLeavesToSync = new java.util.HashSet<>(leaveTypeRepository.findAll());
        }

        leaveBalanceService.initializeBalancesForEmployee(savedEmployee, currentYear, finalLeavesToSync);

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Employee {} successfully. Employee ID: {}", action, savedEmployee.getId());
        auditLoggingService.saveAuditLog(savedEmployee.getId(), action, "employees", oldState, "{}");

        return savedEmployee;
    }

    private void executeStandardDeactivation(Employee employee) {
        User currUser = employee.getUser();
        if (currUser != null) {
            currUser.setActive(false);
            userRepository.save(currUser);
        }
        employeeRepository.save(employee);
        log.info("Employee deactivated. Employee ID: {}", employee.getId());
    }

    private void demoteEmployeeToStandard(Long employeeId) {
        Employee emp = employeeRepository.findById(employeeId).orElseThrow();
        Role empRole = roleRepository.findByName("ROLE_EMPLOYEE");
        emp.getUser().setRole(empRole);

        Optional<Department> deptOpt = departmentRepository.findByHodId(emp.getId());
        if (deptOpt.isPresent()) {
            Department d = deptOpt.get();
            d.setHod(null);
            departmentRepository.save(d);
        }

        userRepository.save(emp.getUser());
    }

    public List<Employee> findAllActive() {
        return employeeRepository.findByActiveTrue();
    }

    public List<Employee> searchActive(String searchTerm) {
        return employeeRepository.searchActiveEmployees(searchTerm);
    }

    @Transactional
    public void reassignEmployeesAndDemoteHod(Department oldDept, Department newDept) {
        if (oldDept.getHod() != null) {
            Employee hod = oldDept.getHod();
            User hodUser = hod.getUser();

            if (hodUser != null) {
                Role empRole = roleRepository.findByName("ROLE_EMPLOYEE");

                hodUser.setRole(empRole);
                userRepository.save(hodUser);
            }
        }

        List<Employee> employees = employeeRepository.findByDepartmentId(oldDept.getId());
        for (Employee emp : employees) {
            emp.setDepartment(newDept);
        }

        employeeRepository.saveAll(employees);
    }
    @Transactional(readOnly = true)
    public Optional<Employee> findByIdWithDetails(Long id) {
        return employeeRepository.findByIdWithDepartmentAndManager(id);
    }

    public Optional<Employee> findById(Long employeeId) {
        return employeeRepository.findById(employeeId);
    }
}