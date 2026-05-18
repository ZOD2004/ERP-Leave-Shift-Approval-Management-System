package com.murali.views;

import com.murali.entity.LeaveRequest;
import com.murali.service.DashboardService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
@PageTitle("Admin Dashboard")
@Route(value = "admin-dashboard", layout = MainLayout.class)
public class ExecutiveDashboardView extends VerticalLayout {

    private final DashboardService dashboardService;

    public ExecutiveDashboardView(DashboardService dashboardService) {
        this.dashboardService = dashboardService;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE, LumoUtility.Gap.LARGE);

        buildUI();
    }

    private void buildUI() {
        H2 title = new H2("HR Operations Overview");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL);

        // 1. KPI Row using the createStatsCard method defined below
        HorizontalLayout kpiRow = new HorizontalLayout(
                createStatsCard("On Leave Today", dashboardService.getTodayAbsenceCount(), VaadinIcon.EXIT, "var(--lumo-error-text-color)"),
                createStatsCard("Pending Approvals", dashboardService.getTotalPendingCount(), VaadinIcon.CLOCK, "var(--lumo-primary-color)"),
                createStatsCard("Leave Utilization", dashboardService.getMonthlyUtilization() + "%", VaadinIcon.CHART_3D, "var(--lumo-success-text-color)"),
                createStatsCard("Attendance Rate", dashboardService.getAttendanceRate() + "%", VaadinIcon.USER_CHECK, "var(--lumo-secondary-text-color)")
        );
        kpiRow.setWidthFull();
        kpiRow.addClassNames(LumoUtility.FlexWrap.WRAP, LumoUtility.Margin.Bottom.MEDIUM);

        // 2. Main Content Split
        HorizontalLayout contentSplit = new HorizontalLayout(
                createPendingApprovalsPreview(),
                createAnalyticsCard()
        );
        contentSplit.setWidthFull();
        contentSplit.setFlexGrow(2, contentSplit.getComponentAt(0));
        contentSplit.setFlexGrow(1, contentSplit.getComponentAt(1));

        add(title, kpiRow, contentSplit, createCurrentAbsenceList());
    }

    // --- REUSABLE UI HELPERS ---

    private Component createStatsCard(String title, String value, VaadinIcon icon, String color) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(
                LumoUtility.Background.BASE,
                LumoUtility.Border.ALL,
                LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Padding.MEDIUM,
                LumoUtility.BoxShadow.SMALL,
                LumoUtility.Gap.SMALL
        );
        card.setSpacing(false);
        card.setMinWidth("200px");

        Icon iconComp = icon.create();
        iconComp.getStyle().set("color", color);

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.MEDIUM);

        H3 valueH3 = new H3(value);
        valueH3.addClassNames(LumoUtility.Margin.NONE);

        HorizontalLayout header = new HorizontalLayout(iconComp, titleSpan);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        card.add(header, valueH3);
        return card;
    }

    private Component createPendingApprovalsPreview() {
        VerticalLayout container = new VerticalLayout();
        container.addClassNames(LumoUtility.Background.BASE, LumoUtility.Border.ALL, LumoUtility.BorderRadius.LARGE);

        H3 title = new H3("Critical Pending Approvals");
        title.addClassNames(LumoUtility.FontSize.MEDIUM);

        Grid<LeaveRequest> grid = new Grid<>(LeaveRequest.class, false);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(req -> req.getEmployee().getFirstName()).setHeader("Employee");
        grid.addColumn(LeaveRequest::getStartDate).setHeader("Starts");

        grid.addComponentColumn(req -> {
            long daysPending = ChronoUnit.DAYS.between(req.getCreatedAt() != null ? req.getCreatedAt() : LocalDate.now(), LocalDate.now());
            Span span = new Span(daysPending + "d ago");
            if (daysPending > 3) span.addClassName(LumoUtility.TextColor.ERROR);
            return span;
        }).setHeader("Wait Time");

        grid.setItems(dashboardService.getOldestPendingRequests(5));
        grid.setAllRowsVisible(true);

        Button viewAll = new Button("View All Approvals", e -> getUI().ifPresent(ui -> ui.navigate("approvals")));
        viewAll.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout header = new HorizontalLayout(title, viewAll);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        container.add(header, grid);
        return container;
    }

    private Component createAnalyticsCard() {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.LARGE, LumoUtility.Padding.LARGE);

        H3 title = new H3("Monthly Analytics");

        VerticalLayout trends = new VerticalLayout(
                createTrendRow("Sick Leave", "+12%", true),
                createTrendRow("Annual Leave", "-5%", false),
                createTrendRow("Work From Home", "+20%", true)
        );
        trends.setPadding(false);

        Button reportBtn = new Button("Audit History Report", VaadinIcon.FILE_SEARCH.create());
        reportBtn.setWidthFull();
        reportBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(title, new Span("Usage trends vs last month"), trends, reportBtn);
        return card;
    }

    private HorizontalLayout createTrendRow(String label, String value, boolean isUp) {
        HorizontalLayout row = new HorizontalLayout(new Span(label), new Span(value));
        row.setWidthFull();
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        row.addClassName(LumoUtility.FontSize.SMALL);

        Span valSpan = (Span) row.getComponentAt(1);
        valSpan.addClassNames(LumoUtility.FontWeight.BOLD, isUp ? LumoUtility.TextColor.ERROR : LumoUtility.TextColor.SUCCESS);

        return row;
    }

    private Component createCurrentAbsenceList() {
        Details details = new Details("Who's Away Today", new VerticalLayout(
                new Span("Logistics: 4 away | Engineering: 2 away | Sales: 8 away")
        ));
        details.setWidthFull();
        details.addClassNames(LumoUtility.Background.BASE, LumoUtility.Border.ALL, LumoUtility.BorderRadius.MEDIUM);
        return details;
    }
}