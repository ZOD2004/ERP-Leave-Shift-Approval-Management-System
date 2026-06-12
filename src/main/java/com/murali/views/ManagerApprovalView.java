package com.murali.views;

import com.murali.entity.*;
import com.murali.entity.enums.ApprovalType;
import com.murali.service.ApprovalRoutingService;
import com.murali.service.AttendanceCorrectionService;
import com.murali.util.SecurityService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDateTime;
import java.time.LocalTime;

@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN", "ROLE_MANAGER", "ROLE_DEPT_HEAD"})
@PageTitle("Approval Inbox")
@Route(value = "approvals", layout = MainLayout.class) // Adjust layout class if needed
public class ManagerApprovalView extends VerticalLayout {

    private final ApprovalRoutingService approvalRoutingService;
    private final AttendanceCorrectionService attendanceCorrectionService;
    private final SecurityService securityService;
    private final User currentUser;

    private final Grid<LeaveApproval> leaveGrid = new Grid<>(LeaveApproval.class, false);
    private final Grid<AttendanceCorrection> correctionGrid = new Grid<>(AttendanceCorrection.class, false);

    private final VerticalLayout leaveWrapper = new VerticalLayout();
    private final VerticalLayout correctionWrapper = new VerticalLayout();

    public ManagerApprovalView(ApprovalRoutingService approvalRoutingService,
                               AttendanceCorrectionService attendanceCorrectionService,
                               SecurityService securityService) {
        this.approvalRoutingService = approvalRoutingService;
        this.attendanceCorrectionService = attendanceCorrectionService;
        this.securityService = securityService;
        this.currentUser = securityService.getAuthenticatedUser();

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        buildUI();
        configureLeaveGrid();
        configureCorrectionGrid();

        refreshLeaveGrid();
        refreshCorrectionGrid();
    }

    private void buildUI() {
        H2 title = new H2("Approval Inbox");
        title.addClassNames(LumoUtility.Margin.NONE);

        // --- Tabs Setup ---
        Tab leaveTab = new Tab(VaadinIcon.FLIGHT_TAKEOFF.create(), new Span(" Leave Requests"));
        Tab correctionTab = new Tab(VaadinIcon.CLOCK.create(), new Span(" Attendance Corrections"));
        Tabs tabs = new Tabs(leaveTab, correctionTab);
        tabs.setWidthFull();

        tabs.addSelectedChangeListener(event -> {
            boolean isLeaveTab = event.getSelectedTab().equals(leaveTab);
            leaveWrapper.setVisible(isLeaveTab);
            correctionWrapper.setVisible(!isLeaveTab);
        });

        // --- Leave Wrapper Setup ---
        leaveWrapper.setSizeFull();
        leaveWrapper.setPadding(false);
        leaveWrapper.add(createLeaveToolbar(), leaveGrid);

        // --- Correction Wrapper Setup ---
        correctionWrapper.setSizeFull();
        correctionWrapper.setPadding(false);
        correctionWrapper.setVisible(false); // Hidden by default
        correctionWrapper.add(createCorrectionToolbar(), correctionGrid);

        add(title, tabs, leaveWrapper, correctionWrapper);
    }

    // ==========================================
    // LEAVE APPROVAL LOGIC
    // ==========================================

    private HorizontalLayout createLeaveToolbar() {
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search employee name...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addValueChangeListener(e -> {
            leaveGrid.setItems(approvalRoutingService.getPendingApprovalsForUser(currentUser.getId()).stream()
                    .filter(a -> a.getLeaveRequest().getEmployee().getFirstName().toLowerCase().contains(e.getValue().toLowerCase()))
                    .toList());
        });

        HorizontalLayout toolbar = new HorizontalLayout(searchField);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return toolbar;
    }

    private void configureLeaveGrid() {
        leaveGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        leaveGrid.setSizeFull();

        leaveGrid.addComponentColumn(approval -> createEmployeeBadge(approval.getLeaveRequest().getEmployee()))
                .setHeader("Employee").setFlexGrow(1).setAutoWidth(true);

        // MODIFIED: Show a badge if it is a Cancellation Request
        leaveGrid.addComponentColumn(approval -> {
            VerticalLayout cell = new VerticalLayout();
            cell.setPadding(false);
            cell.setSpacing(false);

            Span leaveName = new Span(approval.getLeaveRequest().getLeaveType().getName());
            cell.add(leaveName);

            // Assuming your enum is imported: ApprovalType.CANCELLATION
            if (approval.getApprovalType() == ApprovalType.CANCELLATION) {
                Span cancelBadge = new Span("CANCELLATION REQ");
                cancelBadge.getElement().getThemeList().add("badge error small");
                cancelBadge.getStyle().set("margin-top", "4px");
                cell.add(cancelBadge);
            }
            return cell;
        }).setHeader("Leave Type").setAutoWidth(true);

        leaveGrid.addColumn(approval -> approval.getLeaveRequest().getStartDate() + " to " + approval.getLeaveRequest().getEndDate())
                .setHeader("Dates").setAutoWidth(true);

        leaveGrid.addColumn(approval -> approval.getLeaveRequest().getDurationDays() + " days")
                .setHeader("Duration").setAutoWidth(true);

        leaveGrid.addComponentColumn(approval -> {
            Span badge = new Span("Level " + approval.getApprovalLevel());
            badge.getElement().getThemeList().add("badge pill contrast");
            return badge;
        }).setHeader("Tier").setAutoWidth(true);

        leaveGrid.addComponentColumn(approval -> {
            Button reviewBtn = new Button("Review", VaadinIcon.SEARCH.create());
            reviewBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            reviewBtn.addClickListener(e -> openLeaveReviewDialog(approval));
            return reviewBtn;
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
    }

    private void openLeaveReviewDialog(LeaveApproval approval) {
        Dialog dialog = new Dialog();

        boolean isCancellation = approval.getApprovalType() == ApprovalType.CANCELLATION;
        dialog.setHeaderTitle(isCancellation ? "Review Cancellation Request" : "Review Leave Request");
        dialog.setWidth("450px");
        dialog.setMaxHeight("90vh"); // FIX 1: Prevents dialog from stretching off-screen

        LeaveRequest request = approval.getLeaveRequest();

        // FIX 2: Create a master wrapper for all the content so it can scroll
        VerticalLayout contentLayout = new VerticalLayout();
        contentLayout.setPadding(false);
        contentLayout.setSpacing(true); // Adds a little breathing room between sections

        VerticalLayout detailsLayout = new VerticalLayout();
        detailsLayout.setPadding(false);
        detailsLayout.setSpacing(false);
        detailsLayout.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        if (isCancellation) {
            VerticalLayout warningBanner = new VerticalLayout();
            warningBanner.addClassNames(LumoUtility.Background.ERROR_10, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.SMALL, LumoUtility.Margin.Bottom.MEDIUM);
            Span warnIcon = new Span(VaadinIcon.WARNING.create());
            warnIcon.getStyle().set("color", "var(--lumo-error-color)");
            Span warnText = new Span(" The employee is requesting to CANCEL this already approved leave.");
            warnText.addClassNames(LumoUtility.TextColor.ERROR, LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.BOLD);
            warningBanner.add(new HorizontalLayout(warnIcon, warnText));
            detailsLayout.add(warningBanner);
        }

        detailsLayout.add(createDetailRow("Employee ID:", String.valueOf(request.getEmployee().getId())));
        detailsLayout.add(createDetailRow("Leave Type:", request.getLeaveType().getName()));
        detailsLayout.add(createDetailRow("Dates:", request.getStartDate() + " to " + request.getEndDate()));
        detailsLayout.add(createDetailRow("Duration:", request.getDurationDays() + " days"));

        contentLayout.add(detailsLayout);

        TextArea reasonDisplay = new TextArea(isCancellation ? "Original Reason" : "Employee Reason");
        reasonDisplay.setValue(request.getReason() != null ? request.getReason() : "N/A");
        reasonDisplay.setReadOnly(true);
        reasonDisplay.setWidthFull();
        contentLayout.add(reasonDisplay);

        TextArea commentsArea = new TextArea("Approver Feedback");
        commentsArea.setPlaceholder("Required if rejecting...");
        commentsArea.setWidthFull();
        contentLayout.add(commentsArea);

        // FIX 3: Put the master content layout inside a Scroller
        Scroller scroller = new Scroller(contentLayout);
        scroller.setSizeFull();
        scroller.getStyle().set("padding-right", "8px"); // Prevents scrollbar from covering text

        // Add the Scroller to the dialog instead of the individual pieces
        dialog.add(scroller);

        String approveText = isCancellation ? "Approve Cancellation" : "Approve Leave";
        Button approveBtn = new Button(approveText, VaadinIcon.CHECK.create());
        approveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        approveBtn.addClickListener(e -> {
            try {
                approvalRoutingService.processApprovalAction(approval.getId(), "APPROVED", commentsArea.getValue(), currentUser);
                Notification.show("Approved successfully", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshLeaveGrid();
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        String rejectText = isCancellation ? "Reject (Keep Leave)" : "Reject Leave";
        Button rejectBtn = new Button(rejectText, VaadinIcon.CLOSE.create());
        rejectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        rejectBtn.addClickListener(e -> {
            try {
                approvalRoutingService.processApprovalAction(approval.getId(), "REJECTED", commentsArea.getValue(), currentUser);
                Notification.show("Rejected successfully", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshLeaveGrid();
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelBtn = new Button("Close", e -> dialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footerLayout = new HorizontalLayout(cancelBtn, rejectBtn, approveBtn);
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footerLayout.setWidthFull();

        dialog.getFooter().add(footerLayout);
        dialog.open();
    }

    private void refreshLeaveGrid() {
        leaveGrid.setItems(approvalRoutingService.getPendingApprovalsForUser(currentUser.getId()));
    }



    private HorizontalLayout createCorrectionToolbar() {
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search employee name...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addValueChangeListener(e -> {
            // Note: Replace getPendingCorrectionsForApprover with your actual manager-specific fetch method
            correctionGrid.setItems(attendanceCorrectionService.getPendingCorrectionsForApprover(currentUser.getId()).stream()
                    .filter(c -> c.getAttendance().getEmployee().getFirstName().toLowerCase().contains(e.getValue().toLowerCase()))
                    .toList());
        });

        HorizontalLayout toolbar = new HorizontalLayout(searchField);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return toolbar;
    }

    private void configureCorrectionGrid() {
        correctionGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        correctionGrid.setSizeFull();

        correctionGrid.addComponentColumn(correction -> createEmployeeBadge(correction.getAttendance().getEmployee()))
                .setHeader("Employee").setFlexGrow(1).setAutoWidth(true);

        correctionGrid.addColumn(correction -> correction.getAttendance().getAttendanceDate())
                .setHeader("Date").setAutoWidth(true);

        correctionGrid.addComponentColumn(correction -> {
            Span badge = new Span(correction.getAttendance().getStatus().name());
            badge.getElement().getThemeList().add("badge error");
            return badge;
        }).setHeader("Issue Type").setAutoWidth(true);

        correctionGrid.addColumn(correction -> {
            LocalDateTime in = correction.getAttendance().getFirstCheckIn();
            return in != null ? in.toLocalTime().toString() : "Missing";
        }).setHeader("Check-In").setAutoWidth(true);

        correctionGrid.addComponentColumn(correction -> {
            Button reviewBtn = new Button("Resolve", VaadinIcon.TOOLS.create());
            reviewBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            reviewBtn.addClickListener(e -> openCorrectionDialog(correction));
            return reviewBtn;
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
    }

    private void openCorrectionDialog(AttendanceCorrection correction) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Resolve Missing Check-out");
        dialog.setWidth("450px");

        Attendance attendance = correction.getAttendance();
        ShiftAssignment assignment = attendance.getShiftAssignment();

        VerticalLayout detailsLayout = new VerticalLayout();
        detailsLayout.setPadding(false);
        detailsLayout.setSpacing(false);
        detailsLayout.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        detailsLayout.add(createDetailRow("Employee:", attendance.getEmployee().getFirstName() + " (ID: " + attendance.getEmployee().getId() + ")"));
        detailsLayout.add(createDetailRow("Date:", attendance.getAttendanceDate().toString()));
        detailsLayout.add(createDetailRow("System Status:", attendance.getStatus().name()));
        VerticalLayout infoBanner = new VerticalLayout();
        infoBanner.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.SMALL, LumoUtility.Margin.Top.SMALL);
        infoBanner.setSpacing(false);

        Span infoTitle = new Span(VaadinIcon.INFO_CIRCLE.create(), new Span(" What happens next?"));
        infoTitle.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.FontSize.SMALL, LumoUtility.TextColor.PRIMARY);

        Span infoApprove = new Span("• Approve: Employee gets full credit for the day. No penalty.");
        Span infoReject = new Span("• Reject: Employee is marked Absent. 0.5 Half-Day leave is deducted.");
        infoApprove.addClassNames(LumoUtility.FontSize.XSMALL);
        infoReject.addClassNames(LumoUtility.FontSize.XSMALL);

        infoBanner.add(infoTitle, infoApprove, infoReject);
        detailsLayout.add(infoBanner);

        LocalTime effectiveEnd = (assignment != null) ? assignment.getShift().getEndTime(): null;
        if (effectiveEnd != null) {
            detailsLayout.add(createDetailRow("Expected Shift End:", effectiveEnd.toString()));
        }

        // Time Picker for check-out
        TimePicker manualCheckOutPicker = new TimePicker("Manual Check-out Time");
        manualCheckOutPicker.setWidthFull();
        // PRE-FILL: Automatically set the check-out time to the employee's expected shift end time
        if (effectiveEnd != null) {
            manualCheckOutPicker.setValue(effectiveEnd);
        }

        TextArea commentsArea = new TextArea("Manager Comments");
        commentsArea.setWidthFull();

        Button approveBtn = new Button("Approve (Waive Penalty)", VaadinIcon.CHECK.create());
        approveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        approveBtn.addClickListener(e -> {
            if (manualCheckOutPicker.getValue() == null) {
                Notification.show("Please provide a check-out time.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                // Combine attendance date with the manually selected time
                LocalDateTime checkOutDateTime = attendance.getAttendanceDate().atTime(manualCheckOutPicker.getValue());

                attendanceCorrectionService.resolveCorrection(
                        correction.getId(),
                        "APPROVED",
                        checkOutDateTime,
                        commentsArea.getValue(),
                        currentUser.getId()
                );
                Notification.show("Correction Approved.", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshCorrectionGrid();
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button rejectBtn = new Button("Reject (Apply Penalty)", VaadinIcon.CLOSE.create());
        rejectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        rejectBtn.addClickListener(e -> {
            try {
                attendanceCorrectionService.resolveCorrection(
                        correction.getId(),
                        "REJECTED",
                        null, // Not needed for rejection
                        commentsArea.getValue(),
                        currentUser.getId()
                );
                Notification.show("Correction Rejected. Penalty applied.", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshCorrectionGrid();
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footerLayout = new HorizontalLayout(cancelBtn, rejectBtn, approveBtn);
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footerLayout.setWidthFull();

        dialog.add(detailsLayout, manualCheckOutPicker, commentsArea);
        dialog.getFooter().add(footerLayout);
        dialog.open();
    }

    private void refreshCorrectionGrid() {
        correctionGrid.setItems(attendanceCorrectionService.getPendingCorrectionsForApprover(currentUser.getId()));
    }

    private Component createEmployeeBadge(Employee emp) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        Avatar avatar = new Avatar(emp.getFirstName());
        avatar.setAbbreviation(emp.getFirstName().substring(0, 1));

        VerticalLayout info = new VerticalLayout(new Span(emp.getFirstName()), new Span("ID: " + emp.getId()));
        info.setSpacing(false);
        info.setPadding(false);
        info.addClassName(LumoUtility.FontSize.XSMALL);
        ((Span) info.getChildren().findFirst().get()).addClassName(LumoUtility.FontWeight.BOLD);

        layout.add(avatar, info);
        return layout;
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
}