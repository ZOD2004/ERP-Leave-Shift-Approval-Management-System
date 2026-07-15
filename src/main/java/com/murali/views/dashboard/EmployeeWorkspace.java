package com.murali.views.dashboard;

import com.murali.dto.DailyExpectedShift;
import com.murali.entity.Attendance;
import com.murali.entity.Employee;
import com.murali.entity.Holiday;
import com.murali.entity.LeaveBalance;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.HolidayRepository;
import com.murali.service.LeaveRequestService;
import com.murali.util.SecurityService;
import com.murali.service.AttendanceProcessService;
import com.murali.service.LeaveBalanceService;
import com.murali.service.ScheduleCalculationService;
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
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.murali.entity.LeaveRequest;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SpringComponent
@UIScope
public class EmployeeWorkspace extends VerticalLayout {

    private final SecurityService securityService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceProcessService attendanceProcessService;
    private final ScheduleCalculationService scheduleCalculationService;
    private final LeaveBalanceService leaveBalanceService;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestService leaveRequestService;

    public EmployeeWorkspace(SecurityService securityService,
                             EmployeeRepository employeeRepository,
                             AttendanceProcessService attendanceProcessService,
                             ScheduleCalculationService scheduleCalculationService,
                             LeaveBalanceService leaveBalanceService,
                             HolidayRepository holidayRepository, LeaveRequestService leaveRequestService) {

        this.securityService = securityService;
        this.employeeRepository = employeeRepository;
        this.attendanceProcessService = attendanceProcessService;
        this.scheduleCalculationService = scheduleCalculationService;
        this.leaveBalanceService = leaveBalanceService;
        this.holidayRepository = holidayRepository;
        this.leaveRequestService = leaveRequestService;
        setPadding(false);
        setSpacing(true);
        setWidthFull();
        getStyle().set("gap", "var(--app-layout-margin)");

        buildUI();
    }

    private void buildUI() {
        Long currentUserId = securityService.getCurrentUserId();
        Employee employee = employeeRepository.findByUserId(currentUserId).orElse(null);

        if (employee == null) {
            add(new H2("Error: No Employee record linked to your user account."));
            return;
        }

        // 1. Personal Header & Punch Button
        add(createPersonalHeader(employee));
        // Removed unnecessary spacer Hr tags

        // 2. Today's Status Cards
        add(createTodayStatusWidget(employee));

        // 3. Middle Section: Upcoming Week & Holidays
        HorizontalLayout gridsLayout = new HorizontalLayout();
        gridsLayout.setWidthFull();
        gridsLayout.setAlignItems(FlexComponent.Alignment.START);

        Component scheduleGrid = createWeeklyScheduleWidget(employee);
        Component holidayGrid = createUpcomingHolidaysWidget();

        gridsLayout.add(scheduleGrid, holidayGrid);
        gridsLayout.setFlexGrow(2, scheduleGrid);
        gridsLayout.setFlexGrow(1, holidayGrid);

        add(gridsLayout);
        // Removed unnecessary spacer Hr tag

        // 4. History Section (Tabs)
        add(createMyHistoryWidget(employee));
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

        String titleStr = employee.getDepartment() != null ? employee.getDepartment().getName() + " Team" : "Employee";
        Span roleSpan = new Span(titleStr + " | ID: " + employee.getEmployeeCode());
        roleSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        welcomeTexts.add(greeting, roleSpan);

        // Dynamic Punch Button
        Button punchBtn = new Button();
        Optional<Attendance> todayOpt = attendanceProcessService.getTodayAttendance(employee.getId());

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
            Notification.show(isCheckIn ? "Clocked In Successfully!" : "Clocked Out Successfully!", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().getPage().reload();
        } catch (Exception ex) {
            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private Component createTodayStatusWidget(Employee employee) {
        FlexLayout kpiLayout = new FlexLayout();
        kpiLayout.setWidthFull();
        kpiLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        kpiLayout.getStyle().set("gap", "var(--app-padding)");

        LocalDate today = LocalDate.now();
        DailyExpectedShift todayExpected = scheduleCalculationService.calculateDailyShift(employee, today);
        Optional<Attendance> todayAtt = attendanceProcessService.getTodayAttendance(employee.getId());

        // Card 1: Today's Shift
        String shiftName = "Off Day";
        if (todayExpected.isWorkingDay() && todayExpected.getExpectedShift() != null) {
            shiftName = todayExpected.getExpectedShift().getStartTime() + " to " + todayExpected.getExpectedShift().getEndTime();
        } else if (todayExpected.isHoliday()) {
            shiftName = "Public Holiday";
        }

        String statusText = "Expected";
        String color = "#f1c21b";
        VaadinIcon icon = VaadinIcon.CLOCK;

        if (todayAtt.isPresent()) {
            Attendance att = todayAtt.get();
            String recordStatus = att.getStatus() != null ? att.getStatus().name() : "";

            if ("WORKING".equals(recordStatus)) {
                statusText = "Active (In at " + att.getFirstCheckIn().toLocalTime() + ")";
                color = "var(--app-primary-color)";
                icon = VaadinIcon.PLAY;
            } else if ("PRESENT".equals(recordStatus)) {
                statusText = "Present (Completed)";
                color = "#24a148"; // Standard success green
                icon = VaadinIcon.CHECK_CIRCLE;
            } else if ("PARTIAL_DAY".equals(recordStatus)) {
                statusText = "Partial Day";
                color = "#da1e28"; // Standard error red
                icon = VaadinIcon.EXCLAMATION_CIRCLE;
            } else if ("HALF_DAY_LEAVE".equals(recordStatus)) {
                statusText = "Half Day / Present";
                color = "#24a148";
                icon = VaadinIcon.ADJUST;
            } else if ("ON_LEAVE".equals(recordStatus) || "FULL_LEAVE".equals(recordStatus)) {
                statusText = "On Leave";
                color = "var(--app-text-secondary)";
                icon = VaadinIcon.FLIGHT_TAKEOFF;
            } else if ("ABSENT".equals(recordStatus)) {
                statusText = "Absent";
                color = "#da1e28";
                icon = VaadinIcon.CLOSE_CIRCLE;
            } else if (att.getFirstCheckIn() != null) {
                // Fallback for PENDING but clocked in
                statusText = "Punched In (" + att.getFirstCheckIn().toLocalTime() + ")";
                color = "#24a148";
                icon = VaadinIcon.CHECK_CIRCLE;
            }
        } else if (!todayExpected.isWorkingDay()) {
            statusText = "Not Scheduled";
            color = "var(--app-text-secondary)";
            icon = VaadinIcon.HOME_O;
        }

        // Card 3: Tomorrow's Shift
        DailyExpectedShift tomorrowExpected = scheduleCalculationService.calculateDailyShift(employee, today.plusDays(1));
        String tomorrowShift = "Off Day";
        if (tomorrowExpected.isWorkingDay() && tomorrowExpected.getExpectedShift() != null) {
            tomorrowShift = tomorrowExpected.getExpectedShift().getStartTime() + " to " + tomorrowExpected.getExpectedShift().getEndTime();
        }

        kpiLayout.add(
                createStatCard("Today's Shift", shiftName, VaadinIcon.CALENDAR_CLOCK, "var(--app-primary-color)"),
                createStatCard("Live Status", statusText, icon, color),
                createStatCard("Tomorrow's Shift", tomorrowShift, VaadinIcon.ARROW_RIGHT, "var(--app-text-secondary)")
        );

        return kpiLayout;
    }

    private Component createStatCard(String title, String value, VaadinIcon iconEnum, String iconColor) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames("standard-surface", "hoverable");
        card.setSpacing(false);
        card.setMinWidth("200px");
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

        H3 valueSpan = new H3(value);
        valueSpan.addClassNames(LumoUtility.Margin.Top.SMALL, LumoUtility.Margin.Bottom.NONE);

        card.add(header, valueSpan);
        return card;
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

        // Fetch Next 7 Days in bulk using the engine
        LocalDate today = LocalDate.now();
        Map<Long, List<DailyExpectedShift>> bulkShifts = scheduleCalculationService.calculateBatchShifts(List.of(employee), today, today.plusDays(6));

        List<DailyExpectedShift> items = bulkShifts.getOrDefault(employee.getId(), List.of());

        Span emptyMsg = new Span("No schedule available.");
        emptyMsg.addClassName("empty-grid-message");
        emptyMsg.setVisible(items.isEmpty());
        grid.setVisible(!items.isEmpty());

        grid.setItems(items);

        section.add(title, grid, emptyMsg);
        return section;
    }

    private Component createUpcomingHolidaysWidget() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Upcoming Holidays");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);

        Grid<Holiday> grid = new Grid<>(Holiday.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)").set("border-radius", "8px");
        grid.setHeight("250px");

        grid.addColumn(Holiday::getHolidayDate).setHeader("Date").setAutoWidth(true);
        grid.addColumn(Holiday::getName).setHeader("Occasion").setAutoWidth(true);

        // Fetch top 5 upcoming holidays
        List<Holiday> upcoming = holidayRepository.findUpcomingHolidays(LocalDate.now());

        Span emptyMsg = new Span("No upcoming holidays.");
        emptyMsg.addClassName("empty-grid-message");
        boolean isEmpty = upcoming.isEmpty();
        emptyMsg.setVisible(isEmpty);
        grid.setVisible(!isEmpty);

        if (!isEmpty) {
            grid.setItems(upcoming.size() > 5 ? upcoming.subList(0, 5) : upcoming);
        }

        section.add(title, grid, emptyMsg);
        return section;
    }


    private Component createMyHistoryWidget(Employee employee) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        H3 title = new H3("My History");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.NONE, LumoUtility.FontSize.MEDIUM);

        // Setup Tabs
        Tab leaveTab = new Tab(VaadinIcon.FLIGHT_TAKEOFF.create(), new Span(" Leave Requests"));
        Tab attendanceTab = new Tab(VaadinIcon.CLOCK.create(), new Span(" Attendance Records"));
        Tabs tabs = new Tabs(leaveTab, attendanceTab);

        // Create Layouts
        VerticalLayout leaveLayout = createLeaveHistoryTab(employee.getId());
        VerticalLayout attendanceLayout = createAttendanceHistoryTab(employee.getId());

        // Hide attendance by default
        attendanceLayout.setVisible(false);

        // Toggle logic
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
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)").set("border-radius", "8px");
        grid.setHeight("300px");

        grid.addColumn(r -> r.getLeaveType().getName()).setHeader("Leave Type").setAutoWidth(true);
        grid.addColumn(r -> r.getStartDate() + " to " + r.getEndDate()).setHeader("Dates").setAutoWidth(true);
        grid.addColumn(r -> r.getDurationDays() + " Days").setHeader("Duration").setAutoWidth(true);

        grid.addComponentColumn(r -> {
            Span badge = new Span(r.getStatus());
            badge.getElement().getThemeList().add("badge small");
            switch (r.getStatus().toUpperCase()) {
                case "APPROVED" -> badge.getElement().getThemeList().add("success");
                case "REJECTED", "CANCELLED" -> badge.getElement().getThemeList().add("error");
                default -> badge.getElement().getThemeList().add("warning");
            }
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        List<LeaveRequest> allRequests = leaveRequestService.getLeaveHistoryForEmployee(employeeId);
        Span emptyMsg = new Span("No leave requests found.");
        emptyMsg.addClassName("empty-grid-message");

        GlobalSearchComponent[] searchBoxRef = new GlobalSearchComponent[1];
        searchBoxRef[0] = new GlobalSearchComponent(searchTerm -> {
            String term = searchTerm.toLowerCase();
            List<LeaveRequest> filtered = allRequests.stream()
                    .filter(r -> r.getLeaveType().getName().toLowerCase().contains(term) ||
                            r.getStatus().toLowerCase().contains(term))
                    .toList();

            grid.setItems(filtered);
            grid.setVisible(!filtered.isEmpty());
            emptyMsg.setVisible(filtered.isEmpty());

            if (searchBoxRef[0] != null) {
                searchBoxRef[0].hideSpinner();
            }
        });
        searchBoxRef[0].getStyle().set("margin-bottom", "var(--app-padding)");

        grid.setItems(allRequests);
        grid.setVisible(!allRequests.isEmpty());
        emptyMsg.setVisible(allRequests.isEmpty());

        layout.add(searchBoxRef[0], grid, emptyMsg);
        return layout;
    }

    private VerticalLayout createAttendanceHistoryTab(Long employeeId) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setMargin(false);

        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);
        toolbar.getStyle().set("gap", "var(--app-padding)");

        DatePicker startDate = new DatePicker("Start Date", LocalDate.now().minusDays(30));
        DatePicker endDate = new DatePicker("End Date", LocalDate.now());

        Span emptyMsg = new Span("No attendance records found.");
        emptyMsg.addClassName("empty-grid-message");

        String[] currentSearch = new String[]{""};
        GlobalSearchComponent[] searchBoxRef = new GlobalSearchComponent[1];

        // Grid
        Grid<Attendance> grid = new Grid<>(Attendance.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)").set("border-radius", "8px");
        grid.setHeight("300px");

        grid.addColumn(Attendance::getAttendanceDate).setHeader("Date").setAutoWidth(true);
        grid.addColumn(a -> a.getFirstCheckIn() != null ? a.getFirstCheckIn().toLocalTime().toString() : "No Check-In").setHeader("Time In").setAutoWidth(true);
        grid.addColumn(a -> a.getLastCheckOut() != null ? a.getLastCheckOut().toLocalTime().toString() : "No Check-Out").setHeader("Time Out").setAutoWidth(true);

        grid.addComponentColumn(a -> {
            Span badge = new Span(a.getStatus() != null ? a.getStatus().name() : "PENDING");
            badge.getElement().getThemeList().add("badge small");
            if ("PRESENT".equals(badge.getText())) badge.getElement().getThemeList().add("success");
            else if ("ABSENT".equals(badge.getText())) badge.getElement().getThemeList().add("error");
            else badge.getElement().getThemeList().add("warning");
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        // Centralized Refresh logic connecting date filters and text search
        Runnable refreshData = () -> {
            if (startDate.getValue() != null && endDate.getValue() != null) {
                List<Attendance> data = attendanceProcessService.getEmployeeAttendanceHistory(
                        employeeId, startDate.getValue(), endDate.getValue());

                String term = currentSearch[0];
                if (term != null && !term.isBlank()) {
                    data = data.stream()
                            .filter(a -> (a.getStatus() != null && a.getStatus().name().toLowerCase().contains(term)))
                            .toList();
                }

                grid.setItems(data);
                grid.setVisible(!data.isEmpty());
                emptyMsg.setVisible(data.isEmpty());

                if (searchBoxRef[0] != null) {
                    searchBoxRef[0].hideSpinner();
                }
            }
        };

        searchBoxRef[0] = new GlobalSearchComponent(term -> {
            currentSearch[0] = term.toLowerCase();
            refreshData.run();
        });

        searchBoxRef[0].getStyle().set("margin-left", "auto");
        toolbar.add(startDate, endDate, searchBoxRef[0]);
        toolbar.setWidthFull();

        startDate.addValueChangeListener(e -> refreshData.run());
        endDate.addValueChangeListener(e -> refreshData.run());

        refreshData.run();

        layout.add(toolbar, grid, emptyMsg);
        return layout;
    }
}
