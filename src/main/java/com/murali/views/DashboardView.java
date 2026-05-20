package com.murali.views;


import com.murali.dto.ShiftAssignmentDTO;
import com.murali.dto.TeamAttendanceSummaryDTO;
import com.murali.entity.*;
import com.murali.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@PermitAll
@PageTitle("My Dashboard")
@Route(value = "dashboard", layout = MainLayout.class)
public class DashboardView extends VerticalLayout {

    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestService leaveRequestService;
    private final SecurityService securityService;
    private final ShiftAssignmentService shiftAssignmentService;
    private final AttendanceProcessService attendanceProcessService;
    private final ApprovalRoutingService approvalRoutingService;
    private final DashboardService dashboardService;
    private final AttendanceCorrectionService attendanceCorrectionService;
    private final UserService userService;
    private final DepartmentService departmentService;
    private final RoleService roleService;
    private final AuditLogService auditLogService;
    private final AttendanceCronJobService attendanceCronJobService;

    public DashboardView(LeaveBalanceService leaveBalanceService,
                         LeaveRequestService leaveRequestService,
                         SecurityService securityService,
                         ShiftAssignmentService shiftAssignmentService,
                         AttendanceProcessService attendanceProcessService, ApprovalRoutingService approvalRoutingService, DashboardService dashboardService, AttendanceCorrectionService attendanceCorrectionService, UserService userService, DepartmentService departmentService, RoleService roleService, AuditLogService auditLogService, AttendanceCronJobService attendanceCronJobService) {
        this.leaveBalanceService = leaveBalanceService;
        this.leaveRequestService = leaveRequestService;
        this.securityService = securityService;
        this.shiftAssignmentService = shiftAssignmentService;
        this.attendanceProcessService = attendanceProcessService;
        this.approvalRoutingService = approvalRoutingService;
        this.dashboardService = dashboardService;
        this.attendanceCorrectionService = attendanceCorrectionService;
        this.userService = userService;
        this.departmentService = departmentService;
        this.roleService = roleService;
        this.auditLogService = auditLogService;
        this.attendanceCronJobService = attendanceCronJobService;

        setSizeFull();
        setSpacing(true);
        getStyle().set("overflow", "auto");
        addClassNames(LumoUtility.Padding.LARGE);

        buildUI();
    }

    private void buildUI() {
        if (securityService.hasRole("ROLE_SUPER_ADMIN")) {
            buildSuperAdminUI();
            return;
        }


        Long employeeId = securityService.getCurrentEmployeeId();
        if (employeeId == null) {
            add(new H2("No employee record linked to your user account."));
            return;
        }

        if (securityService.hasRole("ROLE_MANAGER") || securityService.hasRole("ROLE_DEPT_HEAD")) {
            add(new Hr());

            H2 managerTitle = new H2("Team Management Workspace");
            managerTitle.addClassNames(LumoUtility.Margin.Top.XLARGE, LumoUtility.Margin.Bottom.MEDIUM, LumoUtility.TextColor.PRIMARY);
            add(managerTitle);

            // Add the Manager Widgets
            add(createManagerApprovalsWidget(securityService.getCurrentUserId()));
            add(new Hr());
            add(createTeamAttendanceWidget(employeeId));
            add(new Hr());
            add(createTeamShiftsWidget(employeeId));
        }

        // --- View A: Base Employee Widgets ---
        if (securityService.hasRole("ROLE_MANAGER") || securityService.hasRole("ROLE_DEPT_HEAD")
        || securityService.hasRole("ROLE_EMPLOYEE")){
            add(new Hr());
            add(createHeaderWidget(employeeId));
            add(new Hr());
            add(createLeaveBalanceWidget(employeeId));
            add(new Hr());
            add(createUpcomingShiftsWidget(employeeId));
            add(new Hr());
            add(createMyHistoryWidget(employeeId));
        }


        // --- View B: Add Manager Workspace if applicable ---

        if (securityService.hasRole("ROLE_HR_ADMIN") || securityService.hasRole("ROLE_SUPER_ADMIN")) {
            add(new Hr());

            H2 hrTitle = new H2("HR Global Workspace");
            hrTitle.addClassNames(LumoUtility.Margin.Top.XLARGE, LumoUtility.Margin.Bottom.MEDIUM, LumoUtility.TextColor.PRIMARY);
            add(hrTitle);

            add(createHrKpiWidget());
            add(new Hr());
            add(createHrUtilizationWidget());
            add(new Hr());
            add(createHrAnomaliesWidget());
            add(new Hr());
            add(createHrMonthlyPivotWidget());
        }
    }

    // =========================================================================
    // WIDGET 1: Header (Welcome, Clock In/Out, New Leave)
    // =========================================================================
    private Component createHeaderWidget(Long employeeId) {
        String username = securityService.getAuthenticatedUser().getUsername();
        H2 welcomeText = new H2("Welcome back, " + username + "!");
        welcomeText.addClassNames(LumoUtility.Margin.NONE);

        Button newLeaveBtn = new Button("New Leave Request", VaadinIcon.PLUS.create(),
                e -> UI.getCurrent().navigate("apply-leave"));

        HorizontalLayout actions = new HorizontalLayout(createDynamicPunchButton(employeeId), newLeaveBtn);

        HorizontalLayout header = new HorizontalLayout(welcomeText, actions);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        return header;
    }

    private Component createDynamicPunchButton(Long employeeId) {
        Button punchBtn = new Button();
        Optional<Attendance> todayOpt = attendanceProcessService.getTodayAttendance(employeeId);

        boolean hasCheckedIn = todayOpt.isPresent() && todayOpt.get().getCheckIn() != null;
        boolean hasCheckedOut = todayOpt.isPresent() && todayOpt.get().getCheckOut() != null;
        boolean isOnLeave = todayOpt.isPresent() && "ON_LEAVE".equals(todayOpt.get().getStatus());

        if (isOnLeave) {
            punchBtn.setText("On Leave Today");
            punchBtn.setIcon(VaadinIcon.UMBRELLA.create());
            punchBtn.setEnabled(false);
        } else if (!hasCheckedIn) {
            punchBtn.setText("Clock In");
            punchBtn.setIcon(VaadinIcon.SIGN_IN.create());
            punchBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            punchBtn.addClickListener(e -> handlePunch(employeeId, true));
        } else if (!hasCheckedOut) {
            punchBtn.setText("Clock Out");
            punchBtn.setIcon(VaadinIcon.SIGN_OUT.create());
            punchBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            punchBtn.addClickListener(e -> handlePunch(employeeId, false));
        } else {
            punchBtn.setText("Punched for Today");
            punchBtn.setIcon(VaadinIcon.CHECK_CIRCLE.create());
            punchBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
            punchBtn.setEnabled(false);
        }

        return punchBtn;
    }

    private void handlePunch(Long employeeId, boolean isCheckIn) {
        try {
            attendanceProcessService.processDailyPunch(employeeId, LocalDateTime.now(), isCheckIn);
            Notification.show(isCheckIn ? "Clocked In Successfully!" : "Clocked Out Successfully!",
                    3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().getPage().reload();
        } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    // =========================================================================
    // WIDGET 2: Leave Balances
    // =========================================================================
    private Component createLeaveBalanceWidget(Long employeeId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Your Leave Balances");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        FlexLayout cardsLayout = new FlexLayout();
        cardsLayout.setWidthFull();
        cardsLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cardsLayout.getStyle().set("gap", "var(--lumo-space-m)");

        int currentYear = LocalDate.now().getYear();
        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(employeeId, currentYear);

        for (LeaveBalance balance : balances) {
            cardsLayout.add(createSingleBalanceCard(balance));
        }

        section.add(title, cardsLayout);
        return section;
    }

    private Component createSingleBalanceCard(LeaveBalance balance) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(LumoUtility.Background.BASE, LumoUtility.BorderRadius.LARGE,
                LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.LARGE, LumoUtility.Border.ALL);
        card.setMinWidth("220px");
        card.setMaxWidth("300px");
        card.getStyle().set("flex-grow", "1");
        card.setSpacing(false);

        BigDecimal total = balance.getTotalEntitled();
        BigDecimal remaining = leaveBalanceService.getEffectiveBalance(balance);
        double used = total.subtract(remaining).doubleValue();
        double percentUsed = (total.doubleValue() > 0) ? (used / total.doubleValue()) * 100 : 0;

        Span typeName = new Span(balance.getLeaveType().getName());
        typeName.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD);

        H2 amount = new H2(remaining + " Days Left");
        amount.addClassNames(LumoUtility.Margin.Vertical.SMALL, LumoUtility.TextColor.PRIMARY);

        Span progressBar = new Span();
        progressBar.setWidthFull();
        progressBar.setHeight("8px");
        progressBar.addClassNames(LumoUtility.Background.CONTRAST_10, LumoUtility.BorderRadius.MEDIUM);
        progressBar.getStyle().set("position", "relative").set("overflow", "hidden");

        Span progressFill = new Span();
        progressFill.setHeightFull();
        progressFill.setWidth(percentUsed + "%");
        progressFill.addClassNames(LumoUtility.Background.PRIMARY);
        progressBar.add(progressFill);

        Span stats = new Span(String.format("Used: %s / %s total", used, total));
        stats.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY, LumoUtility.Margin.Top.SMALL);

        card.add(typeName, amount, progressBar, stats);
        return card;
    }

    // =========================================================================
    // WIDGET 3: Upcoming Shifts
    // =========================================================================
    private Component createUpcomingShiftsWidget(Long employeeId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Your Upcoming Shifts (Next 7 Days)");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        Grid<ShiftAssignmentDTO> grid = new Grid<>(ShiftAssignmentDTO.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setHeight("250px");

        grid.addColumn(ShiftAssignmentDTO::getAssignmentDate).setHeader("Date").setAutoWidth(true);
        grid.addColumn(ShiftAssignmentDTO::getShiftName).setHeader("Shift").setAutoWidth(true);
        grid.addColumn(dto -> dto.getStartTime() + " - " + dto.getEndTime()).setHeader("Hours").setAutoWidth(true);
        grid.addComponentColumn(dto -> {
            if (Boolean.TRUE.equals(dto.getIsOverride())) {
                Span badge = new Span("Adjusted");
                badge.getElement().getThemeList().add("badge warning small");
                return badge;
            }
            return new Span("-");
        }).setHeader("Note").setAutoWidth(true);

        LocalDate today = LocalDate.now();
        List<ShiftAssignmentDTO> myShifts = shiftAssignmentService
                .fetchAssignmentsForCalendarPivot(today, today.plusDays(7))
                .stream()
                .filter(a -> a.getEmployeeId().equals(employeeId))
                .toList();
        grid.setItems(myShifts);

        section.add(title, grid);
        return section;
    }

    // =========================================================================
    // WIDGET 4: My History (Tabs)
    // =========================================================================
    private Component createMyHistoryWidget(Long employeeId) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        H3 title = new H3("My History");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.NONE);

        Tab leaveTab = new Tab("Leave Requests");
        Tab attendanceTab = new Tab("Attendance Records");
        Tabs tabs = new Tabs(leaveTab, attendanceTab);

        VerticalLayout leaveLayout = createLeaveHistoryTab(employeeId);
        VerticalLayout attendanceLayout = createAttendanceHistoryTab(employeeId);
        attendanceLayout.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            boolean isLeave = event.getSelectedTab().equals(leaveTab);
            leaveLayout.setVisible(isLeave);
            attendanceLayout.setVisible(!isLeave);
        });

        layout.add(title, tabs, leaveLayout, attendanceLayout);
        return layout;
    }

    private VerticalLayout createLeaveHistoryTab(Long employeeId) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setMargin(false);

        Grid<LeaveRequest> grid = new Grid<>(LeaveRequest.class, false);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setHeight("300px");

        grid.addColumn(r -> r.getLeaveType().getName()).setHeader("Type").setAutoWidth(true);
        grid.addColumn(LeaveRequest::getStartDate).setHeader("Start").setAutoWidth(true);
        grid.addColumn(LeaveRequest::getEndDate).setHeader("End").setAutoWidth(true);
        grid.addColumn(LeaveRequest::getDurationDays).setHeader("Days").setAutoWidth(true);
        grid.addComponentColumn(r -> {
            Span badge = new Span(r.getStatus());
            badge.getElement().getThemeList().add("badge");
            switch (r.getStatus().toUpperCase()) {
                case "APPROVED" -> badge.getElement().getThemeList().add("success");
                case "REJECTED", "CANCELLED" -> badge.getElement().getThemeList().add("error");
                default -> badge.getElement().getThemeList().add("warning");
            }
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        grid.setItems(leaveRequestService.getLeaveHistoryForEmployee(employeeId));
        layout.add(grid);
        return layout;
    }

    private VerticalLayout createAttendanceHistoryTab(Long employeeId) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setMargin(false);

        HorizontalLayout toolbar = new HorizontalLayout();
        DatePicker start = new DatePicker("Start", LocalDate.now().minusDays(30));
        DatePicker end = new DatePicker("End", LocalDate.now());
        toolbar.add(start, end);

        Grid<Attendance> grid = new Grid<>(Attendance.class, false);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setHeight("300px");

        grid.addColumn(Attendance::getAttendanceDate).setHeader("Date").setAutoWidth(true);
        grid.addColumn(a -> a.getCheckIn() != null ? a.getCheckIn().toLocalTime() : "-").setHeader("In").setAutoWidth(true);
        grid.addColumn(a -> a.getCheckOut() != null ? a.getCheckOut().toLocalTime() : "-").setHeader("Out").setAutoWidth(true);
        grid.addComponentColumn(a -> {
            Span badge = new Span(a.getStatus() != null ? a.getStatus() : "PENDING");
            badge.getElement().getThemeList().add("badge");
            if ("PRESENT".equals(a.getStatus())) badge.getElement().getThemeList().add("success");
            else if ("ABSENT".equals(a.getStatus())) badge.getElement().getThemeList().add("error");
            else badge.getElement().getThemeList().add("warning");
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        Runnable refresh = () -> grid.setItems(attendanceProcessService.getEmployeeAttendanceHistory(
                employeeId, start.getValue(), end.getValue()));

        start.addValueChangeListener(e -> refresh.run());
        end.addValueChangeListener(e -> refresh.run());
        refresh.run();

        layout.add(toolbar, grid);
        return layout;
    }
    // =========================================================================
    // MANAGER WIDGET 1: Pending Approvals Inbox
    // =========================================================================
    private Component createManagerApprovalsWidget(Long userId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Action Required: Pending Approvals");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        Grid<LeaveApproval> grid = new Grid<>(LeaveApproval.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setHeight("250px");

        grid.addComponentColumn(approval -> {
            Employee emp = approval.getLeaveRequest().getEmployee();
            Span name = new Span(emp.getFirstName());
            name.addClassName(LumoUtility.FontWeight.BOLD);
            Span id = new Span("ID: " + emp.getId());
            id.addClassName(LumoUtility.FontSize.XSMALL);
            VerticalLayout layout = new VerticalLayout(name, id);
            layout.setPadding(false);
            layout.setSpacing(false);
            return layout;
        }).setHeader("Employee").setAutoWidth(true);

        grid.addColumn(approval -> approval.getLeaveRequest().getLeaveType().getName()).setHeader("Type").setAutoWidth(true);
        grid.addColumn(approval -> approval.getLeaveRequest().getStartDate() + " to " + approval.getLeaveRequest().getEndDate()).setHeader("Dates").setAutoWidth(true);
        grid.addColumn(approval -> approval.getLeaveRequest().getDurationDays() + " days").setHeader("Duration").setAutoWidth(true);

        grid.addComponentColumn(approval -> {
            Button reviewBtn = new Button("Review", VaadinIcon.SEARCH.create());
            reviewBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            reviewBtn.addClickListener(e -> openReviewDialog(approval, grid));
            return reviewBtn;
        }).setHeader("Actions").setAutoWidth(true);

        List<LeaveApproval> pendingItems = approvalRoutingService.getPendingApprovalsForUser(userId);
        grid.setItems(pendingItems);

        section.add(title, grid);
        return section;
    }

    private void openReviewDialog(LeaveApproval approval, Grid<LeaveApproval> gridToRefresh) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Review Leave Request");
        dialog.setWidth("450px");

        LeaveRequest request = approval.getLeaveRequest();
        com.murali.entity.User currentUser = securityService.getAuthenticatedUser();

        // Details block
        VerticalLayout detailsLayout = new VerticalLayout();
        detailsLayout.setPadding(false);
        detailsLayout.setSpacing(false);
        detailsLayout.addClassNames(LumoUtility.Margin.Bottom.LARGE);

        detailsLayout.add(createDetailRow("Employee ID:", String.valueOf(request.getEmployee().getId())));
        detailsLayout.add(createDetailRow("Name:", request.getEmployee().getFirstName()));
        detailsLayout.add(createDetailRow("Leave Type:", request.getLeaveType().getName()));
        detailsLayout.add(createDetailRow("Dates:", request.getStartDate() + " to " + request.getEndDate()));
        detailsLayout.add(createDetailRow("Duration:", request.getDurationDays() + " days"));

        com.vaadin.flow.component.textfield.TextArea reasonDisplay = new com.vaadin.flow.component.textfield.TextArea("Employee Reason");
        reasonDisplay.setValue(request.getReason() != null ? request.getReason() : "");
        reasonDisplay.setReadOnly(true);
        reasonDisplay.setWidthFull();

        com.vaadin.flow.component.textfield.TextArea commentsArea = new com.vaadin.flow.component.textfield.TextArea("Approver Feedback");
        commentsArea.setPlaceholder("Required if rejecting...");
        commentsArea.setWidthFull();

        // Buttons
        Button approveBtn = new Button("Approve", VaadinIcon.CHECK.create());
        approveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        approveBtn.addClickListener(e -> {
            processAction(approval, "APPROVED", commentsArea.getValue(), dialog, gridToRefresh);
        });

        Button rejectBtn = new Button("Reject", VaadinIcon.CLOSE.create());
        rejectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        rejectBtn.addClickListener(e -> {
            processAction(approval, "REJECTED", commentsArea.getValue(), dialog, gridToRefresh);
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footerLayout = new HorizontalLayout(cancelBtn, rejectBtn, approveBtn);
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footerLayout.setWidthFull();

        dialog.add(detailsLayout, reasonDisplay, commentsArea);
        dialog.getFooter().add(footerLayout);
        dialog.open();
    }

    private HorizontalLayout createDetailRow(String labelText, String valueText) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        Span label = new Span(labelText);
        label.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD, LumoUtility.FontSize.SMALL);
        Span value = new Span(valueText);
        value.addClassNames(LumoUtility.FontSize.SMALL);
        row.add(label, value);
        return row;
    }

    private void processAction(LeaveApproval approval, String action, String comments, Dialog dialog, Grid<LeaveApproval> grid) {
        try {
            com.murali.entity.User currentUser = securityService.getAuthenticatedUser();
            approvalRoutingService.processApprovalAction(approval.getId(), action, comments, currentUser);

            Notification.show("Request " + action.toLowerCase() + " successfully", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(action.equals("APPROVED") ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_ERROR);
            dialog.close();
            // Refresh Grid data
            grid.setItems(approvalRoutingService.getPendingApprovalsForUser(currentUser.getId()));
        } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    // =========================================================================
    // MANAGER WIDGET 2: Today's Team Attendance Summary
    // =========================================================================
    private Component createTeamAttendanceWidget(Long managerId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        H3 title = new H3("Today's Team Attendance Overview");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        TeamAttendanceSummaryDTO summary = attendanceProcessService.getTodayTeamAttendanceSummary(managerId);

        HorizontalLayout summaryCards = new HorizontalLayout();
        summaryCards.setWidthFull();

        summaryCards.add(
                createStatCard("Present", summary.getPresentCount(), "var(--lumo-success-color)"),
                createStatCard("Late", summary.getLateCount(), "var(--lumo-warning-color)"),
                createStatCard("Absent", summary.getAbsentCount(), "var(--lumo-error-color)")
        );

        Grid<Attendance> teamGrid = new Grid<>(Attendance.class, false);
        teamGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        teamGrid.setHeight("250px");

        teamGrid.addColumn(a -> a.getEmployee().getFirstName()).setHeader("Employee").setAutoWidth(true);
        teamGrid.addColumn(a -> a.getCheckIn() != null ? a.getCheckIn().toLocalTime().toString() : "-").setHeader("Clock In").setAutoWidth(true);
        teamGrid.addComponentColumn(a -> {
            Span badge = new Span(a.getStatus() != null ? a.getStatus() : "PENDING");
            badge.getElement().getThemeList().add("badge");
            if ("PRESENT".equals(a.getStatus())) badge.getElement().getThemeList().add("success");
            else if ("ABSENT".equals(a.getStatus())) badge.getElement().getThemeList().add("error");
            else badge.getElement().getThemeList().add("warning");
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        teamGrid.setItems(attendanceProcessService.getTodayTeamAttendanceDetails(managerId));

        section.add(title, summaryCards, teamGrid);
        return section;
    }

    private Component createStatCard(String title, int count, String color) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(LumoUtility.Background.BASE, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM, LumoUtility.Border.ALL);
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setSpacing(false);
        card.getStyle().set("flex-grow", "1");

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD);

        H2 countHeading = new H2(String.valueOf(count));
        countHeading.addClassNames(LumoUtility.Margin.NONE);
        countHeading.getStyle().set("color", color);

        card.add(titleSpan, countHeading);
        return card;
    }

    // =========================================================================
    // MANAGER WIDGET 3: Team Shifts
    // =========================================================================
    private Component createTeamShiftsWidget(Long managerId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Team Schedule (Next 7 Days)");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        Grid<ShiftAssignmentDTO> grid = new Grid<>(ShiftAssignmentDTO.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setHeight("250px");

        grid.addColumn(ShiftAssignmentDTO::getEmployeeName).setHeader("Employee").setAutoWidth(true);
        grid.addColumn(ShiftAssignmentDTO::getAssignmentDate).setHeader("Date").setAutoWidth(true);
        grid.addColumn(ShiftAssignmentDTO::getShiftName).setHeader("Shift").setAutoWidth(true);
        grid.addColumn(dto -> dto.getStartTime() + " - " + dto.getEndTime()).setHeader("Hours").setAutoWidth(true);

        LocalDate today = LocalDate.now();
        grid.setItems(shiftAssignmentService.getTeamUpcomingShifts(managerId, today, today.plusDays(7)));

        section.add(title, grid);
        return section;
    }
    // =========================================================================
    // HR WIDGET 1: Global KPIs
    // =========================================================================
    private Component createHrKpiWidget() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();

        String leavesToday = dashboardService.getTodayAbsenceCount();
        String pendingApprovals = dashboardService.getTotalPendingCount();
        long pendingAnomalies = attendanceCorrectionService.getGlobalPendingCorrectionsCount();

        layout.add(
                createStatCard("Total on Leave Today", Integer.parseInt(leavesToday), "var(--lumo-primary-color)"),
                createStatCard("Global Pending Approvals", Integer.parseInt(pendingApprovals), "var(--lumo-warning-color)"),
                createStatCard("Pending Anomalies", (int) pendingAnomalies, "var(--lumo-error-color)")
        );
        return layout;
    }

    // =========================================================================
    // HR WIDGET 2: Global Leave Utilization
    // =========================================================================
    private Component createHrUtilizationWidget() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        H3 title = new H3("Company Leave Utilization (" + LocalDate.now().getYear() + ")");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        Map<String, BigDecimal> data = dashboardService.getGlobalLeaveUtilization(LocalDate.now().getYear());
        double total = data.get("total").doubleValue();
        double used = data.get("used").doubleValue();
        double percent = total > 0 ? (used / total) * 100 : 0;

        Span progressBar = new Span();
        progressBar.setWidthFull();
        progressBar.setHeight("20px");
        progressBar.addClassNames(LumoUtility.Background.CONTRAST_10, LumoUtility.BorderRadius.LARGE);
        progressBar.getStyle().set("position", "relative").set("overflow", "hidden");

        Span progressFill = new Span();
        progressFill.setHeightFull();
        progressFill.setWidth(percent + "%");
        progressFill.addClassNames(LumoUtility.Background.PRIMARY);
        progressBar.add(progressFill);

        HorizontalLayout stats = new HorizontalLayout(
                new Span(String.format("Used: %.1f Days", used)),
                new Span(String.format("Total Allocated: %.1f Days", total)),
                new Span(String.format("%.1f%% Burn Rate", percent))
        );
        stats.setWidthFull();
        stats.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        stats.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD);

        layout.add(title, progressBar, stats);
        return layout;
    }

    // =========================================================================
    // HR WIDGET 3: Attendance Anomalies (Corrections) Grid
    // =========================================================================
    private Component createHrAnomaliesWidget() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Global Attendance Anomalies (Missing Check-outs)");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        Grid<com.murali.entity.AttendanceCorrection> grid = new Grid<>(com.murali.entity.AttendanceCorrection.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setHeight("250px");

        grid.addColumn(ac -> ac.getAttendance().getEmployee().getFirstName() + " (ID: " + ac.getAttendance().getEmployee().getId() + ")")
                .setHeader("Employee").setAutoWidth(true);
        grid.addColumn(ac -> ac.getAttendance().getAttendanceDate()).setHeader("Date").setAutoWidth(true);
        grid.addColumn(ac -> ac.getApprover().getUsername()).setHeader("Assigned Manager").setAutoWidth(true);
        grid.addComponentColumn(ac -> {
            Span badge = new Span(ac.getStatus());
            badge.getElement().getThemeList().add("badge error");
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        grid.setItems(attendanceCorrectionService.getAllPendingCorrectionsGlobally());

        section.add(title, grid);
        return section;
    }

    // =========================================================================
    // HR WIDGET 4: Monthly Shift Pivot
    // =========================================================================
    private Component createHrMonthlyPivotWidget() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        LocalDate today = LocalDate.now();
        H3 title = new H3("Monthly Shift Overview (" + today.getMonth().name() + ")");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        Grid<com.murali.dto.MonthlyPivotRowDTO> monthlyGrid = new Grid<>(com.murali.dto.MonthlyPivotRowDTO.class, false);
        monthlyGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        monthlyGrid.setHeight("400px");

        monthlyGrid.addColumn(com.murali.dto.MonthlyPivotRowDTO::getEmployeeName)
                .setHeader("Employee")
                .setFrozen(true)
                .setWidth("180px")
                .setFlexGrow(0);

        int daysInMonth = today.lengthOfMonth();
        for (int i = 1; i <= daysInMonth; i++) {
            final int day = i;
            monthlyGrid.addComponentColumn(row -> {
                com.murali.dto.ShiftAssignmentDTO assignment = row.getAssignmentForDay(day);
                if (assignment != null && assignment.getShiftName() != null) {
                    String shortCode = assignment.getShiftName().length() > 4
                            ? assignment.getShiftName().substring(0, 4)
                            : assignment.getShiftName();
                    Span badge = new Span(shortCode);
                    badge.getElement().getThemeList().add("badge small contrast");
                    return badge;
                }
                return new Span("-");
            }).setHeader(String.valueOf(day)).setAutoWidth(true);
        }

        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(daysInMonth);

        List<com.murali.dto.ShiftAssignmentDTO> flatAssignments = shiftAssignmentService.fetchAssignmentsForCalendarPivot(startOfMonth, endOfMonth);
        java.util.Map<String, com.murali.dto.MonthlyPivotRowDTO> pivotData = new java.util.HashMap<>();

        for (com.murali.dto.ShiftAssignmentDTO dto : flatAssignments) {
            com.murali.dto.MonthlyPivotRowDTO row = pivotData.computeIfAbsent(
                    dto.getEmployeeName(),
                    k -> new com.murali.dto.MonthlyPivotRowDTO(dto.getEmployeeName())
            );
            row.addShift(dto.getAssignmentDate().getDayOfMonth(), dto);
        }

        monthlyGrid.setItems(pivotData.values());
        section.add(title, monthlyGrid);
        return section;
    }
    // =========================================================================
    // SUPER ADMIN ORCHESTRATOR
    // =========================================================================
    private void buildSuperAdminUI() {
        HorizontalLayout header = new HorizontalLayout(
                new H2("System Administrator Console")
        );
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        add(header, new Hr());
        add(createSuperAdminKpis());
        add(new Hr());
        add(createSystemStatusWidget());
        add(new Hr());

        // Use a HorizontalLayout to put Audit Logs and Quick Links side-by-side
        HorizontalLayout bottomRow = new HorizontalLayout();
        bottomRow.setWidthFull();

        Component logs = createAuditLogWidget();
        Component nav = createQuickNavWidget();

        bottomRow.add(logs, nav);
        bottomRow.setFlexGrow(2, logs);
        bottomRow.setFlexGrow(1, nav);

        add(bottomRow);
    }

    // =========================================================================
    // SUPER ADMIN WIDGET 1: System KPIs
    // =========================================================================
    private Component createSuperAdminKpis() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();

        long activeUsers = userService.getActiveUsersCount();
        long totalDepts = departmentService.findAll().size();
        long totalRoles = roleService.findAll().size();

        layout.add(
                createStatCard("Active Users", (int) activeUsers, "var(--lumo-primary-color)"),
                createStatCard("Configured Depts", (int) totalDepts, "var(--lumo-contrast-color)"),
                createStatCard("System Roles", (int) totalRoles, "var(--lumo-contrast-color)")
        );
        return layout;
    }

    // =========================================================================
    // SUPER ADMIN WIDGET 2: System Status (Cron & Sync)
    // =========================================================================
    private Component createSystemStatusWidget() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        H3 title = new H3("System Health Monitors");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        HorizontalLayout monitors = new HorizontalLayout();
        monitors.setWidthFull();

        // Monitor 1: Cron Job
        VerticalLayout cronMonitor = new VerticalLayout(
                new Span(VaadinIcon.AUTOMATION.create(), new Span(" Attendance Cron Engine")),
                new Span("Status: " + attendanceCronJobService.getLastRunStatus()),
                new Span("Last Run: " + (attendanceCronJobService.getLastRunTime() != null ? attendanceCronJobService.getLastRunTime().toString() : "N/A"))
        );
        styleMonitorCard(cronMonitor, attendanceCronJobService.getLastRunStatus());

        // Monitor 2: Sync Service (Assuming it's injected or passed through DashboardService)
//        VerticalLayout syncMonitor = new VerticalLayout(
//                new Span(VaadinIcon.DATABASE.create(), new Span(" Database Sync Layer")),
//                new Span("Status: " + dashboardService.getSyncStatus()), // ADD THIS TO DashboardService
//                new Span("Last Sync: " + (dashboardService.getLastSyncTime() != null ? dashboardService.getLastSyncTime().toString() : "N/A")) // ADD THIS TO DashboardService
//        );
//        styleMonitorCard(syncMonitor, dashboardService.getSyncStatus());

        monitors.add(cronMonitor);
        layout.add(title, monitors);
        return layout;
    }

    private void styleMonitorCard(VerticalLayout card, String status) {
        card.addClassNames(LumoUtility.Background.BASE, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM, LumoUtility.Border.ALL);
        card.setSpacing(false);
        card.getStyle().set("flex-grow", "1");

        // Make the first child (the title) bold
        card.getComponentAt(0).addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.Margin.Bottom.SMALL);

        if ("SUCCESS".equals(status) || "SYNCED".equals(status)) {
            card.getStyle().set("border-left", "4px solid var(--lumo-success-color)");
        } else {
            card.getStyle().set("border-left", "4px solid var(--lumo-error-color)");
        }
    }

    // =========================================================================
    // SUPER ADMIN WIDGET 3: Audit Logs
    // =========================================================================
    private Component createAuditLogWidget() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Recent System Activity");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        Grid<com.murali.entity.AuditLog> grid = new Grid<>(com.murali.entity.AuditLog.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setHeight("350px");

        grid.addColumn(log -> log.getTimestamp() != null ? log.getTimestamp().toLocalTime() : "").setHeader("Time").setAutoWidth(true);
        grid.addColumn(com.murali.entity.AuditLog::getUsername).setHeader("User").setAutoWidth(true);
        grid.addColumn(com.murali.entity.AuditLog::getAction).setHeader("Action").setAutoWidth(true);
        grid.addColumn(com.murali.entity.AuditLog::getDetails).setHeader("Details").setAutoWidth(true).setFlexGrow(1);

        grid.setItems(auditLogService.getRecentLogs(50));

        section.add(title, grid);
        return section;
    }

    // =========================================================================
    // SUPER ADMIN WIDGET 4: Quick Navigation
    // =========================================================================
    private Component createQuickNavWidget() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Configuration Shortcuts");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        FlexLayout shortcuts = new FlexLayout();
        shortcuts.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        shortcuts.getStyle().set("gap", "var(--lumo-space-m)");

        shortcuts.add(
                createNavButton("System Config", VaadinIcon.COG, "admin-config"),
                createNavButton("Leave Types", VaadinIcon.CALENDAR_USER, "add-leave-types"),
                createNavButton("Manage Roles", VaadinIcon.SAFE, "add-role"),
                createNavButton("Departments", VaadinIcon.BUILDING, "add-departments"),
                createNavButton("Users", VaadinIcon.USER, "add-user")
        );

        section.add(title, shortcuts);
        return section;
    }

    private Button createNavButton(String text, VaadinIcon icon, String route) {
        Button btn = new Button(text, icon.create());
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_LARGE);
        btn.setWidth("100%");
        btn.getStyle().set("justify-content", "flex-start");
        btn.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(route)));
        return btn;
    }
}
