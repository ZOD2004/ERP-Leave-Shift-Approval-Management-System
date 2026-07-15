package com.murali.views.dashboard;

import com.murali.entity.Attendance;
import com.murali.entity.Employee;
import com.murali.entity.Shift;
import com.murali.service.*;
import com.murali.dto.DailyExpectedShift;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.util.SecurityService;
import com.murali.views.components.GlobalSearchComponent;
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
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@SpringComponent
@UIScope
public class HodWorkspace extends VerticalLayout {

    private final SecurityService securityService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final ScheduleCalculationService scheduleCalculationService;
    private final ApprovalRoutingService approvalRoutingService;
    private final AttendanceProcessService attendanceProcessService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestService leaveRequestService;

    private Map<Long, List<Employee>> managerToDirectReportsMap = new HashMap<>();
    private Map<Long, Attendance> todayAttendanceMap = new HashMap<>();
    private Map<Long, DailyExpectedShift> todayScheduleMap = new HashMap<>();

    public HodWorkspace(SecurityService securityService, EmployeeRepository employeeRepository, AttendanceRepository attendanceRepository, ScheduleCalculationService scheduleCalculationService, ApprovalRoutingService approvalRoutingService, AttendanceProcessService attendanceProcessService, LeaveBalanceService leaveBalanceService, LeaveRequestService leaveRequestService) {

        this.securityService = securityService;
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.scheduleCalculationService = scheduleCalculationService;
        this.approvalRoutingService = approvalRoutingService;
        this.attendanceProcessService = attendanceProcessService;
        this.leaveBalanceService = leaveBalanceService;
        this.leaveRequestService = leaveRequestService;

        setPadding(false);
        setSpacing(true);
        setWidthFull();
        getStyle().set("gap", "var(--app-layout-margin)");

        buildUI();
    }

    private void buildUI() {
        Long currentUserId = securityService.getCurrentUserId();
        Employee hod = employeeRepository.findByUserId(currentUserId).orElse(null);

        if (hod == null || hod.getDepartment() == null) {
            add(new H2("Error: No Department Assigned to your profile."));
            return;
        }

        List<Employee> departmentEmployees = employeeRepository.findByDepartmentId(hod.getDepartment().getId());

        loadBulkDailyData(departmentEmployees);

        buildHierarchyMap(departmentEmployees, hod.getId());

        add(createPersonalHeader(hod));
        H2 teamHeader = new H2(hod.getDepartment().getName() + " - Department Overview");
        teamHeader.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.MEDIUM, LumoUtility.TextColor.PRIMARY);
        add(teamHeader);

        add(createDepartmentKpis(departmentEmployees, currentUserId));
        add(createWeeklyScheduleWidget(hod));
        add(createHierarchyTreeGrid(hod.getId()));
    }

    private void loadBulkDailyData(List<Employee> deptEmployees) {
        LocalDate today = LocalDate.now();
        List<Long> empIds = deptEmployees.stream().map(Employee::getId).toList();

        // Load Schedules
        Map<Long, List<DailyExpectedShift>> batchSchedules = scheduleCalculationService.calculateBatchShifts(deptEmployees, today, today);
        batchSchedules.forEach((empId, shifts) -> {
            if (!shifts.isEmpty()) {
                todayScheduleMap.put(empId, shifts.get(0));
            }
        });

        List<Attendance> attendances = attendanceRepository.findByEmployeeIdsAndAttendanceDate(empIds, today);
        attendances.forEach(a -> todayAttendanceMap.put(a.getEmployee().getId(), a));
    }

    private void buildHierarchyMap(List<Employee> deptEmployees, Long hodId) {
        managerToDirectReportsMap.clear();
        for (Employee emp : deptEmployees) {
            if (emp.getId().equals(hodId)) continue;

            Long managerId = emp.getManager() != null ? emp.getManager().getId() : hodId; // Default orphans to HOD
            managerToDirectReportsMap.computeIfAbsent(managerId, k -> new ArrayList<>()).add(emp);
        }
    }

    private Component createDepartmentKpis(List<Employee> deptEmployees, Long hodUserId) {
        HorizontalLayout kpiLayout = new HorizontalLayout();
        kpiLayout.setWidthFull();
        kpiLayout.setSpacing(false);
        kpiLayout.getStyle().set("gap", "var(--app-padding)");
        kpiLayout.getStyle().set("overflow-x", "auto");
        kpiLayout.getStyle().set("padding-bottom", "8px");

        int totalHeadcount = deptEmployees.size() - 1;
        int managersCount = (int) deptEmployees.stream().filter(e -> "ROLE_MANAGER".equals(e.getUser().getRole().getName())).count();

        int presentCount = 0;
        int onLeaveCount = 0;

        for (Employee emp : deptEmployees) {
            Attendance att = todayAttendanceMap.get(emp.getId());
            DailyExpectedShift shift = todayScheduleMap.get(emp.getId());

            if (att != null && att.getFirstCheckIn() != null) {
                presentCount++;
            } else if (shift != null && shift.getActiveLeave() != null) {
                onLeaveCount++;
            }
        }

        int pendingApprovals = approvalRoutingService.getPendingApprovalsForUser(hodUserId).size();

        kpiLayout.add(
                createStatCard("Dept Headcount", String.valueOf(totalHeadcount), VaadinIcon.GROUP, "var(--app-primary-color)"),
                createStatCard("Sub-Managers", String.valueOf(managersCount), VaadinIcon.USER_STAR, "var(--app-text-secondary)"),
                createStatCard("Present Today", String.valueOf(presentCount), VaadinIcon.CHECK_CIRCLE, "#24a148"),
                createStatCard("On Leave", String.valueOf(onLeaveCount), VaadinIcon.FLIGHT_TAKEOFF, "#f1c21b"),
                createStatCard("Action Required", String.valueOf(pendingApprovals), VaadinIcon.INBOX, "#da1e28")
        );

        return kpiLayout;
    }

    private Component createStatCard(String title, String value, VaadinIcon iconEnum, String iconColor) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames("standard-surface", "hoverable");card.setSpacing(false);
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

    private Component createHierarchyTreeGrid(Long hodId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Reporting Hierarchy & Live Status");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        TreeGrid<Employee> treeGrid = new TreeGrid<>();
        treeGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        treeGrid.addClassName("standard-surface");
        treeGrid.setHeight("400px");

        treeGrid.addHierarchyColumn(emp -> emp.getFirstName() + " (" + emp.getEmployeeCode() + ")").setHeader("Employee Name").setFlexGrow(2);

        treeGrid.addColumn(emp -> {
            String roleName = emp.getUser() != null ? emp.getUser().getRole().getName() : "N/A";
            return roleName.replace("ROLE_", "").replace("_", " ");
        }).setHeader("Role").setAutoWidth(true);

        treeGrid.addColumn(emp -> {
            DailyExpectedShift expected = todayScheduleMap.get(emp.getId());
            if (expected == null || !expected.isWorkingDay()) return "Off-Day";
            Shift shift = expected.getExpectedShift();
            return shift != null ? shift.getName() : "Unknown";
        }).setHeader("Today's Shift").setAutoWidth(true);

        treeGrid.addComponentColumn(emp -> {
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

        treeGrid.addComponentColumn(emp -> createViewLeavesBtn(emp)).setHeader("Actions").setAutoWidth(true);

        List<Employee> directReportsToHod = managerToDirectReportsMap.getOrDefault(hodId, Collections.emptyList());

        Span emptyMsg = new Span("No employees found in hierarchy.");
        emptyMsg.addClassName("empty-grid-message");

        GlobalSearchComponent[] searchBoxRef = new GlobalSearchComponent[1];
        searchBoxRef[0] = new GlobalSearchComponent(searchTerm -> {
            String term = searchTerm.toLowerCase();

            // Filter the root level (direct reports)
            List<Employee> filteredRoots = directReportsToHod.stream()
                    .filter(emp -> emp.getFirstName().toLowerCase().contains(term) ||
                            emp.getEmployeeCode().toLowerCase().contains(term))
                    .toList();

            treeGrid.setItems(filteredRoots, emp -> managerToDirectReportsMap.getOrDefault(emp.getId(), Collections.emptyList()));
            treeGrid.expand(filteredRoots);

            boolean isDataEmpty = filteredRoots.isEmpty();
            treeGrid.setVisible(!isDataEmpty);
            emptyMsg.setVisible(isDataEmpty);

            if (searchBoxRef[0] != null) {
                searchBoxRef[0].hideSpinner();
            }
        });

        searchBoxRef[0].getStyle().set("margin-bottom", "var(--app-padding)");

        boolean isInitialEmpty = directReportsToHod.isEmpty();
        treeGrid.setVisible(!isInitialEmpty);
        emptyMsg.setVisible(isInitialEmpty);

        treeGrid.setItems(directReportsToHod, emp -> managerToDirectReportsMap.getOrDefault(emp.getId(), Collections.emptyList()));
        treeGrid.expand(directReportsToHod);

        section.add(title, searchBoxRef[0], treeGrid, emptyMsg);
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

        Span roleSpan = new Span("Head of " + employee.getDepartment().getName() + " | ID: " + employee.getEmployeeCode());
        roleSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        welcomeTexts.add(greeting, roleSpan);

        Button punchBtn = new Button();
        Attendance todayAtt = todayAttendanceMap.get(employee.getId());
        DailyExpectedShift todayShift = todayScheduleMap.get(employee.getId());

        boolean isOnLeave = todayShift != null && todayShift.getActiveLeave() != null;

        if (isOnLeave) {
            punchBtn.setText("On Leave Today");
            punchBtn.setIcon(VaadinIcon.UMBRELLA.create());
            punchBtn.setEnabled(false);
        } else {
            boolean isWorking = todayAtt != null && "WORKING".equals(todayAtt.getStatus().name());

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

        List<DailyExpectedShift> items = bulkShifts.getOrDefault(employee.getId(), Collections.emptyList());

        Span emptyMsg = new Span("No schedule available.");
        emptyMsg.addClassName("empty-grid-message");
        emptyMsg.setVisible(items.isEmpty());
        grid.setVisible(!items.isEmpty());

        grid.setItems(items);

        section.add(title, grid, emptyMsg);
        return section;
    }
    private Button createViewLeavesBtn(Employee emp) {
        Button btn = new Button("Leaves", VaadinIcon.EYE.create());
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        btn.addClickListener(e -> new EmployeeLeaveDialog(emp, leaveBalanceService, leaveRequestService).open());
        return btn;
    }
}
