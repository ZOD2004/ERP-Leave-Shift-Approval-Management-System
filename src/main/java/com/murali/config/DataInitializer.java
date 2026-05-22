package com.murali.config;

import com.murali.entity.*;
import com.murali.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final NavMenuItemRepository navMenuItemRepository;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           EmployeeRepository employeeRepository,
                           LeaveTypeRepository leaveTypeRepository,
                           PasswordEncoder passwordEncoder,
                           NavMenuItemRepository navMenuItemRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.passwordEncoder = passwordEncoder;
        this.navMenuItemRepository = navMenuItemRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        // 1. Initialize Roles
        List<String> roles = Arrays.asList(
                "ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN", "ROLE_EMPLOYEE",
                "ROLE_MANAGER", "ROLE_AUDITOR", "ROLE_DEPT_HEAD"
        );

        for (String roleName : roles) {
            if (roleRepository.findByName(roleName) == null) {
                Role role = new Role();
                role.setName(roleName);
                roleRepository.save(role);
            }
        }

        // 2. Initialize Leave Types
        createLeaveTypeIfNotFound("Sick Leave", "SL-001", 12, true);
        createLeaveTypeIfNotFound("Half Day Leave", "HDL-001", 12, true);
        createLeaveTypeIfNotFound("Emergency Leave", "EMG-001", 10, true);

        // 3. Initialize Dummy Department (No HOD yet)
        Department adminDept = departmentRepository.findByName("Administration");
        if (adminDept == null) {
            adminDept = new Department();
            adminDept.setName("Administration");
            adminDept = departmentRepository.save(adminDept);
        }

        // 4. Initialize Super User
        User superUser = userRepository.findByUsername("super");
        if (superUser == null) {
            superUser = new User();
            superUser.setUsername("super");
            superUser.setEmail("super@admin.com");
            superUser.setPasswordHash(passwordEncoder.encode("1234"));
            superUser.setActive(true);

            Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN");
            superUser.setRole(superAdminRole);

            superUser = userRepository.save(superUser);
        }

        // 5. Initialize Super Employee
        if (employeeRepository.findByEmployeeCode("EMP-SUPER").isEmpty()) {
            Employee superEmployee = new Employee();
            superEmployee.setEmployeeCode("EMP-SUPER");
            superEmployee.setFirstName("Super Admin");
            superEmployee.setUser(superUser);
            superEmployee.setDepartment(adminDept);

            // Assign all default leave types to the super employee
            superEmployee.getApplicableLeaveTypes().addAll(leaveTypeRepository.findAll());

            superEmployee = employeeRepository.save(superEmployee);

            // 6. Resolve Circular Dependency: Set HOD to Department
            adminDept.setHod(superEmployee);
            departmentRepository.save(adminDept);
        }

        // 7. Initialize Navigation Menus
        initializeNavigationMenus();

        System.out.println("System Initialized: Roles, Leave Types, Super Admin, and Navigation Menus setup complete.");
    }

    private void createLeaveTypeIfNotFound(String name, String code, int maxDays, boolean isPaid) {
        if (leaveTypeRepository.findByCode(code).isEmpty()) {
            LeaveType leaveType = new LeaveType();
            leaveType.setName(name);
            leaveType.setCode(code);
            leaveType.setMaxDaysPerYear(maxDays);
            leaveType.setPaid(isPaid);
            leaveTypeRepository.save(leaveType);
        }
    }

    private void initializeNavigationMenus() {
        // =============================================
        // ROLE_SUPER_ADMIN
        // =============================================
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "Departments", "add-departments", "BUILDING");
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "Employees", "add-employees", "USER_CARD");
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "Leave Types", "add-leave-types", "CALENDAR_USER");
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "Roles", "add-role", "SAFE");
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "Shifts", "add-shifts", "CLOCK");
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "Users", "add-user", "USER");
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "Shift Management", "shift-assignments", "CALENDAR");
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "System Configuration", "admin-config", "COG");
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "Apply Leave", "apply-leave", "FLIGHT_TAKEOFF");
        createNavMenuIfNotFound("ROLE_SUPER_ADMIN", "Approval Inbox", "approvals", "CHECK_SQUARE_O");

        // =============================================
        // ROLE_HR_ADMIN
        // =============================================
        createNavMenuIfNotFound("ROLE_HR_ADMIN", "Shift Management", "shift-assignments", "CALENDAR");
        createNavMenuIfNotFound("ROLE_HR_ADMIN", "Approval Inbox", "approvals", "CHECK_SQUARE_O");
        createNavMenuIfNotFound("ROLE_HR_ADMIN", "Apply Leave", "apply-leave", "FLIGHT_TAKEOFF");
        createNavMenuIfNotFound("ROLE_HR_ADMIN", "System Configuration", "admin-config", "COG");
        createNavMenuIfNotFound("ROLE_HR_ADMIN", "Employees", "add-employees", "USER_CARD");

        // =============================================
        // ROLE_MANAGER
        // =============================================
        createNavMenuIfNotFound("ROLE_MANAGER", "Approval Inbox", "approvals", "CHECK_SQUARE_O");
        createNavMenuIfNotFound("ROLE_MANAGER", "Apply Leave", "apply-leave", "FLIGHT_TAKEOFF");

        // =============================================
        // ROLE_DEPT_HEAD
        // =============================================
        createNavMenuIfNotFound("ROLE_DEPT_HEAD", "Approval Inbox", "approvals", "CHECK_SQUARE_O");
        createNavMenuIfNotFound("ROLE_DEPT_HEAD", "Apply Leave", "apply-leave", "FLIGHT_TAKEOFF");

        // =============================================
        // ROLE_EMPLOYEE
        // =============================================
        createNavMenuIfNotFound("ROLE_EMPLOYEE", "Apply Leave", "apply-leave", "FLIGHT_TAKEOFF");

        // =============================================
        // ROLE_AUDITOR
        // =============================================
        createNavMenuIfNotFound("ROLE_AUDITOR", "Apply Leave", "apply-leave", "FLIGHT_TAKEOFF");
    }

    private void createNavMenuIfNotFound(String roleName, String label, String path, String iconName) {
        if (!navMenuItemRepository.existsByRoleNameAndPath(roleName, path)) {
            NavMenuItem navMenuItem = NavMenuItem.builder()
                    .roleName(roleName)
                    .label(label)
                    .path(path)
                    .iconName(iconName)
                    .build();
            navMenuItemRepository.save(navMenuItem);
        }
    }
}