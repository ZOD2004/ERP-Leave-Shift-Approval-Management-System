package com.murali.views;

import com.murali.entity.*;
import com.murali.entity.enums.LeaveSession;
import com.murali.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN", "ROLE_MANAGER", "ROLE_DEPT_HEAD", "ROLE_EMPLOYEE", "ROLE_AUDITOR"})
@PageTitle("Leave Dashboard")
@Route(value = "apply-leave", layout = MainLayout.class)
public class LeaveApplicationView extends VerticalLayout {

    // Services
    private final LeaveRequestService leaveRequestService;
    private final AttendanceSyncService attendanceSyncService;
    private final LeaveTypeService leaveTypeService;
    private final EmployeeService employeeService;
    private final DurationEngineService durationEngineService;
    private final SecurityService securityService;
    private final LeaveBalanceService leaveBalanceService;
    private final ApprovalRoutingService approvalRoutingService;

    private final Employee currentEmployee;
    private final LeaveRequest currentRequest = new LeaveRequest();
    private final Binder<LeaveRequest> binder = new Binder<>(LeaveRequest.class);

    private final ComboBox<LeaveType> leaveType = new ComboBox<>("Leave Type");
    private final DatePicker startDate = new DatePicker("Start Date");
    private final DatePicker endDate = new DatePicker("End Date");
    private final NumberField durationDays = new NumberField("Net Duration (Days)");
    private final TextArea reason = new TextArea("Reason for Leave");
    private final RadioButtonGroup<LeaveSession> leaveSessionGroup = new RadioButtonGroup<>("Session");

    private final Grid<LeaveRequest> historyGrid = new Grid<>(LeaveRequest.class, false);
    private final HorizontalLayout balanceLayout = new HorizontalLayout();

    private final Grid<LeaveRequest> draftGrid = new Grid<>(LeaveRequest.class, false);
    private final VerticalLayout draftSection = new VerticalLayout();
    private LeaveRequest currentDraft = null;

    public LeaveApplicationView(LeaveRequestService leaveRequestService,
                                AttendanceSyncService attendanceSyncService,
                                LeaveTypeService leaveTypeService,
                                EmployeeService employeeService,
                                DurationEngineService durationEngineService,
                                SecurityService securityService,
                                LeaveBalanceService leaveBalanceService, ApprovalRoutingService approvalRoutingService) {

        this.leaveRequestService = leaveRequestService;
        this.attendanceSyncService = attendanceSyncService;
        this.leaveTypeService = leaveTypeService;
        this.employeeService = employeeService;
        this.durationEngineService = durationEngineService;
        this.securityService = securityService;
        this.leaveBalanceService = leaveBalanceService;
        this.currentEmployee = securityService.getCurrentEmployee();
        this.approvalRoutingService = approvalRoutingService;

        buildMainView();
        setupBinder();
        setupDateCalculations();
        refreshBalanceAndHistory();
    }

    private void buildMainView() {
        addClassNames(LumoUtility.Padding.LARGE);
        setSizeFull();

        H2 title = new H2("My Leave Dashboard");
        title.addClassNames(LumoUtility.Margin.NONE);

        Button applyLeaveBtn = new Button("Apply Leave", VaadinIcon.PLUS.create());
        applyLeaveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        applyLeaveBtn.addClickListener(e -> openApplyLeaveDialog(null));

        HorizontalLayout header = new HorizontalLayout(title, applyLeaveBtn);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        add(header, createBalanceSection(),createDraftSection(), createHistorySection());
    }

    private Component createBalanceSection() {
        balanceLayout.setWidthFull();
        balanceLayout.addClassNames(LumoUtility.FlexWrap.WRAP, LumoUtility.Gap.MEDIUM);

        VerticalLayout wrapper = new VerticalLayout(new H3("Leave Balances"), balanceLayout);
        wrapper.setPadding(false);
        return wrapper;
    }

    private Component createHistorySection() {
        H3 title = new H3("Recent Requests");
        title.addClassNames(LumoUtility.Margin.Top.LARGE, LumoUtility.Margin.Bottom.SMALL);

        historyGrid.addColumn(LeaveRequest::getStartDate)
                .setHeader("Start")
                .setAutoWidth(true);

        historyGrid.addColumn(req -> req.getLeaveType() != null ? req.getLeaveType().getName() : "")
                .setHeader("Type")
                .setAutoWidth(true);

        historyGrid.addColumn(LeaveRequest::getDurationDays)
                .setHeader("Days")
                .setAutoWidth(true);

        historyGrid.addComponentColumn(req -> {
            Span badge = new Span(req.getStatus());
            badge.getElement().getThemeList().add("badge pill");
            String status = req.getStatus() != null ? req.getStatus().toUpperCase() : "";
            if ("APPROVED".equals(status)) {
                badge.getElement().getThemeList().add("success");
            } else if ("REJECTED".equals(status)) {
                badge.getElement().getThemeList().add("error");
            } else if (status.startsWith("PENDING")) {
                badge.getElement().getThemeList().add("warning");
            } else if ("CANCELLED".equals(status)) {
                badge.getElement().getThemeList().add("contrast");
            }
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        historyGrid.addComponentColumn(req -> {
            Button viewCommentsBtn = new Button("View Comments");
            viewCommentsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            viewCommentsBtn.addClickListener(e -> showCommentsDialog(req));
            return viewCommentsBtn;
        }).setHeader("Comments").setAutoWidth(true);

        historyGrid.addComponentColumn(this::createActionColumn)
                .setHeader("Actions")
                .setAutoWidth(true)
                .setFlexGrow(0);

        historyGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        historyGrid.setSizeFull();

        VerticalLayout wrapper = new VerticalLayout(title, historyGrid);
        wrapper.setPadding(false);
        wrapper.setSizeFull();

        expand(wrapper);
        return wrapper;
    }

    private Component createActionColumn(LeaveRequest request) {
        String status = request.getStatus() != null ? request.getStatus().toUpperCase() : "";

        if ("REJECTED".equals(status) || "CANCELLED".equals(status)) {
            Button disabledBtn = new Button("Cancel");
            disabledBtn.setEnabled(false);
            disabledBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            return disabledBtn;
        }

        Button cancelBtn = new Button("Cancel");
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        cancelBtn.addClickListener(e -> openConfirmationDialog(request));
        return cancelBtn;
    }

    private void openConfirmationDialog(LeaveRequest request) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Cancel Leave Request");

        Span warningMessage = new Span(String.format(
                "Are you sure you want to cancel your %s leave request from %s to %s?",
                request.getLeaveType().getName(), request.getStartDate(), request.getEndDate()
        ));
        confirmDialog.add(new VerticalLayout(warningMessage));

        Button confirmBtn = new Button("Yes, Cancel It", e -> {
            try {
                int currentYear = LocalDate.now().getYear();

                leaveRequestService.cancelLeaveRequest(request.getId(), currentEmployee.getId(), currentYear);


                Notification.show("Leave request cancelled successfully.", 3000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                confirmDialog.close();
                refreshBalanceAndHistory();

            } catch (Exception ex) {
                Notification.show("Cancellation failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button dismissBtn = new Button("No, Keep It", e -> confirmDialog.close());
        dismissBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        confirmDialog.getFooter().add(dismissBtn, confirmBtn);
        confirmDialog.open();
    }

    private Component createBalanceCard(String title, BigDecimal remaining, double used, BigDecimal total, String themeColor, VaadinIcon iconType) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(
                LumoUtility.Background.BASE,
                LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.Padding.LARGE,
                LumoUtility.BoxShadow.SMALL
        );
        card.setWidth("280px");
        card.setSpacing(false);

        Icon icon = iconType.create();
        icon.addClassNames("text-" + themeColor);
        icon.getStyle().set("padding", "8px");
        icon.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
        icon.getStyle().set("border-radius", "50%");

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.FontWeight.BOLD, LumoUtility.TextColor.SECONDARY);

        HorizontalLayout headerLayout = new HorizontalLayout(titleSpan, icon);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);

        Span valueSpan = new Span(remaining.stripTrailingZeros().toPlainString());
        valueSpan.addClassNames(LumoUtility.FontWeight.BLACK, "text-" + themeColor);
        valueSpan.getStyle().set("line-height", "1");
        valueSpan.getStyle().set("font-size", "3rem");

        Span daysLabel = new Span("Days Left");
        daysLabel.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.Left.SMALL, LumoUtility.FontWeight.MEDIUM);

        HorizontalLayout numberLayout = new HorizontalLayout(valueSpan, daysLabel);
        numberLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
        numberLayout.addClassNames(LumoUtility.Margin.Top.LARGE, LumoUtility.Margin.Bottom.MEDIUM);

        ProgressBar progressBar = new ProgressBar();
        progressBar.setMin(0);
        progressBar.setMax(total.doubleValue() > 0 ? total.doubleValue() : 1);
        progressBar.setValue(used);

        progressBar.getElement().getThemeList().add(themeColor);

        if (remaining.doubleValue() <= 3.0 && remaining.doubleValue() > 0) {
            progressBar.getElement().getThemeList().add("error");
            valueSpan.addClassNames(LumoUtility.TextColor.ERROR);
        }

        Span statsSpan = new Span(String.format("%s used of %s total",
                BigDecimal.valueOf(used).stripTrailingZeros().toPlainString(),
                total.stripTrailingZeros().toPlainString()));
        statsSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY, LumoUtility.FontWeight.MEDIUM);

        HorizontalLayout footerLayout = new HorizontalLayout(statsSpan);
        footerLayout.setWidthFull();
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footerLayout.addClassNames(LumoUtility.Margin.Top.XSMALL);

        card.add(headerLayout, numberLayout, progressBar, footerLayout);
        return card;
    }
    private void openApplyLeaveDialog(LeaveRequest draftToEdit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(draftToEdit == null ? "New Leave Request" : "Resume Draft");
        dialog.setWidth("500px");

        this.currentDraft = draftToEdit;

        // 1. Initialize Form Components
        leaveType.setItems(leaveTypeService.getAvailableLeaveTypes());
        leaveType.setItemLabelGenerator(LeaveType::getName);

        durationDays.setReadOnly(true);
        durationDays.setPrefixComponent(VaadinIcon.CLOCK.create());
        durationDays.setHelperText("Excludes weekends/holidays");
        reason.setMinHeight("100px");

        leaveSessionGroup.setItems(LeaveSession.FIRST_HALF, LeaveSession.SECOND_HALF,LeaveSession.FULL_DAY);
        leaveSessionGroup.setItemLabelGenerator(session ->
                session == LeaveSession.FIRST_HALF ? "1st Half (Morning off)" : "2nd Half (Afternoon off)"
        );
        leaveSessionGroup.setVisible(false);

        // 2. If resuming a draft, populate the fields
        if (draftToEdit != null) {
            leaveType.setValue(draftToEdit.getLeaveType());
            startDate.setValue(draftToEdit.getStartDate());
            endDate.setValue(draftToEdit.getEndDate());
            reason.setValue(draftToEdit.getReason());
            leaveSessionGroup.setValue(draftToEdit.getLeaveSession());

            // Re-trigger visibility logic for half days
            if (isHalfDayType(draftToEdit.getLeaveType())) {
                leaveSessionGroup.setVisible(true);
                endDate.setVisible(false);
            }
            calculateDuration();
        } else {
            // Ensure form is fresh for new requests
            clearForm();
        }

        // 3. Build Form Layout
        FormLayout formLayout = new FormLayout();
        formLayout.add(leaveType, 2);
        formLayout.add(leaveSessionGroup, 2);
        formLayout.add(startDate, 1);
        formLayout.add(endDate, 1);
        formLayout.add(durationDays, 2);
        formLayout.add(reason, 2);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        Span infoNote = new Span(VaadinIcon.INFO_CIRCLE.create(), new Span(" Routed to immediate manager."));
        infoNote.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY,
                LumoUtility.Display.FLEX, LumoUtility.Gap.XSMALL, LumoUtility.Margin.Top.SMALL);

        VerticalLayout dialogLayout = new VerticalLayout(formLayout, infoNote);
        dialogLayout.setPadding(false);
        dialog.add(dialogLayout);

        // 4. Create Buttons & Logic
        Button saveDraftBtn = new Button("Save as Draft", e -> {
            // Basic validation because DB requires dates and type
            if (leaveType.getValue() == null || startDate.getValue() == null || endDate.getValue() == null) {
                Notification.show("Please select Leave Type and Dates to save a draft.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            try {
                Long draftId = currentDraft != null ? currentDraft.getId() : null;
                leaveRequestService.saveOrUpdateDraft(
                        draftId, currentEmployee, leaveType.getValue(),
                        startDate.getValue(), endDate.getValue(),
                        reason.getValue(), leaveSessionGroup.getValue()
                );
                Notification.show("Draft saved successfully.", 3000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                this.currentDraft = null;
                dialog.close();
                refreshBalanceAndHistory();
            } catch (Exception ex) {
                Notification.show("Failed to save draft: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveDraftBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button submitBtn = new Button("Submit", e -> {
            if (attemptSubmit()) {
                dialog.close();
            }
        });
        submitBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> {
            clearForm();
            this.currentDraft = null; // Clear state
            dialog.close();
        });

        // 5. Layout the Footer (Draft on left, Cancel/Submit on right)
        HorizontalLayout leftFooter = new HorizontalLayout(saveDraftBtn);
        HorizontalLayout rightFooter = new HorizontalLayout(cancelBtn, submitBtn);

        HorizontalLayout footerLayout = new HorizontalLayout(leftFooter, rightFooter);
        footerLayout.setWidthFull();
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        dialog.getFooter().add(footerLayout);
        dialog.open();
    }

    private void setupBinder() {
        binder.forField(leaveType)
                .asRequired("Please select a leave type")
                .bind(LeaveRequest::getLeaveType, LeaveRequest::setLeaveType);

        binder.forField(startDate)
                .asRequired("Start date is required")
                .withValidator(date -> !date.isBefore(LocalDate.now()), "Start date cannot be in the past")
                .bind(LeaveRequest::getStartDate, LeaveRequest::setStartDate);

        binder.forField(endDate)
                .asRequired("End date is required")
                .withValidator(date -> startDate.getValue() == null || !date.isBefore(startDate.getValue()),
                        "End date cannot be before start date")
                .bind(LeaveRequest::getEndDate, LeaveRequest::setEndDate);

        binder.forField(reason)
                .asRequired("Please provide a reason")
                .withValidator(text -> text.length() >= 5, "Reason must be at least 5 characters")
                .bind(LeaveRequest::getReason, LeaveRequest::setReason);

        binder.forField(leaveSessionGroup)
                .withValidator(session -> !leaveSessionGroup.isVisible() || session != null,
                        "Please select which half of the day you are taking off")
                .bind(LeaveRequest::getLeaveSession, LeaveRequest::setLeaveSession);

        binder.readBean(currentRequest);
    }

    private void setupDateCalculations() {
        leaveType.addValueChangeListener(e -> {
            LeaveType type = e.getValue();
            boolean isHalfDay = isHalfDayType(type);

            if (isHalfDay) {
                leaveSessionGroup.setVisible(true);
                endDate.setVisible(false); // Hide End Date from UI

                // Auto-sync end date with start date
                if (startDate.getValue() != null) {
                    endDate.setValue(startDate.getValue());
                }
            } else {
                leaveSessionGroup.setVisible(false);
                leaveSessionGroup.clear(); // Clear value if they switch back to a full day
                endDate.setVisible(true);  // Show End Date again
            }
            calculateDuration();
        });

        endDate.addValueChangeListener(e -> calculateDuration());

        startDate.addValueChangeListener(e -> {
            if (isHalfDayType(leaveType.getValue())) {
                endDate.setValue(e.getValue()); // Keep synced behind the scenes
            } else {
                endDate.setMin(e.getValue());
            }
            calculateDuration();
        });
    }

    private void calculateDuration() {
        LeaveType type = leaveType.getValue();
        LocalDate start = startDate.getValue();
        LocalDate end = endDate.getValue();

        if (type != null && start != null && end != null && !end.isBefore(start)) {
            try {
                BigDecimal netDays = durationEngineService.calculateNetLeaveDays(
                        start, end, currentEmployee, type, false
                );
                durationDays.setValue(netDays.doubleValue());
            } catch (Exception ex) {
                durationDays.clear();
                Notification.show("Error calculating days: " + ex.getMessage(), 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        } else {
            durationDays.clear();
        }
    }

    private boolean attemptSubmit() {
        try {
            binder.writeBean(currentRequest);

            if (durationDays.getValue() == null || durationDays.getValue() <= 0) {
                Notification.show("Duration must be greater than 0", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return false;
            }

            Long draftId = this.currentDraft != null ? this.currentDraft.getId() : null;

            LocalDate finalEndDate = endDate.getValue();
            if (isHalfDayType(leaveType.getValue()) && startDate.getValue() != null) {
                finalEndDate = startDate.getValue(); // Force end date to match start date for half days
            }

            leaveRequestService.submitLeaveRequest(
                    draftId,
                    currentEmployee,
                    leaveType.getValue(),
                    startDate.getValue(),
                    finalEndDate,
                    reason.getValue(),
                    LocalDate.now().getYear(),
                    leaveSessionGroup.getValue()
            );

            Notification.show("Leave request submitted successfully!", 4000, Notification.Position.TOP_END)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            clearForm();
            this.currentDraft = null;
            refreshBalanceAndHistory();
            return true;

        } catch (ValidationException e) {
            Notification.show("Please fix the errors in the form.", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return false;
        } catch (Exception e) {
            Notification.show("Submission failed: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return false;
        }
    }

    private void clearForm() {
        binder.readBean(new LeaveRequest());
        durationDays.clear();
        endDate.setMin(null);
        endDate.setVisible(true);
    }

    private void refreshBalanceAndHistory() {
        balanceLayout.removeAll();

        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(
                currentEmployee.getId(), LocalDate.now().getYear()
        );

        for (LeaveBalance balance : balances) {
            BigDecimal total = balance.getTotalEntitled();
            BigDecimal remaining = leaveBalanceService.getEffectiveBalance(balance);
            double used = total.subtract(remaining).doubleValue();

            // Smart Color & Icon Assignment based on Leave Name
            String leaveName = balance.getLeaveType().getName().toLowerCase();
            String themeColor = "primary"; // Default Blue
            VaadinIcon iconType = VaadinIcon.CALENDAR_CLOCK; // Default Icon

            if (leaveName.contains("annual") || leaveName.contains("vacation") || leaveName.contains("paid")) {
                themeColor = "success"; // Green
                iconType = VaadinIcon.AIRPLANE;
            } else if (leaveName.contains("sick") || leaveName.contains("medical")) {
                themeColor = "error"; // Red
                iconType = VaadinIcon.PLUS_SQUARE_O; // Medical Cross
            } else if (leaveName.contains("casual") || leaveName.contains("personal")) {
                themeColor = "warning"; // Orange/Yellow
                iconType = VaadinIcon.COFFEE;
            } else if (leaveName.contains("maternity") || leaveName.contains("paternity")) {
                themeColor = "primary";
                iconType = VaadinIcon.FAMILY;
            }

            balanceLayout.add(createBalanceCard(
                    balance.getLeaveType().getName(),
                    remaining,
                    used,
                    total,
                    themeColor,
                    iconType
            ));
        }

        historyGrid.setItems(leaveRequestService.getLeaveHistoryForEmployee(currentEmployee.getId())
                .stream().filter(req -> !"DRAFT".equals(req.getStatus())).toList());

        List<LeaveRequest> drafts = leaveRequestService.getDraftsForEmployee(currentEmployee.getId());
        if (drafts.isEmpty()) {
            draftSection.setVisible(false);
        } else {
            draftGrid.setItems(drafts);
            draftSection.setVisible(true);
        }
    }

    private Component createDraftSection() {
        H3 title = new H3("My Drafts");
        title.addClassNames(LumoUtility.Margin.Top.LARGE, LumoUtility.Margin.Bottom.SMALL);

        draftGrid.addColumn(req -> req.getLeaveType() != null ? req.getLeaveType().getName() : "").setHeader("Type").setAutoWidth(true);
        draftGrid.addColumn(LeaveRequest::getStartDate).setHeader("Start").setAutoWidth(true);
        draftGrid.addColumn(LeaveRequest::getEndDate).setHeader("End").setAutoWidth(true);
        draftGrid.addColumn(LeaveRequest::getDurationDays).setHeader("Days").setAutoWidth(true);

        draftGrid.addComponentColumn(draft -> {
            Button resumeBtn = new Button("Resume / Edit", VaadinIcon.EDIT.create());
            resumeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            resumeBtn.addClickListener(e -> openApplyLeaveDialog(draft));
            return resumeBtn;
        }).setHeader("Action").setAutoWidth(true);

        draftGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        draftGrid.setAllRowsVisible(true);

        draftSection.add(title, draftGrid);
        draftSection.setPadding(false);
        draftSection.setVisible(false); // Hidden by default
        return draftSection;
    }
    // Add these methods to your View class

    private void showCommentsDialog(LeaveRequest request) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Approval Workflow Comments");

        List<LeaveApproval> approvals = approvalRoutingService.getApprovalsForRequest(request.getId());

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        if (approvals == null || approvals.isEmpty()) {
            layout.add(new Span("No approval records found for this request yet."));
        } else {
            for (LeaveApproval approval : approvals) {
                String roleName = getRoleName(approval.getApprovalLevel());
                String actionText = approval.getAction() != null ? approval.getAction() : "PENDING";
                String commentText = (approval.getComments() != null && !approval.getComments().isBlank())
                        ? approval.getComments()
                        : "No comment provided.";

                Span headerSpan = new Span(roleName + " (" + actionText + "): ");
                headerSpan.getStyle().set("font-weight", "bold");

                Span bodySpan = new Span(commentText);

                Div entry = new Div(headerSpan, bodySpan);
                entry.getStyle().set("margin-bottom", "10px");
                layout.add(entry);
            }
        }

        dialog.add(layout);

        Button closeButton = new Button("Close", e -> dialog.close());
        dialog.getFooter().add(closeButton);

        dialog.open();
    }

    private String getRoleName(Integer level) {
        if (level == null) return "Approver";
        switch (level) {
            case 1: return "Manager";
            case 2: return "HR";
            case 3: return "Head of Department (HOD)";
            default: return "Approver Level " + level;
        }
    }
    private boolean isHalfDayType(LeaveType type) {
        return type != null && (
                "HDL-001".equalsIgnoreCase(type.getCode()) ||
                        (type.getName() != null && type.getName().toLowerCase().contains("half day"))
        );
    }
}