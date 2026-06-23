package com.murali.config;

import com.murali.entity.*;
import com.murali.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final LeaveApprovalRuleRepository leaveApprovalRuleRepository;
    private final NavMenuRoleRepository navMenuRoleRepository;
    private final LeaveApprovalPolicyRepository policyRepository;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           EmployeeRepository employeeRepository,
                           LeaveTypeRepository leaveTypeRepository,
                           PasswordEncoder passwordEncoder,
                           NavMenuItemRepository navMenuItemRepository, LeaveApprovalRuleRepository leaveApprovalRuleRepository, NavMenuRoleRepository navMenuRoleRepository, LeaveApprovalPolicyRepository policyRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.passwordEncoder = passwordEncoder;
        this.navMenuItemRepository = navMenuItemRepository;
        this.leaveApprovalRuleRepository = leaveApprovalRuleRepository;
        this.navMenuRoleRepository = navMenuRoleRepository;
        this.policyRepository = policyRepository;
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
                role.setHierarchyWeight(0);
                switch (roleName){
                    case "ROLE_SUPER_ADMIN":
                        role.setHierarchyWeight(6);
                        break;
                    case "ROLE_HR_ADMIN":
                        role.setHierarchyWeight(5);
                        break;
                    case "ROLE_EMPLOYEE":
                        role.setHierarchyWeight(2);
                        break;
                    case "ROLE_MANAGER":
                        role.setHierarchyWeight(3);
                        break;
                    case "ROLE_AUDITOR":
                        role.setHierarchyWeight(1);
                        break;
                    case "ROLE_DEPT_HEAD":
                        role.setHierarchyWeight(4);
                        break;
                    default:
                        role.setHierarchyWeight(0);
                }
                roleRepository.save(role);
            }
        }
        LeaveApprovalPolicy defaultPolicy = initializeDefaultLeavePolicy();
        createLeaveTypeIfNotFound("Casual Leave", "CL-001", 10, true, true, defaultPolicy);
        createLeaveTypeIfNotFound("Sick Leave", "SL-001", 12, true, true, defaultPolicy);
        createLeaveTypeIfNotFound("Earned Leave", "EL-001", 6, true, true, defaultPolicy);
        createLeaveTypeIfNotFound("Work From Home", "WFH-001", 60, true, false, defaultPolicy);
        createLeaveTypeIfNotFound("Half Day Leave", "HDL-001", 12, true, true, defaultPolicy);
        createLeaveTypeIfNotFound("Emergency Leave", "EMG-001", 10, true, true, defaultPolicy);
        createLeaveTypeIfNotFound("Unpaid Leave", "UPL-001", 365, false, false, defaultPolicy);

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

            superEmployee = employeeRepository.save(superEmployee);

            // 6. Resolve Circular Dependency: Set HOD to Department
            adminDept.setHod(superEmployee);
            departmentRepository.save(adminDept);
        }

        // 7. Initialize Navigation Menus
        initializeNavigationMenus();


        System.out.println("System Initialized: Roles, Leave Types, Super Admin, and Navigation Menus setup complete.");
    }

    private void createLeaveTypeIfNotFound(String name, String code, int maxDays, boolean isPaid, boolean sand, LeaveApprovalPolicy policy) {
        if (leaveTypeRepository.findByCode(code).isEmpty()) {
            LeaveType leaveType = new LeaveType();
            leaveType.setName(name);
            leaveType.setCode(code);
            leaveType.setMaxDaysPerYear(maxDays);
            leaveType.setPaid(isPaid);
            leaveType.setApplySandwichRule(sand);
            leaveType.setApprovalPolicy(policy);

            leaveTypeRepository.save(leaveType);
        }
    }

    private void initializeNavigationMenus() {
        // 1. Define and Upsert all unique Menu Items
        NavMenuItem departments = getOrCreateNewMenuItem("Departments", "add-departments", "BUILDING");
        NavMenuItem employees = getOrCreateNewMenuItem("Employees", "add-employees", "USER_CARD");
        NavMenuItem leaveTypes = getOrCreateNewMenuItem("Leave Types", "add-leave-types", "CALENDAR_USER");
        NavMenuItem roles = getOrCreateNewMenuItem("Roles", "add-role", "SAFE");
        NavMenuItem shifts = getOrCreateNewMenuItem("Shifts", "add-shifts", "CLOCK");
        NavMenuItem users = getOrCreateNewMenuItem("Users", "add-user", "USER");
        NavMenuItem shiftMgmt = getOrCreateNewMenuItem("Shift Management", "shift-assignments", "CALENDAR");
        NavMenuItem sysConfig = getOrCreateNewMenuItem("System Configuration", "admin-config", "COG");
        NavMenuItem applyLeave = getOrCreateNewMenuItem("Apply Leave", "apply-leave", "FLIGHT_TAKEOFF");
        NavMenuItem approvals = getOrCreateNewMenuItem("Approval Inbox", "approvals", "CHECK_SQUARE_O");
        NavMenuItem audit = getOrCreateNewMenuItem("Audit & Compliance", "audit-dashboard", "CLIPBOARD_CHECK");

        // =============================================
        // ROLE_SUPER_ADMIN
        // =============================================
        assignMenuToRole("ROLE_SUPER_ADMIN", departments);
        assignMenuToRole("ROLE_SUPER_ADMIN", employees);
        assignMenuToRole("ROLE_SUPER_ADMIN", leaveTypes);
        assignMenuToRole("ROLE_SUPER_ADMIN", roles);
        assignMenuToRole("ROLE_SUPER_ADMIN", shifts);
        assignMenuToRole("ROLE_SUPER_ADMIN", users);
        assignMenuToRole("ROLE_SUPER_ADMIN", shiftMgmt);
        assignMenuToRole("ROLE_SUPER_ADMIN", sysConfig);
        assignMenuToRole("ROLE_SUPER_ADMIN", applyLeave);
        assignMenuToRole("ROLE_SUPER_ADMIN", approvals);
        assignMenuToRole("ROLE_SUPER_ADMIN", audit);

        // =============================================
        // ROLE_HR_ADMIN
        // =============================================
        assignMenuToRole("ROLE_HR_ADMIN", shiftMgmt);
        assignMenuToRole("ROLE_HR_ADMIN", approvals);
        assignMenuToRole("ROLE_HR_ADMIN", applyLeave);
        assignMenuToRole("ROLE_HR_ADMIN", sysConfig);
        assignMenuToRole("ROLE_HR_ADMIN", employees);

        // =============================================
        // ROLE_MANAGER
        // =============================================
        assignMenuToRole("ROLE_MANAGER", approvals);
        assignMenuToRole("ROLE_MANAGER", applyLeave);

        // =============================================
        // ROLE_DEPT_HEAD
        // =============================================
        assignMenuToRole("ROLE_DEPT_HEAD", approvals);
        assignMenuToRole("ROLE_DEPT_HEAD", applyLeave);

        // =============================================
        // ROLE_EMPLOYEE
        // =============================================
        assignMenuToRole("ROLE_EMPLOYEE", applyLeave);

        // =============================================
        // ROLE_AUDITOR
        // =============================================
        assignMenuToRole("ROLE_AUDITOR", applyLeave);
        assignMenuToRole("ROLE_AUDITOR", audit);
    }

    // Helper 1: Ensures the unique menu item exists and updates it if labels/icons changed
    private NavMenuItem getOrCreateNewMenuItem(String label, String path, String iconName) {
        return navMenuItemRepository.findByPath(path)
                .map(existingItem -> {
                    // Optional: Update label or icon if they changed in code
                    existingItem.setLabel(label);
                    existingItem.setIconName(iconName);
                    return navMenuItemRepository.save(existingItem);
                })
                .orElseGet(() -> navMenuItemRepository.save(
                        NavMenuItem.builder().label(label).path(path).iconName(iconName).build()
                ));
    }

    // Helper 2: Maps the menu item to the role safely without duplicates
    private void assignMenuToRole(String roleName, NavMenuItem menuItem) {
        if (!navMenuRoleRepository.existsByRoleNameAndNavMenuItem(roleName, menuItem)) {
            NavMenuRole mapping = NavMenuRole.builder()
                    .roleName(roleName)
                    .navMenuItem(menuItem)
                    .build();
            navMenuRoleRepository.save(mapping);
        }
    }
    private LeaveApprovalPolicy initializeDefaultLeavePolicy() {
        LeaveApprovalPolicy policy = policyRepository.findByName("Standard Company Policy");

        if (policy == null) {
            policy = new LeaveApprovalPolicy();
            policy.setName("Standard Company Policy");
            policy.setRules(new ArrayList<>());

            Role manager = roleRepository.findByName("ROLE_MANAGER");
            Role deptHead = roleRepository.findByName("ROLE_DEPT_HEAD");
            Role hrAdmin = roleRepository.findByName("ROLE_HR_ADMIN");

            // Tier 1: Small duration (0.5 to 2.0 days) -> Needs Manager Approval (Level 1)
            policy.getRules().add(createRule(policy, 0.5, 2.0, 1, manager));

            // Tier 2: Mid duration (2.5 to 5.0 days) -> Needs Manager (L1) then Dept Head (L2)
            policy.getRules().add(createRule(policy, 2.5, 5.0, 1, manager));
            policy.getRules().add(createRule(policy, 2.5, 5.0, 2, deptHead));

            // Tier 3: High duration (5.5+ days) -> Needs Manager (L1), Dept Head (L2), then HR Admin (L3)
            policy.getRules().add(createRule(policy, 5.5, 99.9, 1, manager));
            policy.getRules().add(createRule(policy, 5.5, 99.9, 2, deptHead));
            policy.getRules().add(createRule(policy, 5.5, 99.9, 3, hrAdmin));

            // Because of CascadeType.ALL, saving the policy saves all 6 rules instantly!
            policy = policyRepository.save(policy);
        }
        return policy;
    }

    private LeaveApprovalRule createRule(LeaveApprovalPolicy policy, double minDays, double maxDays, int level, Role role) {
        LeaveApprovalRule rule = new LeaveApprovalRule();
        rule.setPolicy(policy);
        rule.setMinDays(BigDecimal.valueOf(minDays));
        rule.setMaxDays(BigDecimal.valueOf(maxDays));
        rule.setApprovalLevel(level);
        rule.setRequiredRole(role);
        return rule;
    }

}