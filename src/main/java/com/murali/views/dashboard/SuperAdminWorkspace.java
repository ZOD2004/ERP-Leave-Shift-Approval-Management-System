package com.murali.views.dashboard;

import com.murali.entity.AuditLog;
import com.murali.repository.DepartmentRepository;
import com.murali.repository.UserRepository;
import com.murali.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@SpringComponent
@UIScope
public class SuperAdminWorkspace extends VerticalLayout {

    private final EmployeeService employeeService;
    private final AuditLogService auditLogService;
    private final DepartmentRepository departmentRepository; // Or DepartmentService
    private final UserRepository userRepository; // Or UserService
    private final LeaveRequestService leaveRequestService;
    private final AttendanceCorrectionService attendanceCorrectionService;
    private final AttendanceCronJobService attendanceCronJobService;

    public SuperAdminWorkspace(EmployeeService employeeService, AuditLogService auditLogService, DepartmentRepository departmentRepository, UserRepository userRepository, LeaveRequestService leaveRequestService, AttendanceCorrectionService attendanceCorrectionService, AttendanceCronJobService attendanceCronJobService) {
        this.employeeService = employeeService;
        this.auditLogService = auditLogService;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.leaveRequestService = leaveRequestService;
        this.attendanceCorrectionService = attendanceCorrectionService;
        this.attendanceCronJobService = attendanceCronJobService;

        setPadding(false);
        setSpacing(true);
        setWidthFull();

        buildUI();
    }

    private void buildUI() {
        // 1. Header
        H2 header = new H2("System Administrator Console");
        header.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.MEDIUM, LumoUtility.TextColor.PRIMARY);
        add(header);

        // 2. KPI Cards Section
        add(createKpiSection());
        add(new Hr());

        // 3. Middle Section: System Health & Activity
        HorizontalLayout middleSection = new HorizontalLayout();
        middleSection.setWidthFull();
        middleSection.setAlignItems(FlexComponent.Alignment.START);

        Component systemHealth = createSystemHealthWidget();
        Component recentActivity = createAuditLogWidget();

        // Give the Grid more space than the health cards
        middleSection.add(systemHealth, recentActivity);
        middleSection.setFlexGrow(1, systemHealth);
        middleSection.setFlexGrow(2, recentActivity);

        add(middleSection);
    }

    private Component createKpiSection() {
        HorizontalLayout kpiLayout = new HorizontalLayout();
        kpiLayout.setWidthFull();
        kpiLayout.setSpacing(true);
        // Force horizontal layout and allow horizontal scrolling on small screens
        kpiLayout.getStyle().set("overflow-x", "auto");
        kpiLayout.getStyle().set("padding-bottom", "8px");

        // Fetch Data safely
        long totalEmployees = employeeService.findAllActive().size();
        long activeLogins = auditLogService.getActiveLoginsToday();
        long totalDepts = departmentRepository.count();
        long totalManagers = userRepository.countByRoleName("ROLE_MANAGER") + userRepository.countByRoleName("ROLE_DEPT_HEAD");

        long pendingLeaves = leaveRequestService.countPendingRequests();
        long pendingCorrections = attendanceCorrectionService.getAllPendingCorrectionsGlobally().size();
        long totalPendingApprovals = pendingLeaves + pendingCorrections;

        long auditEventsToday = auditLogService.getRecentLogs(100).stream()
                .filter(log -> log.getTimestamp().toLocalDate().equals(LocalDate.now()))
                .count();

        // Build Cards
        kpiLayout.add(
                createStatCard("Total Employees", String.valueOf(totalEmployees), VaadinIcon.USERS, "var(--lumo-primary-color)"),
                createStatCard("Active Logins Today", String.valueOf(activeLogins), VaadinIcon.SIGN_IN, "var(--lumo-success-color)"),
                createStatCard("Departments", String.valueOf(totalDepts), VaadinIcon.BUILDING, "var(--lumo-contrast-70pct)"),
                createStatCard("Managers & HODs", String.valueOf(totalManagers), VaadinIcon.USER, "var(--lumo-contrast-70pct)"),
                createStatCard("System Pending Approvals", String.valueOf(totalPendingApprovals), VaadinIcon.INBOX, "var(--lumo-warning-color)"),
                createStatCard("Audit Events Today", String.valueOf(auditEventsToday), VaadinIcon.RECORDS, "var(--lumo-secondary-text-color)")
        );

        return kpiLayout;
    }

    private Component createStatCard(String title, String value, VaadinIcon iconEnum, String iconColor) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(LumoUtility.Background.BASE, LumoUtility.BorderRadius.MEDIUM, LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.MEDIUM, LumoUtility.Border.ALL);
        card.setSpacing(false);
        card.setMinWidth("220px");

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

    private Component createSystemHealthWidget() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        H3 title = new H3("System Health & Jobs");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        // Fetch Cron Job Status
        String cronStatus = attendanceCronJobService.getLastRunStatus();
        String lastRun = attendanceCronJobService.getLastRunTime() != null ? attendanceCronJobService.getLastRunTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "Never Run";

        // Build the Health Card
        VerticalLayout healthCard = new VerticalLayout();
        healthCard.addClassNames(LumoUtility.Background.BASE, LumoUtility.BorderRadius.MEDIUM, LumoUtility.BoxShadow.SMALL, LumoUtility.Padding.LARGE, LumoUtility.Border.ALL);

        Span jobTitle = new Span(VaadinIcon.AUTOMATION.create(), new Span(" Attendance Nightly Batch"));
        jobTitle.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.FontSize.MEDIUM);

        Span statusBadge = new Span(cronStatus);
        statusBadge.getElement().getThemeList().add("badge");

        if ("SUCCESS".equals(cronStatus)) {
            statusBadge.getElement().getThemeList().add("success");
            healthCard.getStyle().set("border-left", "4px solid var(--lumo-success-color)");
        } else if ("RUNNING".equals(cronStatus)) {
            statusBadge.getElement().getThemeList().add("primary");
            healthCard.getStyle().set("border-left", "4px solid var(--lumo-primary-color)");
        } else {
            statusBadge.getElement().getThemeList().add("error");
            healthCard.getStyle().set("border-left", "4px solid var(--lumo-error-color)");
        }

        HorizontalLayout statusRow = new HorizontalLayout(new Span("Status:"), statusBadge);
        statusRow.setAlignItems(FlexComponent.Alignment.CENTER);
        statusRow.addClassNames(LumoUtility.Margin.Top.SMALL, LumoUtility.FontSize.SMALL);

        Span timeSpan = new Span("Last Run: " + lastRun);
        timeSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        healthCard.add(jobTitle, statusRow, timeSpan);
        layout.add(title, healthCard);

        return layout;
    }

    private Component createAuditLogWidget() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        H3 title = new H3("Live Audit Stream");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        Grid<AuditLog> grid = new Grid<>(AuditLog.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.setHeight("350px");
        grid.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)").set("border-radius", "8px");

        grid.addColumn(log -> log.getTimestamp() != null ? log.getTimestamp().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "").setHeader("Time").setAutoWidth(true).setFlexGrow(0);

        grid.addColumn(AuditLog::getPerformedBy).setHeader("User").setAutoWidth(true);
        grid.addColumn(log -> log.getEntityName().toUpperCase()).setHeader("Module").setAutoWidth(true);

        grid.addComponentColumn(log -> {
            Span badge = new Span(log.getAction());
            badge.getElement().getThemeList().add("badge small");
            if (log.getAction().contains("DELETE") || log.getAction().contains("REJECT") || log.getAction().contains("CANCEL")) {
                badge.getElement().getThemeList().add("error");
            } else if (log.getAction().contains("CREATE") || log.getAction().contains("APPROVE") || log.getAction().contains("SUCCESS")) {
                badge.getElement().getThemeList().add("success");
            }
            return badge;
        }).setHeader("Action").setAutoWidth(true);

        grid.addComponentColumn(log -> {
            Button viewBtn = new Button("Diff", VaadinIcon.SEARCH.create());
            viewBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            if (log.getOldState() == null && log.getNewState() == null) {
                viewBtn.setVisible(false); // Hide if there's no data payload to diff (like simple logins)
            }
            viewBtn.addClickListener(e -> openDiffViewer(log));
            return viewBtn;
        }).setHeader("Payload").setAutoWidth(true).setFlexGrow(1);

        grid.setItems(auditLogService.getRecentLogs(40));

        layout.add(title, grid);
        return layout;
    }

    private void openDiffViewer(AuditLog log) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Audit Payload Diff (Record ID: " + log.getRecordId() + ")");
        dialog.setWidth("800px");

        HorizontalLayout diffLayout = new HorizontalLayout();
        diffLayout.setSizeFull();

        TextArea oldStateArea = new TextArea("Old State");
        oldStateArea.setValue(log.getOldState() != null ? log.getOldState() : "NULL");
        oldStateArea.setReadOnly(true);
        oldStateArea.setWidth("50%");
        oldStateArea.getStyle().set("color", "var(--lumo-error-text-color)");

        TextArea newStateArea = new TextArea("New State");
        newStateArea.setValue(log.getNewState() != null ? log.getNewState() : "NULL");
        newStateArea.setReadOnly(true);
        newStateArea.setWidth("50%");
        newStateArea.getStyle().set("color", "var(--lumo-success-text-color)");

        diffLayout.add(oldStateArea, newStateArea);

        Button closeButton = new Button("Close", e -> dialog.close());
        dialog.getFooter().add(closeButton);

        dialog.add(diffLayout);
        dialog.open();
    }
}
