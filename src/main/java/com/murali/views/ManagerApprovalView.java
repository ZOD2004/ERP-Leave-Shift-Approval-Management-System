package com.murali.views;

import com.murali.entity.*;
import com.murali.entity.enums.ApprovalType;
import com.murali.service.ApprovalRoutingService;
import com.murali.service.AttendanceCorrectionService;
import com.murali.service.LeaveBalanceService;
import com.murali.util.SecurityService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN", "ROLE_MANAGER", "ROLE_DEPT_HEAD"})
@PageTitle("Approval Inbox")
@Route(value = "approvals", layout = MainLayout.class)
public class ManagerApprovalView extends VerticalLayout {

    private final ApprovalRoutingService approvalRoutingService;
    private final AttendanceCorrectionService attendanceCorrectionService;
    private final SecurityService securityService;
    private final LeaveBalanceService leaveBalanceService;
    private final User currentUser;

    private final Grid<LeaveApproval> leaveGrid = new Grid<>(LeaveApproval.class, false);
    private final Grid<AttendanceCorrection> correctionGrid = new Grid<>(AttendanceCorrection.class, false);

    private final VerticalLayout leaveWrapper = new VerticalLayout();
    private final VerticalLayout correctionWrapper = new VerticalLayout();

    public ManagerApprovalView(ApprovalRoutingService approvalRoutingService, AttendanceCorrectionService attendanceCorrectionService, SecurityService securityService, LeaveBalanceService leaveBalanceService) {
        this.approvalRoutingService = approvalRoutingService;
        this.attendanceCorrectionService = attendanceCorrectionService;
        this.securityService = securityService;
        this.currentUser = securityService.getAuthenticatedUser();
        this.leaveBalanceService = leaveBalanceService;

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

        Tab leaveTab = new Tab(VaadinIcon.FLIGHT_TAKEOFF.create(), new Span(" Leave Requests"));
        Tab correctionTab = new Tab(VaadinIcon.CLOCK.create(), new Span(" Attendance Corrections"));
        Tabs tabs = new Tabs(leaveTab, correctionTab);
        tabs.setWidthFull();

        tabs.addSelectedChangeListener(event -> {
            boolean isLeaveTab = event.getSelectedTab().equals(leaveTab);
            leaveWrapper.setVisible(isLeaveTab);
            correctionWrapper.setVisible(!isLeaveTab);
        });

        leaveWrapper.setSizeFull();
        leaveWrapper.setPadding(false);
        leaveWrapper.add(createLeaveToolbar(), leaveGrid);

        correctionWrapper.setSizeFull();
        correctionWrapper.setPadding(false);
        correctionWrapper.setVisible(false);
        correctionWrapper.add(createCorrectionToolbar(), correctionGrid);

        add(title, tabs, leaveWrapper, correctionWrapper);
    }


    private HorizontalLayout createLeaveToolbar() {
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search employee name...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addValueChangeListener(e -> {
            leaveGrid.setItems(approvalRoutingService.getPendingApprovalsForUser(currentUser.getId()).stream().filter(a -> a.getLeaveRequest().getEmployee().getFirstName().toLowerCase().contains(e.getValue().toLowerCase())).toList());
        });

        HorizontalLayout toolbar = new HorizontalLayout(searchField);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return toolbar;
    }

    private void configureLeaveGrid() {
        leaveGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        leaveGrid.setSizeFull();
        leaveGrid.setPartNameGenerator(approval -> approval.getApprovalType() == ApprovalType.CANCELLATION ? "cancellation-row" : null);

        leaveGrid.addComponentColumn(approval -> createEmployeeBadge(approval.getLeaveRequest().getEmployee())).setHeader("Employee").setFlexGrow(1).setAutoWidth(true);

        leaveGrid.addComponentColumn(approval -> {
            VerticalLayout cell = new VerticalLayout();
            cell.setPadding(false);
            cell.setSpacing(false);

            Span leaveName = new Span(approval.getLeaveRequest().getLeaveType().getName());
            cell.add(leaveName);

            if (approval.getApprovalType() == ApprovalType.CANCELLATION) {
                Span cancelBadge = new Span("CANCELLATION REQ");
                cancelBadge.getElement().getThemeList().add("badge error small");
                cancelBadge.getStyle().set("margin-top", "4px");
                cell.add(cancelBadge);
            }
            return cell;
        }).setHeader("Leave Type").setAutoWidth(true);

        leaveGrid.addColumn(approval -> approval.getLeaveRequest().getStartDate() + " to " + approval.getLeaveRequest().getEndDate()).setHeader("Dates").setAutoWidth(true);

        leaveGrid.addColumn(approval -> approval.getLeaveRequest().getDurationDays() + " days").setHeader("Duration").setAutoWidth(true);

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
        dialog.setWidth("900px");
        dialog.setMaxWidth("95vw");

        LeaveRequest request = approval.getLeaveRequest();
        Component heroSection = createHeroSection(request, isCancellation);

        VerticalLayout contentLayout = new VerticalLayout();
        contentLayout.setPadding(false);
        contentLayout.setSpacing(true);


        Component reasonPanel = createReadOnlyPanel(isCancellation ? "Original Reason" : "Reason for Leave", request.getReason());

        TextArea commentsArea = new TextArea("Decision Notes");
        commentsArea.setPlaceholder("Give feedback/Reason.");
        commentsArea.setMinHeight("140px");
        commentsArea.setWidthFull();


        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(request.getEmployee().getId(), request.getStartDate().getYear());

        LeaveBalance targetBalance = null;
        for (LeaveBalance balance : balances) {
            if (balance.getLeaveType().getId().equals(request.getLeaveType().getId())) {
                targetBalance = balance;
                break;
            }
        }

        BigDecimal netDeduction = request.getDurationDays();

        if (request.getParentLeave() != null) {
            netDeduction = netDeduction.subtract(request.getParentLeave().getDurationDays());
        } else if (request.getMergedLeaves() != null && !request.getMergedLeaves().isEmpty()) {
            BigDecimal alreadyApproved = leaveBalanceService.calculateApprovedMergedDays(request);
            netDeduction = netDeduction.subtract(alreadyApproved);
        }

        if (netDeduction.compareTo(BigDecimal.ZERO) < 0) {
            netDeduction = BigDecimal.ZERO;
        }

        BigDecimal remainingAfterApproval = BigDecimal.ZERO;
        BigDecimal available = BigDecimal.ZERO;

        if (targetBalance != null) {
            remainingAfterApproval = leaveBalanceService.getEffectiveBalance(targetBalance);
            available = remainingAfterApproval.add(netDeduction);
        }
        Component impactCard = createImpactCard(request.getLeaveType(), available, netDeduction, remainingAfterApproval);

        Component statusBanner = createStatusBanner(remainingAfterApproval);

        VerticalLayout leftColumn = new VerticalLayout();
        leftColumn.setPadding(false);
        leftColumn.setSpacing(true);
        leftColumn.setWidth("60%");

        VerticalLayout rightColumn = new VerticalLayout();
        rightColumn.setPadding(false);
        rightColumn.setSpacing(true);
        rightColumn.setWidth("40%");

        Component requestPanel = createRequestDetailsPanel(request);

        leftColumn.add(requestPanel, reasonPanel, commentsArea);
        rightColumn.add(impactCard, statusBanner);

        HorizontalLayout body = new HorizontalLayout(leftColumn, rightColumn);
        body.setWidthFull();
        body.setSpacing(true);
        body.setAlignItems(FlexComponent.Alignment.START);

        contentLayout.add(heroSection, body);
        Scroller scroller = new Scroller(contentLayout);
        scroller.setSizeFull();
        scroller.getStyle().set("padding-right", "8px");

        dialog.add(scroller);


        String approveText = isCancellation ? "Approve Cancellation" : "Approve Leave";
        Button approveBtn = new Button(approveText, VaadinIcon.CHECK.create());
        approveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        if (remainingAfterApproval.compareTo(BigDecimal.ZERO) < 0) {
            approveBtn.setEnabled(false);
        }
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
            if (commentsArea.getValue() == null || commentsArea.getValue().trim().isEmpty()) {
                Notification.show("Rejection requires decision notes.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
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
        approveBtn.setMinWidth("180px");
        rejectBtn.setMinWidth("140px");

        HorizontalLayout footerLayout = new HorizontalLayout();
        footerLayout.setWidthFull();

        footerLayout.add(cancelBtn);
        footerLayout.addAndExpand(new Div());
        footerLayout.add(rejectBtn, approveBtn);

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
            correctionGrid.setItems(attendanceCorrectionService.getPendingCorrectionsForApprover(currentUser.getId()).stream().filter(c -> c.getAttendance().getEmployee().getFirstName().toLowerCase().contains(e.getValue().toLowerCase())).toList());
        });

        HorizontalLayout toolbar = new HorizontalLayout(searchField);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return toolbar;
    }

    private void configureCorrectionGrid() {
        correctionGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        correctionGrid.setSizeFull();

        correctionGrid.addComponentColumn(correction -> createEmployeeBadge(correction.getAttendance().getEmployee())).setHeader("Employee").setFlexGrow(1).setAutoWidth(true);

        correctionGrid.addColumn(correction -> correction.getAttendance().getAttendanceDate()).setHeader("Date").setAutoWidth(true);

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
        dialog.setWidth("600px");
        dialog.setMaxWidth("95vw");

        Attendance attendance = correction.getAttendance();

        LocalTime effectiveEnd = attendance.getExpectedEndTime();
        String expectedShiftName = attendance.getExpectedShiftName();

        VerticalLayout detailsLayout = new VerticalLayout();
        detailsLayout.setPadding(false);
        detailsLayout.setSpacing(false);
        detailsLayout.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        detailsLayout.add(createDetailRow("Employee:", attendance.getEmployee().getFirstName() + " (ID: " + attendance.getEmployee().getId() + ")"));
        detailsLayout.add(createDetailRow("Date:", attendance.getAttendanceDate().toString()));
        detailsLayout.add(createDetailRow("System Status:", attendance.getStatus().name()));

        if (expectedShiftName != null) {
            detailsLayout.add(createDetailRow("Expected Shift:", expectedShiftName));
        }

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

        if (effectiveEnd != null) {
            detailsLayout.add(createDetailRow("Expected Shift End:", effectiveEnd.toString()));
        }

        TimePicker manualCheckOutPicker = new TimePicker("Manual Check-out Time");
        manualCheckOutPicker.setWidthFull();

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
                LocalDateTime checkOutDateTime = attendance.getAttendanceDate().atTime(manualCheckOutPicker.getValue());

                attendanceCorrectionService.resolveCorrection(correction.getId(), "APPROVED", checkOutDateTime, commentsArea.getValue(), currentUser.getId());
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
                attendanceCorrectionService.resolveCorrection(correction.getId(), "REJECTED", null,
                        commentsArea.getValue(), currentUser.getId());
                Notification.show("Correction Rejected. Penalty applied.", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshCorrectionGrid();
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footerLayout = new HorizontalLayout();
        footerLayout.add(cancelBtn);
        footerLayout.addAndExpand(new Div());
        footerLayout.add(rejectBtn, approveBtn);
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

    private Component createHeroSection(LeaveRequest request, boolean isCancellation) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);
        header.addClassNames(LumoUtility.Padding.Bottom.MEDIUM, LumoUtility.Border.BOTTOM, LumoUtility.BorderColor.CONTRAST_10);

        Employee emp = request.getEmployee();

        Avatar avatar = new Avatar(emp.getFirstName());
        avatar.setAbbreviation(emp.getFirstName().substring(0, 1));
        avatar.getStyle().set("width", "64px");
        avatar.getStyle().set("height", "64px");

        VerticalLayout empInfo = new VerticalLayout();
        empInfo.setPadding(false);
        empInfo.setSpacing(false);

        Span name = new Span(emp.getFirstName() + " (" + emp.getEmployeeCode() + ")");
        name.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD);

        String deptName = emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned Department";
        Span dept = new Span(deptName);
        dept.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        HorizontalLayout badges = new HorizontalLayout();
        badges.getStyle().set("margin-top", "8px");
        badges.setSpacing(true);

        Span leaveTypeBadge = new Span(request.getLeaveType().getCode() + " LEAVE");
        leaveTypeBadge.getElement().getThemeList().add("badge contrast primary");
        badges.add(leaveTypeBadge);

        if (isCancellation) {
            Span cancelBadge = new Span("CANCELLATION");
            cancelBadge.getElement().getThemeList().add("badge error");
            badges.add(cancelBadge);
        }

        boolean isMerged = request.getParentLeave() != null || (request.getMergedLeaves() != null && !request.getMergedLeaves().isEmpty());
        if (isMerged) {
            Span mergedBadge = new Span("MERGED LEAVE");
            mergedBadge.getElement().getThemeList().add("badge success");
            badges.add(mergedBadge);
        }

        empInfo.add(name, dept, badges);
        header.add(avatar, empInfo);

        return header;
    }

    private Component createRequestDetailsPanel(LeaveRequest request) {
        VerticalLayout requestPanel = new VerticalLayout();
        requestPanel.setPadding(true);
        requestPanel.setSpacing(false);
        requestPanel.getStyle().set("border-radius", "12px");
        requestPanel.getStyle().set("background", "var(--lumo-contrast-5pct)");

        requestPanel.add(createDetailRow("Leave Type", request.getLeaveType().getName()));

        String dateStr = request.getStartDate() + " to " + request.getEndDate();
        requestPanel.add(createDetailRow("Dates", dateStr));

        if (!"FULL_DAY".equals(request.getStartSession().name()) || !"FULL_DAY".equals(request.getEndSession().name())) {
            String sessionStr = "Start: " + request.getStartSession().name() + " | End: " + request.getEndSession().name();
            requestPanel.add(createDetailRow("Sessions", sessionStr));
        }

        requestPanel.add(createDetailRow("Total Duration", request.getDurationDays().toPlainString() + " Days"));

        if (Boolean.TRUE.equals(request.getIsSandwichLeave()) && request.getSandwichPenaltyDays() != null && request.getSandwichPenaltyDays().compareTo(BigDecimal.ZERO) > 0) {

            HorizontalLayout penaltyRow = createDetailRow("Sandwich Penalty Included", "+" + request.getSandwichPenaltyDays().toPlainString() + " Days");
            penaltyRow.getStyle().set("color", "var(--lumo-error-text-color)");
            penaltyRow.getStyle().set("font-weight", "bold");

            penaltyRow.getStyle().set("margin-top", "8px");
            requestPanel.add(penaltyRow);
        }

        return requestPanel;
    }

    private Component createReadOnlyPanel(String title, String content) {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(true);
        panel.setSpacing(false);
        panel.getStyle().set("border-radius", "12px");
        panel.getStyle().set("background", "var(--lumo-contrast-5pct)");
        panel.setWidthFull();

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.BOLD, LumoUtility.TextColor.SECONDARY);
        titleSpan.getStyle().set("margin-bottom", "8px");

        Span contentSpan = new Span(content != null && !content.trim().isEmpty() ? content : "No notes provided by the employee.");
        contentSpan.addClassNames(LumoUtility.FontSize.MEDIUM);

        panel.add(titleSpan, contentSpan);
        return panel;
    }

    private Component createImpactCard(LeaveType leaveType, BigDecimal available, BigDecimal duration, BigDecimal remaining) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        card.getStyle().set("border-radius", "8px");

        Span title = new Span("Balance Impact: " + leaveType.getCode());
        title.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.BOLD, LumoUtility.TextColor.SECONDARY);
        title.getStyle().set("margin-bottom", "16px");

        HorizontalLayout mathLayout = new HorizontalLayout();
        mathLayout.setWidthFull();
        mathLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout availCol = new VerticalLayout(new Span("Available"), new Span(available.toPlainString()));
        availCol.setPadding(false);
        availCol.setSpacing(false);
        availCol.setAlignItems(FlexComponent.Alignment.CENTER);
        ((Span) availCol.getComponentAt(0)).addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        ((Span) availCol.getComponentAt(1)).addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);

        Span minus = new Span("-");
        minus.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD, LumoUtility.TextColor.SECONDARY);
        minus.getStyle().set("margin-top", "16px");

        VerticalLayout durCol = new VerticalLayout(new Span("Deduction"), new Span(duration.toPlainString()));
        durCol.setPadding(false);
        durCol.setSpacing(false);
        durCol.setAlignItems(FlexComponent.Alignment.CENTER);
        ((Span) durCol.getComponentAt(0)).addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        ((Span) durCol.getComponentAt(1)).addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);

        Span equals = new Span("=");
        equals.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD, LumoUtility.TextColor.SECONDARY);
        equals.getStyle().set("margin-top", "16px");

        VerticalLayout remCol = new VerticalLayout(new Span("Remaining"), new Span(remaining.toPlainString()));
        remCol.setPadding(false);
        remCol.setSpacing(false);
        remCol.setAlignItems(FlexComponent.Alignment.CENTER);
        ((Span) remCol.getComponentAt(0)).addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        ((Span) remCol.getComponentAt(1)).addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);

        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            ((Span) remCol.getComponentAt(1)).getStyle().set("color", "var(--lumo-error-text-color)");
        } else {
            ((Span) remCol.getComponentAt(1)).getStyle().set("color", "var(--lumo-success-text-color)");
        }

        mathLayout.add(availCol, minus, durCol, equals, remCol);
        card.add(title, mathLayout);

        return card;
    }

    private Component createStatusBanner(BigDecimal remainingAfterApproval) {
        HorizontalLayout banner = new HorizontalLayout();
        banner.setWidthFull();
        banner.setPadding(true);
        banner.setAlignItems(FlexComponent.Alignment.CENTER);
        banner.getStyle().set("border-radius", "8px");

        Icon icon;
        Span text;

        if (remainingAfterApproval.compareTo(BigDecimal.ZERO) < 0) {
            banner.getStyle().set("background", "var(--lumo-error-color-10pct)");
            banner.getStyle().set("color", "var(--lumo-error-text-color)");
            icon = VaadinIcon.WARNING.create();
            text = new Span("Insufficient balance. Approval disabled.");
        } else {
            banner.getStyle().set("background", "var(--lumo-success-color-10pct)");
            banner.getStyle().set("color", "var(--lumo-success-text-color)");
            icon = VaadinIcon.CHECK_CIRCLE.create();
            text = new Span("Sufficient balance to approve.");
        }

        icon.setSize("20px");
        text.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.FontWeight.BOLD);

        banner.add(icon, text);
        return banner;
    }
}