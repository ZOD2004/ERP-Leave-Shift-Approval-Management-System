package com.murali.views.dashboard;

import com.murali.dto.DailyExpectedShift;
import com.murali.dto.TeamAttendanceSummaryDTO;
import com.murali.entity.Attendance;
import com.murali.entity.Employee;
import com.murali.entity.LeaveApproval;
import com.murali.entity.Shift;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.service.*;
import com.murali.util.SecurityService;
import com.murali.views.components.EmptyStateComponent;
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
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@SpringComponent
@UIScope
public class ManagerWorkspace extends VerticalLayout {

    private final SecurityService securityService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceProcessService attendanceProcessService;
    private final ScheduleCalculationService scheduleCalculationService;
    private final ApprovalRoutingService approvalRoutingService;
    private final AttendanceRepository attendanceRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestService leaveRequestService;

    private Map<Long, Attendance> todayAttendanceMap = new HashMap<>();
    private Map<Long, DailyExpectedShift> todayScheduleMap = new HashMap<>();

    public ManagerWorkspace(SecurityService securityService,
                            EmployeeRepository employeeRepository,
                            AttendanceProcessService attendanceProcessService,
                            ScheduleCalculationService scheduleCalculationService,
                            ApprovalRoutingService approvalRoutingService,
                            AttendanceRepository attendanceRepository, LeaveBalanceService leaveBalanceService, LeaveRequestService leaveRequestService) {
        this.securityService = securityService;
        this.employeeRepository = employeeRepository;
        this.attendanceProcessService = attendanceProcessService;
        this.scheduleCalculationService = scheduleCalculationService;
        this.approvalRoutingService = approvalRoutingService;
        this.attendanceRepository = attendanceRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.leaveRequestService = leaveRequestService;

        setPadding(false);
        setSpacing(true);
        setWidthFull();
        getStyle().set("gap", "var(--app-layout-margin)"); // Applies consistent global block spacing

        buildUI();
    }

    private void buildUI() {
        Long currentUserId = securityService.getCurrentUserId();
        Employee manager = employeeRepository.findByUserId(currentUserId).orElse(null);

        if (manager == null) {
            add(new H2("Error: No Employee record linked to your user account."));
            return;
        }

        List<Employee> directReports = employeeRepository.findReportingEmployees(manager.getId());
        loadBulkDailyData(directReports);

        // 1. Personal Section
        add(createPersonalHeader(manager));
        // Removed unnecessary spacer Hr tag

        // 2. Team Section
        H3 teamHeader = new H3("My Team Overview");
        teamHeader.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.MEDIUM, LumoUtility.TextColor.PRIMARY);
        add(teamHeader);

        add(createTeamKpiSection(manager.getId(), currentUserId));

        HorizontalLayout gridsLayout = new HorizontalLayout();
        gridsLayout.setWidthFull();
        gridsLayout.setAlignItems(FlexComponent.Alignment.START);

        Component teamGrid = createTeamLiveStatusGrid(directReports);
        Component actionGrid = createActionRequiredWidget(currentUserId);
        Component scheduleGrid = createWeeklyScheduleWidget(manager);

        gridsLayout.add(scheduleGrid, teamGrid, actionGrid);
        gridsLayout.setFlexGrow(1, scheduleGrid);
        gridsLayout.setFlexGrow(2, teamGrid);
        gridsLayout.setFlexGrow(1, actionGrid);

        add(gridsLayout);
    }

    private Component createPersonalHeader(Employee manager) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout welcomeTexts = new VerticalLayout();
        welcomeTexts.setPadding(false);
        welcomeTexts.setSpacing(false);

        H2 greeting = new H2("Welcome back, " + manager.getFirstName() + "!");
        greeting.addClassNames(LumoUtility.Margin.NONE);

        Span roleSpan = new Span(manager.getDepartment() != null ? manager.getDepartment().getName() + " Manager" : "Manager");
        roleSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        welcomeTexts.add(greeting, roleSpan);

        // Dynamic Punch Button
        Button punchBtn = new Button();
        Optional<Attendance> todayOpt = attendanceProcessService.getTodayAttendance(manager.getId());

        boolean isOnLeave = todayOpt.isPresent() &&
                ("ON_LEAVE".equals(todayOpt.get().getStatus().name()) || "FULL_LEAVE".equals(todayOpt.get().getStatus().name()));

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
                punchBtn.addClickListener(e -> handlePunch(manager.getId(), true));
            } else {
                punchBtn.setText("Clock Out");
                punchBtn.setIcon(VaadinIcon.SIGN_OUT.create());
                punchBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
                punchBtn.addClickListener(e -> handlePunch(manager.getId(), false));
            }
        }

        header.add(welcomeTexts, punchBtn);
        return header;
    }

    private void handlePunch(Long employeeId, boolean isCheckIn) {
        try {
            attendanceProcessService.processDailyPunch(employeeId, LocalDateTime.now(), isCheckIn);
            Notification.show(isCheckIn ? "Clocked In Successfully!" : "Clocked Out Successfully!", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().getPage().reload();
        } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void loadBulkDailyData(List<Employee> team) {
        if (team.isEmpty()) return;
        LocalDate today = LocalDate.now();
        List<Long> empIds = team.stream().map(Employee::getId).toList();

        Map<Long, List<DailyExpectedShift>> batchSchedules = scheduleCalculationService.calculateBatchShifts(team, today, today);
        batchSchedules.forEach((empId, shifts) -> {
            if (!shifts.isEmpty()) todayScheduleMap.put(empId, shifts.get(0));
        });

        List<Attendance> attendances = attendanceRepository.findByEmployeeIdsAndAttendanceDate(empIds, today);
        attendances.forEach(a -> todayAttendanceMap.put(a.getEmployee().getId(), a));
    }

    private Component createTeamKpiSection(Long managerEmployeeId, Long managerUserId) {
        HorizontalLayout kpiLayout = new HorizontalLayout();
        kpiLayout.setWidthFull();
        kpiLayout.setSpacing(false);
        kpiLayout.getStyle().set("gap", "var(--app-padding)");
        // This ensures the cards stay horizontal and scroll sideways on very tiny mobile screens
        // rather than stacking vertically and taking up page height.
        kpiLayout.getStyle().set("overflow-x", "auto");
        kpiLayout.getStyle().set("padding-bottom", "8px"); // breathe room for potential scrollbar

        TeamAttendanceSummaryDTO summary = attendanceProcessService.getTodayTeamAttendanceSummary(managerEmployeeId);
        int pendingApprovals = approvalRoutingService.getPendingApprovalsForUser(managerUserId).size();

        kpiLayout.add(
                createStatCard("Present Today", String.valueOf(summary.getPresentCount()), VaadinIcon.CHECK_CIRCLE, "#24a148"),
                createStatCard("Yet to Check-in", String.valueOf(summary.getExpectedCount()), VaadinIcon.CLOCK, "#f1c21b"),
                createStatCard("On Leave / Off", String.valueOf(summary.getAbsentOrLeaveCount()), VaadinIcon.FLIGHT_TAKEOFF, "var(--app-text-secondary)"),
                createStatCard("Pending Approvals", String.valueOf(pendingApprovals), VaadinIcon.INBOX, "#da1e28")
        );

        return kpiLayout;
    }

    private Component createStatCard(String title, String value, VaadinIcon iconEnum, String iconColor) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames("standard-surface", "hoverable");
        card.setSpacing(false);
        card.setMinWidth("150px");
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

    private Component createTeamLiveStatusGrid(List<Employee> directReports) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Live Status");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);

        Grid<Employee> grid = new Grid<>(Employee.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.addClassName("standard-surface");
        grid.setHeight("300px");

        grid.addColumn(Employee::getFirstName).setHeader("Employee").setAutoWidth(true);

        grid.addColumn(emp -> {
            DailyExpectedShift expected = todayScheduleMap.get(emp.getId());
            if (expected == null || !expected.isWorkingDay()) return "Off-Day";
            Shift shift = expected.getExpectedShift();
            return shift != null ? shift.getStartTime() + " - " + shift.getEndTime() : "Unknown";
        }).setHeader("Schedule").setAutoWidth(true);

        grid.addComponentColumn(emp -> {
            Attendance att = todayAttendanceMap.get(emp.getId());
            DailyExpectedShift expected = todayScheduleMap.get(emp.getId());

            Span badge = new Span();
            badge.getElement().getThemeList().add("badge small");

            if (att != null && att.getFirstCheckIn() != null) {
                badge.setText(att.getFirstCheckIn().toLocalTime().toString());
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
        }).setHeader("Status").setAutoWidth(true);

        grid.addComponentColumn(emp -> createViewLeavesBtn(emp)).setHeader("Actions").setAutoWidth(true);

        EmptyStateComponent emptyState = new EmptyStateComponent(VaadinIcon.USERS);

        GlobalSearchComponent[] searchBoxRef = new GlobalSearchComponent[1];
        searchBoxRef[0] = new GlobalSearchComponent(searchTerm -> {
            String term = searchTerm.toLowerCase();
            List<Employee> filtered = directReports.stream()
                    .filter(emp -> emp.getFirstName().toLowerCase().contains(term) ||
                            (emp.getEmployeeCode() != null && emp.getEmployeeCode().toLowerCase().contains(term)))
                    .toList();

            grid.setItems(filtered);
            boolean isEmpty = filtered.isEmpty();

            if (isEmpty) {
                if (term.isBlank()) {
                    emptyState.setMessage("No Team Members", "There are no employees currently reporting to you.");
                } else {
                    emptyState.setMessage("No results found", "No team members match the search term: \"" + term + "\"");
                }
            }

            grid.setVisible(!isEmpty);
            emptyState.setVisible(isEmpty);

            if (searchBoxRef[0] != null) {
                searchBoxRef[0].hideSpinner();
            }
        });
        searchBoxRef[0].getStyle().set("margin-bottom", "var(--app-padding)");

        boolean isEmpty = directReports.isEmpty();
        grid.setItems(directReports);
        if (isEmpty) {
            emptyState.setMessage("No Team Members", "There are no employees currently reporting to you.");
        }
        grid.setVisible(!isEmpty);
        emptyState.setVisible(isEmpty);

        section.add(title, searchBoxRef[0], grid, emptyState);
        return section;
    }

    private Component createActionRequiredWidget(Long userId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.setWidthFull();
        titleRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 title = new H3("Action Required");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);

        Button viewAllBtn = new Button("View Inbox");
        viewAllBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        viewAllBtn.addClickListener(e -> UI.getCurrent().navigate("approvals")); // Routes to your Approval Inbox

        titleRow.add(title, viewAllBtn);

        Grid<LeaveApproval> grid = new Grid<>(LeaveApproval.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.addClassName("standard-surface");
        grid.setHeight("300px");

        grid.addColumn(approval -> approval.getLeaveRequest().getEmployee().getFirstName())
                .setHeader("Employee").setAutoWidth(true);
        grid.addColumn(approval -> approval.getLeaveRequest().getLeaveType().getCode())
                .setHeader("Type").setAutoWidth(true);
        grid.addColumn(approval -> approval.getLeaveRequest().getDurationDays() + " Days")
                .setHeader("Duration").setAutoWidth(true);

        List<LeaveApproval> pendingItems = approvalRoutingService.getPendingApprovalsForUser(userId);

        EmptyStateComponent emptyState = new EmptyStateComponent(VaadinIcon.INBOX);

        GlobalSearchComponent[] searchBoxRef = new GlobalSearchComponent[1];
        searchBoxRef[0] = new GlobalSearchComponent(searchTerm -> {
            String term = searchTerm.toLowerCase();
            // Search across ALL pending items, not just the top 5
            List<LeaveApproval> filtered = pendingItems.stream()
                    .filter(approval -> approval.getLeaveRequest().getEmployee().getFirstName().toLowerCase().contains(term) ||
                            approval.getLeaveRequest().getLeaveType().getCode().toLowerCase().contains(term))
                    .toList();

            grid.setItems(filtered);
            boolean isEmpty = filtered.isEmpty();

            if (isEmpty) {
                if (term.isBlank()) {
                    emptyState.setMessage("All Caught Up", "You have no pending approvals at the moment.");
                } else {
                    emptyState.setMessage("No results found", "No pending actions match the search term: \"" + term + "\"");
                }
            }

            grid.setVisible(!isEmpty);
            emptyState.setVisible(isEmpty);

            if (searchBoxRef[0] != null) {
                searchBoxRef[0].hideSpinner();
            }
        });
        searchBoxRef[0].getStyle().set("margin-bottom", "var(--app-padding)");

        boolean isEmpty = pendingItems.isEmpty();
        if (!isEmpty) {
            // Default view keeps it clean with a max of 5 items
            grid.setItems(pendingItems.size() > 5 ? pendingItems.subList(0, 5) : pendingItems);
        }
        if (isEmpty) {
            emptyState.setMessage("All Caught Up", "You have no pending approvals at the moment.");
        }
        grid.setVisible(!isEmpty);
        emptyState.setVisible(isEmpty);

        section.add(titleRow, searchBoxRef[0], grid, emptyState);
        return section;
    }
    private Component createWeeklyScheduleWidget(Employee employee) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("My Weekly Schedule");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);

        Grid<DailyExpectedShift> grid = new Grid<>(DailyExpectedShift.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.addClassName("standard-surface");
        grid.setHeight("300px");

        grid.addColumn(shift -> shift.getTargetDate().toString() + " (" + shift.getTargetDate().getDayOfWeek().name().substring(0, 3) + ")")
                .setHeader("Date").setAutoWidth(true);

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

        EmptyStateComponent emptyState = new EmptyStateComponent(VaadinIcon.CALENDAR);
        emptyState.setMessage("No Schedule", "You have no scheduled shifts for the upcoming week.");
        emptyState.setVisible(items.isEmpty());
        grid.setVisible(!items.isEmpty());

        grid.setItems(items);

        section.add(title, grid, emptyState);
        return section;
    }
    private Button createViewLeavesBtn(Employee emp) {
        Button btn = new Button("Leaves", VaadinIcon.EYE.create());
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        btn.addClickListener(e -> new EmployeeLeaveDialog(emp, leaveBalanceService, leaveRequestService).open());
        return btn;
    }
}
