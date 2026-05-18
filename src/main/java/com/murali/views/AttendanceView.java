package com.murali.views;


import com.murali.entity.Attendance;
import com.murali.entity.User;
import com.murali.security.CustomUserDetailsService;
import com.murali.service.AttendanceProcessService;
import com.murali.service.SecurityService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Route(value = "my-attendance",layout = MainLayout.class)
@PageTitle("My Attendance | ERP")
@PermitAll
public class AttendanceView extends VerticalLayout {

    private final AttendanceProcessService processService;
    private final SecurityService securityService;
    private Long loggedInEmployeeId;


    private final Span todayStatusLabel = new Span();
    private final Button checkInButton = new Button("Check In");
    private final Button checkOutButton = new Button("Check Out");
    private final Grid<Attendance> historyGrid = new Grid<>(Attendance.class, false);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    public AttendanceView(AttendanceProcessService processService,SecurityService securityService) {
        this.processService = processService;
        this.securityService = securityService;

        this.loggedInEmployeeId = securityService.getCurrentEmployeeId();

        setSizeFull();
        setSpacing(true);

        add(createHeader());
        add(createActionPanel());
        add(createGrid());

        refreshUI();
    }

    private HorizontalLayout createHeader() {
        H2 title = new H2("My Attendance");
        title.getStyle().set("margin-top", "0");

        HorizontalLayout header = new HorizontalLayout(title);
        header.setWidthFull();
        return header;
    }

    private VerticalLayout createActionPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.getStyle().set("border", "1px solid #e0e0e0");
        panel.getStyle().set("border-radius", "8px");
        panel.getStyle().set("padding", "16px");
        panel.getStyle().set("background-color", "#f9f9f9");

        todayStatusLabel.getStyle().set("font-weight", "bold");
        todayStatusLabel.getStyle().set("font-size", "1.2em");

        checkInButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        checkOutButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        checkInButton.addClickListener(e -> handleCheckIn());
        checkOutButton.addClickListener(e -> handleCheckOut());

        HorizontalLayout buttonLayout = new HorizontalLayout(checkInButton, checkOutButton);
        panel.add(new Span("Today's Status: "), todayStatusLabel, buttonLayout);
        return panel;
    }

    private Grid<Attendance> createGrid() {
        historyGrid.setSizeFull();

        historyGrid.addColumn(a -> a.getAttendanceDate() != null ? a.getAttendanceDate().format(DATE_FORMATTER) : "")
                .setHeader("Date")
                .setAutoWidth(true)
                .setSortable(true);

        historyGrid.addColumn(a -> {
            if (a.getShiftAssignment() != null && a.getShiftAssignment().getShift() != null) {
                return a.getShiftAssignment().getShift().getName();
            }
            return "Unassigned";
        }).setHeader("Assigned Shift").setAutoWidth(true);

        historyGrid.addColumn(a -> a.getCheckIn() != null ? a.getCheckIn().format(TIME_FORMATTER) : "--")
                .setHeader("Check In Time");

        historyGrid.addColumn(a -> a.getCheckOut() != null ? a.getCheckOut().format(TIME_FORMATTER) : "--")
                .setHeader("Check Out Time");

        historyGrid.addComponentColumn(this::createStatusBadge)
                .setHeader("Status")
                .setAutoWidth(true);

        return historyGrid;
    }

    private void refreshUI() {
        Optional<Attendance> todayOpt = processService.getTodayAttendance(loggedInEmployeeId);

        if (todayOpt.isPresent()) {
            Attendance today = todayOpt.get();
            todayStatusLabel.setText(today.getStatus());

            checkInButton.setEnabled(today.getCheckIn() == null);
            checkOutButton.setEnabled(today.getCheckIn() != null && today.getCheckOut() == null);
        } else {
            todayStatusLabel.setText("PENDING CHECK-IN");
            checkInButton.setEnabled(true);
            checkOutButton.setEnabled(false);
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        historyGrid.setItems(processService.getEmployeeAttendanceHistory(loggedInEmployeeId, startDate, endDate));
    }

    private void handleCheckIn() {
        try {
            processService.processDailyPunch(loggedInEmployeeId, LocalDateTime.now(), true);
            showNotification("Successfully Checked In!", NotificationVariant.LUMO_SUCCESS);
            refreshUI();
        } catch (Exception ex) {
            showNotification("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void handleCheckOut() {
        try {
            processService.processDailyPunch(loggedInEmployeeId, LocalDateTime.now(), false);
            showNotification("Successfully Checked Out!", NotificationVariant.LUMO_SUCCESS);
            refreshUI();
        } catch (Exception ex) {
            showNotification("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private Span createStatusBadge(Attendance attendance) {
        Span badge = new Span(attendance.getStatus() != null ? attendance.getStatus() : "UNKNOWN");
        badge.getElement().getThemeList().add("badge");

        if ("PRESENT".equals(attendance.getStatus())) {
            badge.getElement().getThemeList().add("success");
        } else if ("LATE".equals(attendance.getStatus()) || "HALF_DAY_ABSENT".equals(attendance.getStatus())) {
            badge.getElement().getThemeList().add("contrast");
        } else if ("ABSENT".equals(attendance.getStatus())) {
            badge.getElement().getThemeList().add("error");
        }

        return badge;
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
}
