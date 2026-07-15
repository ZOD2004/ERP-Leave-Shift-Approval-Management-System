package com.murali.views.dashboard;

import com.murali.entity.Employee;
import com.murali.entity.LeaveBalance;
import com.murali.entity.LeaveRequest;
import com.murali.service.LeaveBalanceService;
import com.murali.service.LeaveRequestService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class EmployeeLeaveDialog extends Dialog {

    public EmployeeLeaveDialog(Employee employee, LeaveBalanceService leaveBalanceService, LeaveRequestService leaveRequestService) {
        setHeaderTitle("Leave Profile: " + employee.getFirstName() + " (" + employee.getEmployeeCode() + ")");
        setWidth("850px");
        setMaxWidth("95vw");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        // 1. Leave Balances Section
        H3 balanceTitle = new H3("Current Year Balances");
        balanceTitle.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);

        FlexLayout cardsLayout = new FlexLayout();
        cardsLayout.setWidthFull();
        cardsLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cardsLayout.getStyle().set("gap", "var(--app-padding)");

        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(employee.getId(), LocalDate.now().getYear());
        for (LeaveBalance balance : balances) {
            cardsLayout.add(createMiniBalanceCard(balance, leaveBalanceService));
        }

        // 2. Leave History Section
        H3 historyTitle = new H3("Leave History");
        historyTitle.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);

        Grid<LeaveRequest> grid = new Grid<>(LeaveRequest.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.addClassName("standard-surface");
        grid.setHeight("250px");

        grid.addColumn(r -> r.getLeaveType().getName()).setHeader("Type").setAutoWidth(true);
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

        List<LeaveRequest> history = leaveRequestService.getLeaveHistoryForEmployee(employee.getId());

        if (history == null || history.isEmpty()) {
            Span emptyMessage = new Span("No leave history available.");
            emptyMessage.addClassName("empty-grid-message");

            content.add(balanceTitle, cardsLayout, historyTitle, emptyMessage);
        } else {
            grid.setItems(history);
            content.add(balanceTitle, cardsLayout, historyTitle, grid);
        }
        add(content);

        Button closeBtn = new Button("Close", e -> close());
        getFooter().add(closeBtn);
    }

    private VerticalLayout createMiniBalanceCard(LeaveBalance balance, LeaveBalanceService leaveBalanceService) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames("standard-surface", "hoverable");
        card.setPadding(true);
        card.setSpacing(false);
        card.setMinWidth("180px");
        card.getStyle().set("flex-grow", "1");

        BigDecimal total = balance.getTotalEntitled();
        BigDecimal remaining = leaveBalanceService.getEffectiveBalance(balance);
        double used = total.subtract(remaining).doubleValue();
        double percent = (total.doubleValue() > 0) ? (used / total.doubleValue()) : 0;

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Span type = new Span(balance.getLeaveType().getCode());
        type.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.BOLD);

        Span left = new Span(remaining.toPlainString() + " Left");
        left.getElement().getThemeList().add("badge success");

        header.add(type, left);

        ProgressBar pb = new ProgressBar();
        pb.setValue(percent);
        pb.addClassNames(LumoUtility.Margin.Top.SMALL, LumoUtility.Margin.Bottom.XSMALL);
        if (percent > 0.8) pb.getStyle().set("--lumo-primary-color", "var(--lumo-error-color)");

        Span stats = new Span(String.format("Used: %,.1f / %,.1f", used, total.doubleValue()));
        stats.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY);

        card.add(header, pb, stats);
        return card;
    }
}
