package com.murali.views;

import com.murali.entity.Attendance;
import com.murali.entity.LeaveBalance;
import com.murali.entity.LeaveRequest;
import com.murali.dto.ShiftAssignmentDTO;
import com.murali.dto.TeamAttendanceSummaryDTO;
import com.murali.service.*;
import com.murali.views.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import java.util.Optional;

@PermitAll
@PageTitle("My Dashboard")
@Route(value = "dashboard", layout = MainLayout.class)
public class EmployeeDashboardView extends VerticalLayout {

    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestService leaveRequestService;
    private final SecurityService securityService;
    private final ShiftAssignmentService shiftAssignmentService;
    private final AttendanceProcessService attendanceProcessService; // Injected for Punches and History

    public EmployeeDashboardView(LeaveBalanceService leaveBalanceService,
                                 LeaveRequestService leaveRequestService,
                                 SecurityService securityService,
                                 ShiftAssignmentService shiftAssignmentService,
                                 AttendanceProcessService attendanceProcessService) {
        this.leaveBalanceService = leaveBalanceService;
        this.leaveRequestService = leaveRequestService;
        this.securityService = securityService;
        this.shiftAssignmentService = shiftAssignmentService;
        this.attendanceProcessService = attendanceProcessService;

        setSizeFull();
        setSpacing(true);
        getStyle().set("overflow", "auto");

        addClassNames(LumoUtility.Padding.LARGE);

        buildUI();
    }

    private void buildUI() {
        Long employeeId = securityService.getCurrentEmployeeId();
        if (employeeId == null) {
            return;
        }

        HorizontalLayout header = new HorizontalLayout(
                new H2("Welcome back, " + securityService.getAuthenticatedUser().getUsername() + "!"),
                new HorizontalLayout(
                        createDynamicPunchButton(employeeId),
                        new Button("New Leave Request", VaadinIcon.PLUS.create(),
                                e -> getUI().ifPresent(ui -> ui.navigate("apply-leave")))
                ));

        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Component balanceSection = createBalanceSection(employeeId);
        Component shiftSection = createShiftSection(employeeId);
        Component historySection = createHistorySection(employeeId);

        // --- UPDATED LAYOUT ORDER ---

        // 1. Header first
        add(header, new Hr());

        // 2. Leave Balance directly after the header
        add(balanceSection);

        // 3. Role-Based Manager Dashboard Injection next
        if (isManagerialRole()) {
            add(new Hr(), createTeamAttendanceSection(employeeId));
        }

        // 4. Remaining sections follow
        add(new Hr(), shiftSection, new Hr(), historySection);
    }

    // --- 1. Dynamic Punch Button ---

    private Component createDynamicPunchButton(Long employeeId) {
        Button punchBtn = new Button();
        Optional<Attendance> todayOpt = attendanceProcessService.getTodayAttendance(employeeId);

        boolean hasCheckedIn = todayOpt.isPresent() && todayOpt.get().getCheckIn() != null;
        boolean hasCheckedOut = todayOpt.isPresent() && todayOpt.get().getCheckOut() != null;

        if (!hasCheckedIn) {
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
            punchBtn.setEnabled(false); // Already done for the day
        }

        return punchBtn;
    }

    private void handlePunch(Long employeeId, boolean isCheckIn) {
        try {
            attendanceProcessService.processDailyPunch(employeeId, LocalDateTime.now(), isCheckIn);
            Notification.show(isCheckIn ? "Clocked In Successfully!" : "Clocked Out Successfully!",
                    3000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            // Reload view to update button state and grids
            getUI().ifPresent(ui -> ui.getPage().reload());
        } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    // --- 2. Existing Balance & Shift Sections ---

    private Component createBalanceSection(Long employeeId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        H3 title = new H3("Your Leave Balances");
        title.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.NONE);

        FlexLayout cards = createBalanceCards(employeeId, LocalDate.now().getYear());

        section.add(title, cards);
        return section;
    }

    private FlexLayout createBalanceCards(Long employeeId, int year) {
        FlexLayout cardsLayout = new FlexLayout();
        cardsLayout.setWidthFull();
        cardsLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cardsLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        cardsLayout.setAlignItems(FlexComponent.Alignment.START);
        cardsLayout.getStyle().set("gap", "var(--lumo-space-m)");

        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(employeeId, year);
        for (LeaveBalance balance : balances) {
            cardsLayout.add(createSingleCard(balance));
        }
        return cardsLayout;
    }

    private Component createSingleCard(LeaveBalance balance) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(LumoUtility.Background.BASE, LumoUtility.BorderRadius.LARGE,
                LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.LARGE, LumoUtility.Border.ALL);
        card.setMinWidth("240px");          // Use Min Width instead of fixed Width
        card.setMaxWidth("320px");
        card.getStyle().set("flex-grow", "1"); // Allow it to grow to fill space evenly
        card.getStyle().set("box-sizing", "border-box"); // Prevents padding from causing overflow
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

    private Component createShiftSection(Long employeeId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        H3 title = new H3("Your Upcoming Shift Schedule");
        title.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.NONE);

        Grid<ShiftAssignmentDTO> shiftGrid = new Grid<>(ShiftAssignmentDTO.class, false);
        shiftGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        shiftGrid.setWidthFull();
        shiftGrid.setHeight("200px");

        shiftGrid.addColumn(ShiftAssignmentDTO::getAssignmentDate).setHeader("Date").setAutoWidth(true).setSortable(true);
        shiftGrid.addColumn(ShiftAssignmentDTO::getShiftName).setHeader("Shift Template").setAutoWidth(true);
        shiftGrid.addColumn(dto -> dto.getStartTime() + " - " + dto.getEndTime()).setHeader("Working Hours").setAutoWidth(true);
        shiftGrid.addComponentColumn(dto -> {
            if (Boolean.TRUE.equals(dto.getIsOverride())) {
                Span badge = new Span("Adjusted");
                badge.getElement().getThemeList().add("badge warning small");
                return badge;
            }
            return new Span("-");
        }).setHeader("Schedule Type").setAutoWidth(true);

        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);
        List<ShiftAssignmentDTO> myShifts = shiftAssignmentService.fetchAssignmentsForCalendarPivot(today, nextWeek)
                .stream().filter(assignment -> assignment.getEmployeeId().equals(employeeId)).toList();
        shiftGrid.setItems(myShifts);

        section.add(title, shiftGrid);
        return section;
    }

    // --- 3. History Section with Tabs ---

    private Component createHistorySection(Long employeeId) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setWidthFull();
        // Give the overall history component room to display its grids safely
        layout.setMinHeight("400px");
        layout.getStyle().set("margin-top", "var(--lumo-space-l)");

        H3 title = new H3("My History");

        Tab leaveTab = new Tab("Leave History");
        Tab attendanceTab = new Tab("Attendance History");
        Tabs tabs = new Tabs(leaveTab, attendanceTab);
        tabs.setWidthFull();

        VerticalLayout leaveLayout = createLeaveHistoryLayout(employeeId);
        VerticalLayout attendanceLayout = createAttendanceHistoryLayout(employeeId);
        attendanceLayout.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            boolean isLeave = event.getSelectedTab().equals(leaveTab);
            leaveLayout.setVisible(isLeave);
            attendanceLayout.setVisible(!isLeave);
        });

        layout.add(title, tabs, leaveLayout, attendanceLayout);
        return layout;
    }

    private VerticalLayout createLeaveHistoryLayout(Long employeeId) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setWidthFull();
        layout.setMinHeight("420px");
//        layout.setHeightFull(); // Ensures it fills the active tab container space

        ComboBox<String> statusFilter = new ComboBox<>("Filter by Status");
        statusFilter.setItems("PENDING", "APPROVED", "REJECTED", "CANCELLED");
        statusFilter.setClearButtonVisible(true);

        DatePicker yearFilter = new DatePicker("Starts After");

        HorizontalLayout toolbar = new HorizontalLayout(statusFilter, yearFilter);
        toolbar.setAlignItems(FlexComponent.Alignment.END);
        toolbar.getStyle().set("gap", "var(--lumo-space-m)");

        Grid<LeaveRequest> grid = createHistoryGrid(employeeId);

        statusFilter.addValueChangeListener(e -> {
            List<LeaveRequest> filtered = leaveRequestService.getLeaveHistoryForEmployee(employeeId).stream()
                    .filter(r -> e.getValue() == null || r.getStatus().equalsIgnoreCase(e.getValue()))
                    .toList();
            grid.setItems(filtered);
        });

        layout.add(toolbar, grid);
        layout.setFlexGrow(1, grid); // Forces the grid to take up the remaining space dynamically
        return layout;
    }

    private Grid<LeaveRequest> createHistoryGrid(Long employeeId) {
        Grid<LeaveRequest> grid = new Grid<>(LeaveRequest.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setWidthFull();
        grid.setHeight("350px"); // Assign a stable height for scrolling dynamic items

        // Use specific flex-grows so large text blocks don't smash narrow columns
        grid.addColumn(request -> request.getLeaveType().getName()).setHeader("Leave Type").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(LeaveRequest::getStartDate).setHeader("Start Date").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(LeaveRequest::getEndDate).setHeader("End Date").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(LeaveRequest::getDurationDays).setHeader("Days").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::createLeaveStatusBadge).setHeader("Status").setAutoWidth(true).setFlexGrow(0);

        // The reason field gets the highest flex grow to swallow long texts gracefully
        grid.addColumn(LeaveRequest::getReason).setHeader("Reason").setTooltipGenerator(LeaveRequest::getReason).setAutoWidth(true).setFlexGrow(3);

        grid.setItems(leaveRequestService.getLeaveHistoryForEmployee(employeeId));
        return grid;
    }

    private Span createLeaveStatusBadge(LeaveRequest request) {
        Span badge = new Span(request.getStatus());
        badge.getElement().getThemeList().add("badge");
        String status = request.getStatus() != null ? request.getStatus().toUpperCase() : "";

        switch (status) {
            case "APPROVED": badge.getElement().getThemeList().add("success"); break;
            case "REJECTED": badge.getElement().getThemeList().add("error"); break;
            case "PENDING":
            case "ESCALATED":
            case "RETURNED_FOR_EDIT": badge.getElement().getThemeList().add("warning"); break;
            case "CANCELLED": badge.getElement().getThemeList().add("contrast"); break;
        }
        return badge;
    }

    private VerticalLayout createAttendanceHistoryLayout(Long employeeId) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setWidthFull();
        layout.setMinHeight("420px");
//        layout.setHeightFull();

        DatePicker startDateFilter = new DatePicker("Start Date");
        DatePicker endDateFilter = new DatePicker("End Date");

        startDateFilter.setValue(LocalDate.now().minusDays(30));
        endDateFilter.setValue(LocalDate.now());

        HorizontalLayout toolbar = new HorizontalLayout(startDateFilter, endDateFilter);
        toolbar.setAlignItems(FlexComponent.Alignment.END);
        toolbar.getStyle().set("gap", "var(--lumo-space-m)");

        Grid<Attendance> grid = new Grid<>(Attendance.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setWidthFull();
        grid.setHeight("350px"); // Assign a stable height here as well

        grid.addColumn(Attendance::getAttendanceDate).setHeader("Date").setAutoWidth(true).setSortable(true).setFlexGrow(1);
        grid.addColumn(a -> a.getCheckIn() != null ? a.getCheckIn().toLocalTime().toString() : "-").setHeader("Clock In").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(a -> a.getCheckOut() != null ? a.getCheckOut().toLocalTime().toString() : "-").setHeader("Clock Out").setAutoWidth(true).setFlexGrow(1);

        grid.addColumn(a -> {
            if (a.getCheckIn() != null && a.getCheckOut() != null) {
                long hours = Duration.between(a.getCheckIn(), a.getCheckOut()).toHours();
                long mins = Duration.between(a.getCheckIn(), a.getCheckOut()).toMinutesPart();
                return hours + "h " + mins + "m";
            }
            return "-";
        }).setHeader("Total Hours").setAutoWidth(true).setFlexGrow(1);

        grid.addComponentColumn(this::createAttendanceStatusBadge).setHeader("Status").setAutoWidth(true).setFlexGrow(1);

        Runnable refreshGrid = () -> {
            if (startDateFilter.getValue() != null && endDateFilter.getValue() != null) {
                grid.setItems(attendanceProcessService.getEmployeeAttendanceHistory(
                        employeeId, startDateFilter.getValue(), endDateFilter.getValue()));
            }
        };

        startDateFilter.addValueChangeListener(e -> refreshGrid.run());
        endDateFilter.addValueChangeListener(e -> refreshGrid.run());

        refreshGrid.run();

        layout.add(toolbar, grid);
        layout.setFlexGrow(1, grid); // Forces layout to render cleanly
        return layout;
    }

    private Span createAttendanceStatusBadge(Attendance attendance) {
        String status = attendance.getStatus() != null ? attendance.getStatus().toUpperCase() : "PENDING";
        Span badge = new Span(status);
        badge.getElement().getThemeList().add("badge");

        switch (status) {
            case "PRESENT": badge.getElement().getThemeList().add("success"); break;
            case "LATE":
            case "MISSING_CHECKOUT": badge.getElement().getThemeList().add("warning"); break;
            case "ABSENT":
            case "HALF_DAY_ABSENT": badge.getElement().getThemeList().add("error"); break;
            case "ON_LEAVE":
            case "HALF_DAY_LEAVE": badge.getElement().getThemeList().add("contrast"); break;
        }
        return badge;
    }


    private boolean isManagerialRole() {
        return securityService.hasRole("ROLE_SUPER_ADMIN") ||
                securityService.hasRole("ROLE_HR_ADMIN") ||
                securityService.hasRole("ROLE_MANAGER") ||
                securityService.hasRole("ROLE_DEPT_HEAD");
    }

    private Component createTeamAttendanceSection(Long managerId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        H3 title = new H3("Today's Team Attendance Overview");
        title.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.NONE);

        TeamAttendanceSummaryDTO summary = attendanceProcessService.getTodayTeamAttendanceSummary(managerId);

        HorizontalLayout summaryCards = new HorizontalLayout();
        summaryCards.setWidthFull();

        summaryCards.add(
                createStatCard("Present", summary.getPresentCount(), "success"),
                createStatCard("Late", summary.getLateCount(), "warning"),
                createStatCard("Absent", summary.getAbsentCount(), "error")
        );

        Grid<Attendance> teamGrid = new Grid<>(Attendance.class, false);
        teamGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        teamGrid.setWidthFull();
        teamGrid.setHeight("250px");

        teamGrid.addColumn(a -> a.getEmployee().getFirstName() + " " + a.getEmployee().getFirstName()).setHeader("Employee").setAutoWidth(true);
        teamGrid.addColumn(a -> a.getCheckIn() != null ? a.getCheckIn().toLocalTime().toString() : "-").setHeader("Clock In").setAutoWidth(true);
        teamGrid.addComponentColumn(this::createAttendanceStatusBadge).setHeader("Status").setAutoWidth(true);

        // Required backend call: You will need to implement this
        teamGrid.setItems(attendanceProcessService.getTodayTeamAttendanceDetails(managerId));

        section.add(title, summaryCards, teamGrid);
        return section;
    }

    private Component createStatCard(String title, int count, String theme) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(LumoUtility.Background.BASE, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.BoxShadow.XSMALL, LumoUtility.Padding.MEDIUM, LumoUtility.Border.ALL);
        card.setAlignItems(FlexComponent.Alignment.CENTER);

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        H2 countHeading = new H2(String.valueOf(count));
        countHeading.addClassNames(LumoUtility.Margin.NONE);
        countHeading.getElement().getThemeList().add(theme); // Uses Lumo text colors

        card.add(titleSpan, countHeading);
        return card;
    }
}