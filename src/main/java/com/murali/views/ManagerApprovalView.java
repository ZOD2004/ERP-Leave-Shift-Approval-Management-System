package com.murali.views;

import com.murali.entity.Employee;
import com.murali.entity.LeaveApproval;
import com.murali.entity.LeaveRequest;
import com.murali.entity.User;
import com.murali.service.ApprovalRoutingService;
import com.murali.service.SecurityService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.LocalDateRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN","ROLE_MANAGER","ROLE_DEPT_HEAD"})
@PageTitle("Approval Inbox")
@Route(value = "approvals", layout = MainLayout.class)
public class ManagerApprovalView extends VerticalLayout {

    private final ApprovalRoutingService approvalRoutingService;
    private final SecurityService securityService;

    private final Grid<LeaveApproval> grid = new Grid<>(LeaveApproval.class, false);
    private User currentUser;

    public ManagerApprovalView(ApprovalRoutingService approvalRoutingService, SecurityService securityService) {
        this.approvalRoutingService = approvalRoutingService;
        this.securityService = securityService;

        this.currentUser = securityService.getAuthenticatedUser();

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        buildUI();
        configureGrid();
        refreshGrid();
    }

    private void buildUI() {
        H2 title = new H2("Approval Inbox");
        title.addClassNames(LumoUtility.Margin.NONE);

        HorizontalLayout statsHeader = new HorizontalLayout(
                createStatsCard("Pending", String.valueOf(grid.getListDataView().getItemCount()), VaadinIcon.CLOCK, "var(--lumo-primary-color)")
        );
        statsHeader.setWidthFull();

        TextField searchField = new TextField();
        searchField.setPlaceholder("Search employee name...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addValueChangeListener(e -> {

            grid.setItems(approvalRoutingService.getPendingApprovalsForUser(currentUser.getId()).stream()
                    .filter(a -> a.getLeaveRequest().getEmployee().getFirstName().toLowerCase().contains(e.getValue().toLowerCase()))
                    .toList());
        });

        HorizontalLayout toolbar = new HorizontalLayout(title, searchField);
        toolbar.setWidthFull();
        toolbar.expand(searchField);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

        add(toolbar, statsHeader, grid);
        expand(grid);
    }


    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();

        grid.addComponentColumn(approval -> {
            Employee emp = approval.getLeaveRequest().getEmployee();
            HorizontalLayout layout = new HorizontalLayout();
            layout.setAlignItems(FlexComponent.Alignment.CENTER);

            Avatar avatar = new Avatar(emp.getFirstName());
            avatar.setAbbreviation(emp.getFirstName().substring(0, 1));

            VerticalLayout info = new VerticalLayout(new Span(emp.getFirstName()), new Span("ID: " + emp.getId()));
            info.setSpacing(false);
            info.setPadding(false);
            info.addClassName(LumoUtility.FontSize.XSMALL);
            ((Span)info.getChildren().findFirst().get()).addClassName(LumoUtility.FontWeight.BOLD);

            layout.add(avatar, info);
            return layout;
        }).setHeader("Employee").setFlexGrow(1).setAutoWidth(true);

        grid.addColumn(approval -> approval.getLeaveRequest().getLeaveType().getName())
                .setHeader("Leave Type")
                .setAutoWidth(true);

        grid.addColumn(approval -> approval.getLeaveRequest().getStartDate() + " to " + approval.getLeaveRequest().getEndDate())
                .setHeader("Dates")
                .setAutoWidth(true);

        grid.addColumn(approval -> approval.getLeaveRequest().getDurationDays() + " days")
                .setHeader("Duration")
                .setAutoWidth(true);

        grid.addComponentColumn(approval -> {
            Span badge = new Span("Level " + approval.getApprovalLevel());
            badge.getElement().getThemeList().add("badge pill contrast");
            return badge;
        }).setHeader("Tier").setAutoWidth(true);

        grid.addComponentColumn(this::createActionButtons)
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private Component createActionButtons(LeaveApproval approval) {
        Button reviewBtn = new Button("Review", VaadinIcon.SEARCH.create());
        reviewBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        reviewBtn.addClickListener(e -> openReviewDialog(approval));
        return reviewBtn;
    }

    private void openReviewDialog(LeaveApproval approval) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Review Leave Request");
        dialog.setWidth("450px");

        VerticalLayout conflictAlert = new VerticalLayout();
        conflictAlert.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.SMALL);

        Span alertTitle = new Span(VaadinIcon.USERS.create(), new Span(" Team Availability"));
        alertTitle.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.FontSize.SMALL, LumoUtility.TextColor.PRIMARY);

        Span conflictText = new Span("2 other team members are also away during this period.");
        conflictText.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        conflictAlert.add(alertTitle, conflictText);

        LeaveRequest request = approval.getLeaveRequest();

        VerticalLayout detailsLayout = new VerticalLayout();
        detailsLayout.setPadding(false);
        detailsLayout.setSpacing(false);
        detailsLayout.addClassNames(LumoUtility.Margin.Bottom.LARGE);

        detailsLayout.add(createDetailRow("Employee ID:", String.valueOf(request.getEmployee().getId())));
        detailsLayout.add(createDetailRow("Leave Type:", request.getLeaveType().getName()));
        detailsLayout.add(createDetailRow("Dates:", request.getStartDate() + " to " + request.getEndDate()));
        detailsLayout.add(createDetailRow("Duration:", request.getDurationDays() + " days"));
        detailsLayout.add(createDetailRow("Reason:", request.getReason()));

        TextArea reasonDisplay = new TextArea("Employee Reason");
        reasonDisplay.setValue(approval.getLeaveRequest().getReason());
        reasonDisplay.setReadOnly(true);
        reasonDisplay.setWidthFull();

        TextArea commentsArea = new TextArea("Approver Feedback");
        commentsArea.setPlaceholder("Required if rejecting...");
        commentsArea.setWidthFull();

        // Action Buttons
        Button approveBtn = new Button("Approve", VaadinIcon.CHECK.create());
        approveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        approveBtn.addClickListener(e -> {
            processAction(approval, "APPROVED", commentsArea.getValue(), dialog);
        });

        Button rejectBtn = new Button("Reject", VaadinIcon.CLOSE.create());
        rejectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        rejectBtn.addClickListener(e -> {
            processAction(approval, "REJECTED", commentsArea.getValue(), dialog);
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);


        HorizontalLayout footerLayout = new HorizontalLayout(cancelBtn, rejectBtn, approveBtn);
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footerLayout.setWidthFull();

        dialog.add(detailsLayout, commentsArea);
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

    private void processAction(LeaveApproval approval, String action, String comments, Dialog dialog) {
        try {
            approvalRoutingService.processApprovalAction(approval.getId(), action.toUpperCase(), comments, currentUser);

            Notification.show("Request " + action.toLowerCase() + " successfully", 3000, Notification.Position.TOP_END)
                    .addThemeVariants(action.equals("APPROVED") ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_ERROR);

            dialog.close();
            refreshGrid();

        } catch (Exception ex) {
            Notification.show("Error processing request: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void refreshGrid() {
        List<LeaveApproval> pendingItems = approvalRoutingService.getPendingApprovalsForUser(currentUser.getId());
        grid.setItems(pendingItems);
    }
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
        card.setWidth("250px");

        // Icon setup
        Icon iconComp = icon.create();
        iconComp.getStyle().set("color", color);
        iconComp.addClassName(LumoUtility.FontSize.XLARGE);

        // Text setup
        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.MEDIUM);

        H3 valueH3 = new H3(value);
        valueH3.addClassNames(LumoUtility.Margin.NONE);

        HorizontalLayout header = new HorizontalLayout(iconComp, titleSpan);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);

        card.add(header, valueH3);
        return card;
    }
}