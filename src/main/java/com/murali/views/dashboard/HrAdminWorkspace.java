package com.murali.views.dashboard;

import com.murali.dto.DailyExpectedShift;
import com.murali.dto.TeamAttendanceSummaryDTO;
import com.murali.entity.Attendance;
import com.murali.entity.AttendanceCorrection;
import com.murali.entity.Employee;
import com.murali.repository.EmployeeRepository;
import com.murali.service.*;
import com.murali.util.SecurityService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.murali.entity.Department;
import com.murali.repository.DepartmentRepository;
import com.vaadin.flow.component.treegrid.TreeGrid;

import java.util.HashMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SpringComponent
@UIScope
public class HrAdminWorkspace extends VerticalLayout {

    private final SecurityService securityService;
    private final EmployeeRepository employeeRepository;
    private final ScheduleCalculationService scheduleCalculationService;
    private final EmployeeService employeeService;
    private final AttendanceProcessService attendanceProcessService;
    private final LeaveBalanceService leaveBalanceService;
    private final AttendanceCorrectionService attendanceCorrectionService;
    private final LeaveRequestService leaveRequestService;
    private final DepartmentRepository departmentRepository;

    // Memory Maps for instant grid rendering
    private Map<Long, Attendance> todayAttendanceMap = new HashMap<>();
    private Map<Long, DailyExpectedShift> todayScheduleMap = new HashMap<>();
    private List<Employee> allActiveEmployees;
    private List<Department> allDepartments;

    public HrAdminWorkspace(SecurityService securityService, EmployeeRepository employeeRepository, ScheduleCalculationService scheduleCalculationService, EmployeeService employeeService, AttendanceProcessService attendanceProcessService, LeaveBalanceService leaveBalanceService, AttendanceCorrectionService attendanceCorrectionService, LeaveRequestService leaveRequestService, DepartmentRepository departmentRepository) {
        this.securityService = securityService;
        this.employeeRepository = employeeRepository;
        this.scheduleCalculationService = scheduleCalculationService;
        this.employeeService = employeeService;
        this.attendanceProcessService = attendanceProcessService;
        this.leaveBalanceService = leaveBalanceService;
        this.attendanceCorrectionService = attendanceCorrectionService;
        this.leaveRequestService = leaveRequestService;
        this.departmentRepository = departmentRepository;

        setPadding(false);
        setSpacing(true);
        setWidthFull();
        getStyle().set("gap", "var(--app-layout-margin)"); // Applies consistent global block spacing

        buildUI();
    }

    private void buildUI() {
        Long currentUserId = securityService.getCurrentUserId();
        Employee hrEmployee = employeeRepository.findByUserId(currentUserId).orElse(null);

        if (hrEmployee == null) {
            add(new H2("Error: No Employee record linked to your user account."));
            return;
        }

        allActiveEmployees = employeeService.findAllActive();
        allDepartments = departmentRepository.findAll();
        loadBulkDailyData(allActiveEmployees);
// 1. Personal Header
        add(createPersonalHeader(hrEmployee));
        // Removed unnecessary spacer Hr tags

        H2 header = new H2("HR Global Workspace");
        header.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.MEDIUM, LumoUtility.TextColor.PRIMARY);
        add(header);

        // 2. KPI Cards Section
        add(createGlobalKpiSection());

        // 3. Weekly Schedule
        add(createWeeklyScheduleWidget(hrEmployee));

        // 4. Middle Section: Leave Utilization
        add(createLeaveUtilizationWidget());

        // 5. Global Hierarchy TreeGrid
        add(createCompanyTreeGrid());

        // 6. Bottom Section: Anomalies Grid
        add(createGlobalAnomaliesWidget());
    }

    private Component createGlobalKpiSection() {
        HorizontalLayout kpiLayout = new HorizontalLayout();
        kpiLayout.setWidthFull();
        kpiLayout.setSpacing(false);
        kpiLayout.getStyle().set("gap", "var(--app-padding)");
        // This ensures the cards stay horizontal and scroll sideways on smaller screens
        kpiLayout.getStyle().set("overflow-x", "auto");
        kpiLayout.getStyle().set("padding-bottom", "8px");

        // Fetch Data safely (Using pre-loaded memory maps)
        long totalEmployees = allActiveEmployees.size();
        long pendingApprovals = leaveRequestService.countPendingRequests();
        long pendingAnomalies = attendanceCorrectionService.getAllPendingCorrectionsGlobally().size();

        int presentCount = 0;
        int expectedCount = 0;
        int absentOrLeaveCount = 0;

        for (Employee emp : allActiveEmployees) {
            Attendance att = todayAttendanceMap.get(emp.getId());
            DailyExpectedShift shift = todayScheduleMap.get(emp.getId());

            if (shift != null && shift.isWorkingDay()) {
                if (att != null && att.getFirstCheckIn() != null) {
                    presentCount++;
                } else if (shift.getActiveLeave() != null) {
                    absentOrLeaveCount++;
                } else {
                    expectedCount++;
                }
            } else {
                absentOrLeaveCount++; // Off day
            }
        }

        kpiLayout.add(
                createStatCard("Total Headcount", String.valueOf(totalEmployees), VaadinIcon.GROUP, "var(--app-primary-color)"),
                createStatCard("Present Today", String.valueOf(presentCount), VaadinIcon.CHECK_CIRCLE, "#24a148"),
                createStatCard("Yet to Check-in", String.valueOf(expectedCount), VaadinIcon.CLOCK, "#f1c21b"),
                createStatCard("On Leave / Off", String.valueOf(absentOrLeaveCount), VaadinIcon.FLIGHT_TAKEOFF, "var(--app-text-secondary)"),
                createStatCard("Pending Leaves", String.valueOf(pendingApprovals), VaadinIcon.INBOX, "#f1c21b"),
                createStatCard("Pending Anomalies", String.valueOf(pendingAnomalies), VaadinIcon.WARNING, "#da1e28")
        );

        return kpiLayout;
    }
    private Component createStatCard(String title, String value, VaadinIcon iconEnum, String iconColor) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames("standard-surface", "hoverable");
        card.setSpacing(false);
        card.setMinWidth("180px");
        card.getStyle().set("flex-grow", "1");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD);

        Icon icon = iconEnum.create();
        icon.setSize("16px");
        icon.setColor(iconColor);

        header.add(titleSpan, icon);

        H2 valueSpan = new H2(value);
        valueSpan.addClassNames(LumoUtility.Margin.Top.SMALL, LumoUtility.Margin.Bottom.NONE);

        card.add(header, valueSpan);
        return card;
    }

    private Component createLeaveUtilizationWidget() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);

        H3 title = new H3("Company Leave Utilization (" + LocalDate.now().getYear() + ")");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.MEDIUM);

        // Fetch utilization stats
        Map<String, BigDecimal> data = leaveBalanceService.getGlobalLeaveUtilization(LocalDate.now().getYear());
        BigDecimal total = data.getOrDefault("total", BigDecimal.ZERO);
        BigDecimal used = data.getOrDefault("used", BigDecimal.ZERO);

        double totalD = total.doubleValue();
        double usedD = used.doubleValue();
        double percentage = totalD > 0 ? (usedD / totalD) : 0.0;

        // Visual Progress Bar
        ProgressBar progressBar = new ProgressBar();
        progressBar.setValue(percentage);
        progressBar.setHeight("12px");
        progressBar.addClassNames(LumoUtility.BorderRadius.LARGE, LumoUtility.Margin.Bottom.SMALL);

        if (percentage > 0.8) {
            // Warn if company is burning leaves too fast
            progressBar.getStyle().set("--lumo-primary-color", "var(--lumo-error-color)");
        } else if (percentage > 0.5) {
            progressBar.getStyle().set("--lumo-primary-color", "var(--lumo-warning-color)");
        } else {
            progressBar.getStyle().set("--lumo-primary-color", "var(--lumo-success-color)");
        }

        // Stats Labels
        HorizontalLayout stats = new HorizontalLayout();
        stats.setWidthFull();
        stats.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Span usedLabel = new Span(String.format("Used: %,.1f Days", usedD));
        usedLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.BOLD);

        Span burnRateLabel = new Span(String.format("Burn Rate: %.1f%%", percentage * 100));
        burnRateLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD);

        Span totalLabel = new Span(String.format("Total Allocated: %,.1f Days", totalD));
        totalLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.BOLD);

        stats.add(usedLabel, burnRateLabel, totalLabel);

        layout.add(title, progressBar, stats);
        return layout;
    }

    private Component createGlobalAnomaliesWidget() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Global Attendance Anomalies (Needs Manager Action)");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        Grid<AttendanceCorrection> grid = new Grid<>(AttendanceCorrection.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.setHeight("300px");
        grid.addClassName("standard-surface");

        grid.addColumn(ac -> ac.getAttendance().getEmployee().getFirstName() + " (ID: " + ac.getAttendance().getEmployee().getId() + ")").setHeader("Employee").setAutoWidth(true).setFlexGrow(1);

        // Date Column
        grid.addColumn(ac -> ac.getAttendance().getAttendanceDate()).setHeader("Date").setAutoWidth(true);

        // Assigned Approver (Manager) Column
        grid.addColumn(ac -> ac.getApprover() != null ? ac.getApprover().getUsername() : "Unassigned").setHeader("Assigned Manager").setAutoWidth(true);

        // Status Badge
        grid.addComponentColumn(ac -> {
            Span badge = new Span(ac.getStatus());
            badge.getElement().getThemeList().add("badge error");
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        // Populate Grid
        grid.setItems(attendanceCorrectionService.getAllPendingCorrectionsGlobally());

        section.add(title, grid);
        return section;
    }

    private Component createPersonalHeader(Employee employee) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout welcomeTexts = new VerticalLayout();
        welcomeTexts.setPadding(false);
        welcomeTexts.setSpacing(false);

        H2 greeting = new H2("Welcome back, " + employee.getFirstName() + "!");
        greeting.addClassNames(LumoUtility.Margin.NONE);

        Span roleSpan = new Span("HR Administrator | ID: " + employee.getEmployeeCode());
        roleSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        welcomeTexts.add(greeting, roleSpan);

        Button punchBtn = new Button();
        Optional<Attendance> todayOpt = attendanceProcessService.getTodayAttendance(employee.getId());

        boolean isOnLeave = todayOpt.isPresent() && ("ON_LEAVE".equals(todayOpt.get().getStatus().name()) || "FULL_LEAVE".equals(todayOpt.get().getStatus().name()));

        if (isOnLeave) {
            punchBtn.setText("On Leave Today");
            punchBtn.setIcon(VaadinIcon.UMBRELLA.create());
            punchBtn.setEnabled(false);
        } else {
            boolean isWorking = todayOpt.isPresent() && "WORKING".equals(todayOpt.get().getStatus().name());

            if (!isWorking) {
                punchBtn.setText("Clock In");
                punchBtn.setIcon(VaadinIcon.SIGN_IN.create());
                punchBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                punchBtn.addClickListener(e -> handlePunch(employee.getId(), true));
            } else {
                punchBtn.setText("Clock Out");
                punchBtn.setIcon(VaadinIcon.SIGN_OUT.create());
                punchBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
                punchBtn.addClickListener(e -> handlePunch(employee.getId(), false));
            }
        }

        header.add(welcomeTexts, punchBtn);
        return header;
    }

    private void handlePunch(Long employeeId, boolean isCheckIn) {
        try {
            attendanceProcessService.processDailyPunch(employeeId, LocalDateTime.now(), isCheckIn);
            Notification.show(isCheckIn ? "Clocked In Successfully!" : "Clocked Out Successfully!", 3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().getPage().reload();
        } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private Component createWeeklyScheduleWidget(Employee employee) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("My Weekly Schedule");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);

        Grid<DailyExpectedShift> grid = new Grid<>(DailyExpectedShift.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.addClassName("standard-surface");
        grid.setHeight("250px");

        grid.addColumn(shift -> shift.getTargetDate().toString() + " (" + shift.getTargetDate().getDayOfWeek().name().substring(0, 3) + ")").setHeader("Date").setAutoWidth(true);

        grid.addComponentColumn(shift -> {
            Span badge = new Span();
            badge.getElement().getThemeList().add("badge small");

            if (shift.getActiveLeave() != null) {
                badge.setText("Leave: " + shift.getActiveLeave().getLeaveType().getName());
                badge.getElement().getThemeList().add("warning");
            } else if (shift.isHoliday()) {
                badge.setText("Holiday");
                badge.getElement().getThemeList().add("error");
            } else if (shift.isWorkingDay() && shift.getExpectedShift() != null) {
                badge.setText(shift.getExpectedShift().getStartTime() + " - " + shift.getExpectedShift().getEndTime());
                badge.getElement().getThemeList().add("success");
            } else {
                badge.setText("Off Day");
                badge.getElement().getThemeList().add("contrast");
            }
            return badge;
        }).setHeader("Schedule").setAutoWidth(true);

        LocalDate today = LocalDate.now();
        Map<Long, List<DailyExpectedShift>> bulkShifts = scheduleCalculationService.calculateBatchShifts(List.of(employee), today, today.plusDays(6));
        grid.setItems(bulkShifts.getOrDefault(employee.getId(), Collections.emptyList()));

        section.add(title, grid);
        return section;
    }

    private void loadBulkDailyData(List<Employee> emps) {
        if (emps == null || emps.isEmpty()) return;
        LocalDate today = LocalDate.now();

        // Load Schedules for everyone
        Map<Long, List<DailyExpectedShift>> batchSchedules = scheduleCalculationService.calculateBatchShifts(emps, today, today);
        batchSchedules.forEach((empId, shifts) -> {
            if (!shifts.isEmpty()) todayScheduleMap.put(empId, shifts.get(0));
        });

        // Load Attendance for everyone (Using your existing custom query method if possible, or mapping it manually)
        // Note: For HR, we will fetch directly from attendanceProcessService safely.
        for (Employee emp : emps) {
            attendanceProcessService.getTodayAttendance(emp.getId()).ifPresent(att -> todayAttendanceMap.put(emp.getId(), att));
        }
    }

    private Component createCompanyTreeGrid() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Company Directory & Live Status");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);


        TreeGrid<Object> grid = new TreeGrid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.addClassName("standard-surface");
        grid.setHeight("400px");

        // Column 1: Hierarchy (Department Name OR Employee Name)
        grid.addHierarchyColumn(item -> {
            if (item instanceof Department dept) {
                String hodName = dept.getHod() != null ? dept.getHod().getFirstName() : "No HOD Assigned";
                return dept.getName() + " (HOD: " + hodName + ")";
            }
            if (item instanceof Employee emp) {
                return emp.getFirstName() + " (" + emp.getEmployeeCode() + ")";
            }
            return "";
        }).setHeader("Department / Employee Name").setFlexGrow(2);

        // Column 2: Role
        grid.addColumn(item -> {
            if (item instanceof Employee emp && emp.getUser() != null) {
                return emp.getUser().getRole().getName().replace("ROLE_", "").replace("_", " ");
            }
            return ""; // Departments don't have roles
        }).setHeader("Role").setAutoWidth(true);

        // Column 3: Live Status
        grid.addComponentColumn(item -> {
            if (item instanceof Department) {
                return new Span(); // Leave blank for department rows
            }

            Employee emp = (Employee) item;
            Attendance att = todayAttendanceMap.get(emp.getId());
            DailyExpectedShift expected = todayScheduleMap.get(emp.getId());

            Span badge = new Span();
            badge.getElement().getThemeList().add("badge small");

            if (att != null && att.getFirstCheckIn() != null) {
                badge.setText("Present (" + att.getFirstCheckIn().toLocalTime().toString() + ")");
                badge.getElement().getThemeList().add("success");
            } else if (expected != null && expected.getActiveLeave() != null) {
                badge.setText("On Leave");
                badge.getElement().getThemeList().add("warning");
            } else if (expected != null && !expected.isWorkingDay()) {
                badge.setText("Off Day");
                badge.getElement().getThemeList().add("contrast");
            } else {
                badge.setText("Expected");
                badge.getElement().getThemeList().add("error");
            }
            return badge;
        }).setHeader("Live Status").setAutoWidth(true);

        grid.addComponentColumn(item -> {
            if (item instanceof Employee emp) {
                return createViewLeavesBtn(emp);
            }
            return new Span(); // Leave blank for Department rows
        }).setHeader("Actions").setAutoWidth(true);

        // Populate the Grid
        List<Object> rootItems = new java.util.ArrayList<>(allDepartments);

        grid.setItems(rootItems, item -> {
            // When expanding a department, return all employees belonging to it
            if (item instanceof Department dept) {
                return allActiveEmployees.stream().filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(dept.getId())).map(e -> (Object) e) // Cast Employee to Object to satisfy TreeGrid<Object>
                        .toList();
            }
            return java.util.Collections.emptyList();
        });


        section.add(title, grid);
        return section;
    }
    private Button createViewLeavesBtn(Employee emp) {
        Button btn = new Button("Leaves", VaadinIcon.EYE.create());
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        btn.addClickListener(e -> new EmployeeLeaveDialog(emp, leaveBalanceService, leaveRequestService).open());
        return btn;
    }
}
