package com.murali.views;

import com.murali.entity.AuditLog;
import com.murali.entity.LeaveBalance;
import com.murali.entity.LeaveBalanceTransaction;
import com.murali.entity.LeaveRequest;
import com.murali.service.AuditLogService;
import com.murali.service.DashboardService;
import com.murali.service.LeaveBalanceService;
import com.murali.service.LeaveRequestService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@PageTitle("Audit & Compliance Dashboard")
@Route(value = "audit-dashboard",layout = MainLayout.class)
@RolesAllowed({"ROLE_AUDITOR","ROLE_SUPER_ADMIN"})
public class AuditDashboardView extends VerticalLayout {

    private final DashboardService dashboardService;
    private final LeaveBalanceService leaveBalanceService;
    private final AuditLogService auditLogService;
    private final LeaveRequestService leaveRequestService;

    public AuditDashboardView(DashboardService dashboardService,
                              LeaveBalanceService leaveBalanceService,
                              AuditLogService auditLogService,
                              LeaveRequestService leaveRequestService) {
        this.dashboardService = dashboardService;
        this.leaveBalanceService = leaveBalanceService;
        this.auditLogService = auditLogService;
        this.leaveRequestService = leaveRequestService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        this.getStyle().set("overflow-y", "auto");

        add(
                createHeader(),
                createKpiSection(),
                createLeaveLedgerSection(),
                createAuditLogSection()
        );
    }

    private Component createHeader() {
        H2 header = new H2("System Audit & Compliance");
        header.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.MEDIUM);
        return header;
    }

    private Component createKpiSection() {
        HorizontalLayout kpiLayout = new HorizontalLayout();
        kpiLayout.setWidthFull();
        kpiLayout.setSpacing(true);

        long manualOverrides = dashboardService.getManualOverridesCount();
        long missingPunches = dashboardService.getMissingPunchesCount();
        long escalatedApprovals = dashboardService.getEscalatedApprovalsCount();

        // FAST: Executes a single SELECT COUNT(...) query in the DB!
        long negativeBalances = dashboardService.getNegativeBalancesCount();

        kpiLayout.add(
                createCard("Manual Overrides", String.valueOf(manualOverrides), "Shifts with override_applied"),
                createCard("Missing Punches", String.valueOf(missingPunches), "Records with MISSING_CHECKOUT"),
                createCard("Escalated Approvals", String.valueOf(escalatedApprovals), "Level 3+ Leave Requests"),
                createCard("Negative Balances", String.valueOf(negativeBalances), "Effective balance < 0")
        );

        return kpiLayout;
    }
    private Component createCard(String title, String value, String subtitle) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(
                LumoUtility.Background.BASE, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.BoxShadow.XSMALL, LumoUtility.Padding.MEDIUM
        );
        card.setSpacing(false);
        card.getStyle().set("flex", "1");

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD);

        Span valueSpan = new Span(value);
        valueSpan.addClassNames(LumoUtility.FontSize.XXLARGE, LumoUtility.FontWeight.BOLD);

        Span subtitleSpan = new Span(subtitle);
        subtitleSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY);

        card.add(titleSpan, valueSpan, subtitleSpan);
        return card;
    }

    // --- SECTION B: LEAVE LEDGER ---
    private Component createLeaveLedgerSection() {
        VerticalLayout ledgerLayout = new VerticalLayout();
        ledgerLayout.setWidthFull();
        ledgerLayout.setPadding(false);

        H3 title = new H3("Leave Ledger");
        title.addClassNames(LumoUtility.Margin.Bottom.NONE);

        // Filters
        TextField empFilter = new TextField("Employee Name/ID");
        DatePicker dateFilter = new DatePicker("Date");
        ComboBox<String> typeFilter = new ComboBox<>("Transaction Type");
        typeFilter.setItems(LeaveBalanceService.ALLOCATION, LeaveBalanceService.PENDING_HOLD,
                LeaveBalanceService.HOLD_RELEASE, LeaveBalanceService.LEAVE_DEDUCT,
                LeaveBalanceService.LEAVE_REFUND);

        HorizontalLayout filters = new HorizontalLayout(empFilter, dateFilter, typeFilter);
        filters.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        // Grid
        Grid<LeaveBalanceTransaction> grid = new Grid<>(LeaveBalanceTransaction.class, false);
        grid.setSelectionMode(Grid.SelectionMode.NONE); // Strictly Read-Only
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setHeight("300px");

        grid.addColumn(tx -> tx.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .setHeader("Date").setSortable(true).setAutoWidth(true);
        grid.addColumn(tx -> tx.getEmployee().getId() + " - " + tx.getEmployee().getFirstName()) // Adjust to your Employee fields
                .setHeader("Employee").setAutoWidth(true);
        grid.addColumn(tx -> tx.getLeaveType().getCode()) // Adjust to your LeaveType fields
                .setHeader("Leave Type").setAutoWidth(true);
        grid.addColumn(LeaveBalanceTransaction::getTransactionType)
                .setHeader("Transaction Type").setAutoWidth(true);
        grid.addColumn(LeaveBalanceTransaction::getDays)
                .setHeader("Days (+/-)");

        // Reference ID Column with Dialog trigger
        grid.addColumn(new ComponentRenderer<>(tx -> {
            if (tx.getReferenceId() == null) return new Span("-");
            Button refBtn = new Button(String.valueOf(tx.getReferenceId()));
            refBtn.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
            refBtn.addClickListener(e -> openLeaveRequestDialog(tx.getReferenceId()));
            return refBtn;
        })).setHeader("Ref ID");

        // Assumed data fetch & filtering setup
        List<LeaveBalanceTransaction> transactions = leaveBalanceService.findAllWithDetails();
        ListDataProvider<LeaveBalanceTransaction> dataProvider = new ListDataProvider<>(transactions);
        grid.setDataProvider(dataProvider);

        // Quick in-memory filtering logic
        empFilter.addValueChangeListener(e -> dataProvider.addFilter(tx ->
                String.valueOf(tx.getEmployee().getId()).contains(e.getValue()) ||
                        tx.getEmployee().getFirstName().toLowerCase().contains(e.getValue().toLowerCase())));
        typeFilter.addValueChangeListener(e -> dataProvider.addFilter(tx ->
                e.getValue() == null || tx.getTransactionType().equals(e.getValue())));

        ledgerLayout.add(title, filters, grid);
        return ledgerLayout;
    }

    private void openLeaveRequestDialog(Long requestId) {
        LeaveRequest request = leaveRequestService.findById(requestId); // Ensure null check in real impl
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Leave Request Details (ID: " + requestId + ")");

        VerticalLayout layout = new VerticalLayout();
        if(request != null) {
            layout.add(new Span("Status: " + request.getStatus()));
            layout.add(new Span("Reason: " + request.getReason()));
            layout.add(new Span("Duration: " + request.getDurationDays() + " days"));
            layout.add(new Span("Current Level: " + request.getCurrentLevel()));
        } else {
            layout.add(new Span("Request record not found or deleted."));
        }

        Button closeBtn = new Button("Close", e -> dialog.close());
        dialog.getFooter().add(closeBtn);
        dialog.add(layout);
        dialog.open();
    }


    // --- SECTION C: SYSTEM ACTIVITY FEED ---
    private Component createAuditLogSection() {
        VerticalLayout auditLayout = new VerticalLayout();
        auditLayout.setWidthFull();
        auditLayout.setPadding(false);

        H3 title = new H3("System Activity Feed");
        title.addClassNames(LumoUtility.Margin.Bottom.NONE);

        Grid<AuditLog> grid = new Grid<>(AuditLog.class, false);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setHeight("400px"); // Slightly taller to fit details comfortably

        grid.addColumn(log -> log.getTimestamp() != null ?
                        log.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "")
                .setHeader("Timestamp").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        // ENHANCEMENT 1: Stylish Action Badges
        grid.addComponentColumn(log -> {
            Span badge = new Span(log.getAction());
            badge.getElement().getThemeList().add("badge");
            badge.getElement().getThemeList().add("small");
            switch (log.getAction()) {
                case "CREATED": case "BATCH_CREATED":
                    badge.getElement().getThemeList().add("success"); break;
                case "DELETED":
                    badge.getElement().getThemeList().add("error"); break;
                case "UPDATED": case "PASSWORD_CHANGED":
                    badge.getElement().getThemeList().add("primary"); break;
                default:
                    badge.getElement().getThemeList().add("contrast"); break;
            }
            return badge;
        }).setHeader("Action").setAutoWidth(true).setFlexGrow(0);

        grid.addColumn(AuditLog::getEntityName)
                .setHeader("Module").setAutoWidth(true).setFlexGrow(0);

        grid.addColumn(AuditLog::getPerformedBy)
                .setHeader("Performed By").setAutoWidth(true).setFlexGrow(1);

        // ENHANCEMENT 2: Clean, Monospaced JSON Diff Viewer
        grid.setItemDetailsRenderer(new ComponentRenderer<>(auditLog -> {
            HorizontalLayout diffLayout = new HorizontalLayout();
            diffLayout.setWidthFull();
            diffLayout.setPadding(true);
            diffLayout.setSpacing(true);
            diffLayout.addClassNames(LumoUtility.Background.CONTRAST_5);
            diffLayout.getStyle().set("box-shadow", "inset 0px 2px 4px rgba(0,0,0,0.05)");

            VerticalLayout oldLayout = createStateBox("Old State (Before)", auditLog.getOldState(), "var(--lumo-error-color)");
            VerticalLayout newLayout = createStateBox("New State (After)", auditLog.getNewState(), "var(--lumo-success-color)");

            diffLayout.add(oldLayout, newLayout);
            diffLayout.setFlexGrow(1, oldLayout);
            diffLayout.setFlexGrow(1, newLayout);

            return diffLayout;
        }));

        grid.setItems(auditLogService.getRecentLogs(50)); // Assuming you added the limit method

        auditLayout.add(title, grid);
        return auditLayout;
    }

    // --- HELPER METHOD FOR JSON DIFF UI ---
    private VerticalLayout createStateBox(String titleText, String jsonContent, String topBorderColor) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(false);
        layout.getStyle().set("background-color", "var(--lumo-base-color)");
        layout.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        layout.getStyle().set("border-top", "4px solid " + topBorderColor);
        layout.getStyle().set("box-shadow", "var(--lumo-box-shadow-xs)");
        layout.setWidth("50%"); // Ensure strictly 50% split

        Span header = new Span(titleText);
        header.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        header.getStyle().set("margin-bottom", "var(--lumo-space-s)");

        Span codeText = new Span(jsonContent != null ? jsonContent : "NULL");

        // CRITICAL: These CSS rules prevent the text from breaking the layout
        codeText.getStyle().set("font-family", "monospace");
        codeText.getStyle().set("font-size", "var(--lumo-font-size-xxs)");
        codeText.getStyle().set("color", "var(--lumo-body-text-color)");
        codeText.getStyle().set("white-space", "pre-wrap");
        codeText.getStyle().set("word-break", "break-all");

        layout.add(header, codeText);
        return layout;
    }
}
