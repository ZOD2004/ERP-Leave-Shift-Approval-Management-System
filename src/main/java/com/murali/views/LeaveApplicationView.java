package com.murali.views;

import com.murali.dto.LeaveDurationResultDTO;
import com.murali.entity.*;
import com.murali.entity.enums.ApprovalType;
import com.murali.entity.enums.LeaveSession;
import com.murali.util.SecurityService;
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
import com.vaadin.flow.component.orderedlayout.Scroller;
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
import jakarta.annotation.security.RolesAllowed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN", "ROLE_MANAGER", "ROLE_DEPT_HEAD", "ROLE_EMPLOYEE", "ROLE_AUDITOR"})
@PageTitle("Leave Dashboard")
@Route(value = "apply-leave", layout = MainLayout.class)
public class LeaveApplicationView extends VerticalLayout {

    private final LeaveRequestService leaveRequestService;
    private final LeaveTypeService leaveTypeService;
    private final DurationEngineService durationEngineService;
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
    private final ComboBox<LeaveSession> startSessionBox = new ComboBox<>("Start Date Session");
    private final ComboBox<LeaveSession> endSessionBox = new ComboBox<>("End Date Session");
    private final boolean applySandwichRule = true;

    private final Grid<LeaveRequest> historyGrid = new Grid<>(LeaveRequest.class, false);
    private final HorizontalLayout balanceLayout = new HorizontalLayout();

    private final Grid<LeaveRequest> draftGrid = new Grid<>(LeaveRequest.class, false);
    private final VerticalLayout draftSection = new VerticalLayout();
    private LeaveRequest currentDraft = null;

    public LeaveApplicationView(LeaveRequestService leaveRequestService, LeaveTypeService leaveTypeService, DurationEngineService durationEngineService, SecurityService securityService, LeaveBalanceService leaveBalanceService, ApprovalRoutingService approvalRoutingService) {

        this.leaveRequestService = leaveRequestService;
        this.leaveTypeService = leaveTypeService;
        this.durationEngineService = durationEngineService;
        this.leaveBalanceService = leaveBalanceService;
        this.currentEmployee = securityService.getCurrentEmployee();
        this.approvalRoutingService = approvalRoutingService;

        buildMainView();
        startSessionBox.setItems(LeaveSession.values());
        endSessionBox.setItems(LeaveSession.values());
        leaveType.setItems(leaveTypeService.getAvailableLeaveTypes());
        leaveType.setItems(leaveTypeService.getAvailableLeaveTypes().stream()
                .filter(lt -> !lt.getName().trim().equalsIgnoreCase("Unpaid Leave"))
                .toList());
        setupBinder();
        setupDateCalculations();
        refreshBalanceAndHistory();
    }

    private void buildMainView() {
        addClassName("standard-view-container"); // Standard global layout margins
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

        add(header, createBalanceSection(), createDraftSection(), createHistorySection());
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

        historyGrid.addColumn(LeaveRequest::getStartDate).setHeader("Start").setAutoWidth(true);
        historyGrid.addColumn(LeaveRequest::getEndDate).setHeader("End").setAutoWidth(true);

        historyGrid.addColumn(req -> req.getLeaveType() != null ? req.getLeaveType().getName() : "").setHeader("Type").setAutoWidth(true);

        historyGrid.addColumn(LeaveRequest::getDurationDays).setHeader("Days").setAutoWidth(true);

        historyGrid.addComponentColumn(req -> {
            Span badge = new Span(req.getStatus());
            badge.getElement().getThemeList().add("badge pill");
            badge.getStyle().set("cursor", "pointer");
            badge.setTitle("Click to view approval history");

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

            badge.addClickListener(e -> showApprovalHistoryDialog(req));

            return badge;
        }).setHeader("Status").setAutoWidth(true);


        historyGrid.addComponentColumn(this::createActionColumn).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        historyGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        historyGrid.setSizeFull();
        historyGrid.addClassName("standard-surface"); // Applies standardized grid container styles

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
            disabledBtn.setTooltipText("Cannot cancel a request that is already " + status.toLowerCase() + ".");
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

        Span warningMessage = new Span(String.format("Are you sure you want to cancel your %s leave request from %s to %s?", request.getLeaveType().getName(), request.getStartDate(), request.getEndDate()));
        confirmDialog.add(new VerticalLayout(warningMessage));

        Button confirmBtn = new Button("Yes, Cancel It", e -> {
            try {
                int currentYear = LocalDate.now().getYear();

                leaveRequestService.requestManualCancellation(request.getId(), currentEmployee.getId(), currentYear);


                Notification.show("Leave request cancelled successfully.", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                confirmDialog.close();
                refreshBalanceAndHistory();

            } catch (Exception ex) {
                Notification.show("Cancellation failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
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
        card.addClassNames("standard-surface", "hoverable"); // Centralized standard card styling
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

        Span statsSpan = new Span(String.format("%s used of %s total", BigDecimal.valueOf(used).stripTrailingZeros().toPlainString(), total.stripTrailingZeros().toPlainString()));
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

        leaveType.setItems(leaveTypeService.getAvailableLeaveTypes().stream()
                .filter(lt -> !lt.getName().trim().equalsIgnoreCase("Unpaid Leave"))
                .toList());
        leaveType.setItemLabelGenerator(LeaveType::getName);

        durationDays.setReadOnly(true);
        durationDays.setPrefixComponent(VaadinIcon.CLOCK.create());
        durationDays.setHelperText("Excludes weekends/holidays");
        reason.setMinHeight("100px");

        startSessionBox.setItemLabelGenerator(session -> formatSessionName(session));
        endSessionBox.setItemLabelGenerator(session -> formatSessionName(session));

        // Defaults
        startSessionBox.setValue(LeaveSession.FULL_DAY);
        endSessionBox.setValue(LeaveSession.FULL_DAY);

        // 2. If resuming a draft, populate the fields
        if (draftToEdit != null) {
            leaveType.setValue(draftToEdit.getLeaveType());
            startDate.setValue(draftToEdit.getStartDate());
            endDate.setValue(draftToEdit.getEndDate());
            reason.setValue(draftToEdit.getReason());

            // Setting session values will automatically trigger the UI sync via the listeners we setup
            startSessionBox.setValue(draftToEdit.getStartSession() != null ? draftToEdit.getStartSession() : LeaveSession.FULL_DAY);
            endSessionBox.setValue(draftToEdit.getEndSession() != null ? draftToEdit.getEndSession() : LeaveSession.FULL_DAY);

            syncSessionUI();
            calculateDuration();
        } else {
            clearForm();
        }

        // 3. Build Form Layout
        FormLayout formLayout = new FormLayout();
        formLayout.add(leaveType, 2);
        formLayout.add(startDate, 1);
        formLayout.add(endDate, 1);
        formLayout.add(startSessionBox, 1);
        formLayout.add(endSessionBox, 1);
        formLayout.add(durationDays, 2);
        formLayout.add(reason, 2);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        Span infoNote = new Span(VaadinIcon.INFO_CIRCLE.create(), new Span(" Routed to immediate manager."));
        infoNote.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.Display.FLEX, LumoUtility.Gap.XSMALL, LumoUtility.Margin.Top.SMALL);

        VerticalLayout dialogLayout = new VerticalLayout(formLayout, infoNote);
        dialogLayout.setPadding(false);
        dialog.add(dialogLayout);

        // 4. Create Buttons & Logic
        Button saveDraftBtn = new Button("Save as Draft", e -> {
            // Basic validation because DB requires dates and type
            if (leaveType.getValue() == null || startDate.getValue() == null || endDate.getValue() == null) {
                Notification.show("Please select Leave Type and Dates to save a draft.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            try {
                Long draftId = currentDraft != null ? currentDraft.getId() : null;
                leaveRequestService.saveDraft(draftId, currentEmployee, leaveType.getValue(), startDate.getValue(), endDate.getValue(), reason.getValue(), startSessionBox.getValue(), endSessionBox.getValue(), applySandwichRule);
                Notification.show("Draft saved successfully.", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                this.currentDraft = null;
                dialog.close();
                refreshBalanceAndHistory();
            } catch (Exception ex) {
                Notification.show("Failed to save draft: " + ex.getMessage(), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
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
        binder.forField(leaveType).asRequired("Please select a leave type").bind(LeaveRequest::getLeaveType, LeaveRequest::setLeaveType);

        binder.forField(startDate).asRequired("Start date is required").withValidator(date -> !date.isBefore(LocalDate.now()), "Start date cannot be in the past").bind(LeaveRequest::getStartDate, LeaveRequest::setStartDate);

        binder.forField(endDate).asRequired("End date is required").withValidator(date -> startDate.getValue() == null || !date.isBefore(startDate.getValue()), "End date cannot be before start date").bind(LeaveRequest::getEndDate, LeaveRequest::setEndDate);

        binder.forField(reason).asRequired("Please provide a reason").withValidator(text -> text.length() >= 5, "Reason must be at least 5 characters").bind(LeaveRequest::getReason, LeaveRequest::setReason);

        binder.forField(startSessionBox).asRequired("Start session is required").bind(LeaveRequest::getStartSession, LeaveRequest::setStartSession);
        binder.forField(endSessionBox).bind(LeaveRequest::getEndSession, LeaveRequest::setEndSession);

        binder.readBean(currentRequest);
    }

    private void setupDateCalculations() {
        startDate.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                endDate.setMin(e.getValue());
                if (endDate.getValue() != null && endDate.getValue().isBefore(e.getValue())) {
                    endDate.setValue(e.getValue());
                }
            }
            syncSessionUI();
        });

        endDate.addValueChangeListener(e -> syncSessionUI());
        startSessionBox.addValueChangeListener(e -> calculateDuration());
        endSessionBox.addValueChangeListener(e -> calculateDuration());
        leaveType.addValueChangeListener(e -> calculateDuration());
    }

    private void calculateDuration() {
        LeaveType type = leaveType.getValue();
        LocalDate start = startDate.getValue();
        LocalDate end = endDate.getValue();
        LeaveSession startSess = startSessionBox.getValue();
        LeaveSession endSess = endSessionBox.getValue();

        if (type != null && start != null && end != null && !end.isBefore(start) && startSess != null) {
            try {
                if (endSess == null) endSess = LeaveSession.FULL_DAY;

                LeaveDurationResultDTO result = durationEngineService.calculateLeaveDuration(start, end, currentEmployee, startSess, endSess, type.getApplySandwichRule());
                durationDays.setValue(result.getNetLeaveDays().doubleValue());
            } catch (Exception ex) {
                ex.printStackTrace();
                durationDays.clear();
                Notification.show("Calculation Error: " + ex.getMessage(), 4000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        } else {
            durationDays.clear();
        }
    }

    private void syncSessionUI() {
        LocalDate start = startDate.getValue();
        LocalDate end = endDate.getValue();

        if (start != null && end != null) {
            if (start.equals(end)) {
                // Single day leave: Only show one dropdown
                endSessionBox.setVisible(false);
                endSessionBox.setValue(LeaveSession.FULL_DAY); // Reset invisible box

                startSessionBox.setLabel("Day Session");
                startSessionBox.setItems(LeaveSession.FULL_DAY, LeaveSession.FIRST_HALF, LeaveSession.SECOND_HALF);
            } else {
                endSessionBox.setVisible(true);

                startSessionBox.setLabel("Start Date Session");
                startSessionBox.setItems(LeaveSession.FIRST_HALF, LeaveSession.SECOND_HALF);

                endSessionBox.setLabel("End Date Session");
                endSessionBox.setItems(LeaveSession.FIRST_HALF, LeaveSession.SECOND_HALF);
            }
        }
        calculateDuration();
    }

    private boolean attemptSubmit() {
        try {
            binder.writeBean(currentRequest);

            if (durationDays.getValue() == null || durationDays.getValue() <= 0) {
                Notification.show("Duration must be greater than 0", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return false;
            }

            List<LeaveRequest> conflicts = leaveRequestService.getMergeConflicts(currentEmployee.getId(), startDate.getValue(), endDate.getValue());
            if (!conflicts.isEmpty()) {
                showMergeConflictDialog(conflicts);
                return false;
            }

            executeFinalSubmit(null);
            return true;

        } catch (ValidationException e) {
            Notification.show("Please fix the errors in the form.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            return false;
        }
    }

    private void executeFinalSubmit(List<Long> supersededIds) {
        try {
            Long draftId = this.currentDraft != null ? this.currentDraft.getId() : null;

            leaveRequestService.submitLeaveRequest(draftId, currentEmployee, leaveType.getValue(), startDate.getValue(), endDate.getValue(), reason.getValue(), LocalDate.now().getYear(), startSessionBox.getValue(), endSessionBox.getValue() != null ? endSessionBox.getValue() : LeaveSession.FULL_DAY, supersededIds);

            Notification.show("Leave request submitted successfully!", 4000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            clearForm();
            this.currentDraft = null;
            refreshBalanceAndHistory();

        } catch (Exception e) {
            Notification.show("Submission failed: " + e.getMessage(), 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void showMergeConflictDialog(List<LeaveRequest> conflicts) {
        Dialog conflictDialog = new Dialog();
        conflictDialog.setHeaderTitle("Adjacent Leave Detected");

        Span message = new Span("You already have a leave request near or on these dates. To proceed, this must be merged into a single continuous request of the same leave type.");

        List<Long> conflictIds = new ArrayList<>();
        VerticalLayout list = new VerticalLayout();
        for (LeaveRequest conflict : conflicts) {
            conflictIds.add(conflict.getId());
            list.add(new Span("• " + conflict.getStartDate() + " to " + conflict.getEndDate() + " (" + conflict.getLeaveType().getName() + ") - " + conflict.getStatus()));
        }

        Button mergeBtn = new Button("Merge & Submit", e -> {
            executeFinalSubmit(conflictIds);
            conflictDialog.close();
        });
        mergeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> conflictDialog.close());

        conflictDialog.add(new VerticalLayout(message, list));
        conflictDialog.getFooter().add(cancelBtn, mergeBtn);
        conflictDialog.open();
    }

    private void clearForm() {
        binder.readBean(new LeaveRequest());
        durationDays.clear();
        endDate.setMin(null);
        endDate.setVisible(true);
    }

    private void refreshBalanceAndHistory() {
        balanceLayout.removeAll();

        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(currentEmployee.getId(), LocalDate.now().getYear());

        for (LeaveBalance balance : balances) {
            if (balance.getLeaveType().getName().trim().equalsIgnoreCase("Unpaid Leave")) {
                continue;
            }
            BigDecimal total = balance.getTotalEntitled();
            BigDecimal remaining = leaveBalanceService.getEffectiveBalance(balance);
            double used = total.subtract(remaining).doubleValue();

            String leaveName = balance.getLeaveType().getName().toLowerCase();
            String themeColor = "primary";
            VaadinIcon iconType = VaadinIcon.CALENDAR_CLOCK;

            if (leaveName.contains("annual") || leaveName.contains("vacation") || leaveName.contains("paid")) {
                themeColor = "success";
                iconType = VaadinIcon.AIRPLANE;
            } else if (leaveName.contains("sick") || leaveName.contains("medical")) {
                themeColor = "error";
                iconType = VaadinIcon.PLUS_SQUARE_O;
            } else if (leaveName.contains("casual") || leaveName.contains("personal")) {
                themeColor = "warning";
                iconType = VaadinIcon.COFFEE;
            } else if (leaveName.contains("maternity") || leaveName.contains("paternity")) {
                themeColor = "primary";
                iconType = VaadinIcon.FAMILY;
            }

            balanceLayout.add(createBalanceCard(balance.getLeaveType().getName(), remaining, used, total, themeColor, iconType));
        }

        historyGrid.setItems(leaveRequestService.getLeaveHistoryForEmployee(currentEmployee.getId()).stream().filter(req -> !"DRAFT".equals(req.getStatus())).toList());

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
        draftGrid.addClassName("standard-surface");
        draftGrid.setAllRowsVisible(true);
        draftGrid.addItemDoubleClickListener(e -> openApplyLeaveDialog(e.getItem())); // Invoke existing edit handler

        draftSection.add(title, draftGrid);
        draftSection.setPadding(false);
        draftSection.setVisible(false); // Hidden by default
        return draftSection;
    }

    private void showApprovalHistoryDialog(LeaveRequest request) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Approval History & Comments");
        dialog.setWidth("550px");
        dialog.setMaxHeight("90vh");

        List<LeaveApproval> approvals = approvalRoutingService.getApprovalsForRequest(request.getId());

        VerticalLayout timelineLayout = new VerticalLayout();
        timelineLayout.setPadding(false);
        // Replaced default spacing with a distinct Lumo gap for better consistency between cards
        timelineLayout.setSpacing(false);
        timelineLayout.addClassName(LumoUtility.Gap.MEDIUM);

        if (approvals == null || approvals.isEmpty()) {
            Span emptyState = new Span("No approval records found for this request yet.");
            emptyState.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.Padding.Top.MEDIUM);
            timelineLayout.add(emptyState);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy - hh:mm a");

            for (LeaveApproval approval : approvals) {
                String roleName = getRoleName(approval.getApprovalLevel());
                String approverName = approval.getApprover() != null ? approval.getApprover().getUsername() : "Unknown";
                String actionText = approval.getAction() != null ? approval.getAction() : "PENDING";

                boolean isCancellation = approval.getApprovalType() == ApprovalType.CANCELLATION;
                String displayAction = isCancellation ? "CANCEL " + actionText : actionText;

                // 1. Card Container Spacing Enhancements
                VerticalLayout entryCard = new VerticalLayout();
                entryCard.addClassName("standard-surface");
                entryCard.setSpacing(false);

                // Colored left border with an explicit left-padding so text doesn't hug the line
                String threadColor = isCancellation ? "#da1e28" : "var(--app-primary-color)"; // Standardized fixed colors
                entryCard.getStyle().set("border-left", "5px solid " + threadColor);
                entryCard.getStyle().set("padding-left", "var(--lumo-space-m)");

                // 2. Header Layout
                HorizontalLayout headerLayout = new HorizontalLayout();
                headerLayout.setWidthFull();
                headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

                Span roleSpan = new Span(roleName);
                roleSpan.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.FontSize.MEDIUM);

                Span approverSpan = new Span("• " + approverName);
                approverSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

                HorizontalLayout identityLayout = new HorizontalLayout(roleSpan, approverSpan);
                identityLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
                identityLayout.setThemeName("spacing-s");

                if (isCancellation) {
                    Span typeBadge = new Span("Cancellation");
                    typeBadge.getElement().getThemeList().add("badge small contrast");
                    identityLayout.add(typeBadge);
                }

                Span statusBadge = new Span(displayAction);
                statusBadge.getElement().getThemeList().add("badge small");

                if (actionText.contains("APPROVED")) {
                    statusBadge.getElement().getThemeList().add(isCancellation ? "error" : "success");
                } else if (actionText.contains("REJECTED")) {
                    statusBadge.getElement().getThemeList().add(isCancellation ? "success" : "error");
                } else if (actionText.contains("CANCELLED") || actionText.contains("SIBLING")) {
                    statusBadge.getElement().getThemeList().add("contrast");
                } else {
                    statusBadge.getElement().getThemeList().add("warning");
                }

                headerLayout.add(identityLayout, statusBadge);

                // 3. Time Details
                String timeString = approval.getActedAt() != null
                        ? approval.getActedAt().format(formatter)
                        : "Awaiting Action";
                Span timeSpan = new Span(timeString);
                // Removed bottom margin since the Gap.XSMALL on the parent handles it now
                timeSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY);

                // 4. Comment Formatting
                boolean hasComment = approval.getComments() != null && !approval.getComments().isBlank();
                Span commentSpan = new Span(hasComment ? "\"" + approval.getComments() + "\"" : "No comments provided.");
                commentSpan.addClassNames(LumoUtility.FontSize.SMALL);

                if (hasComment) {
                    commentSpan.addClassNames(LumoUtility.TextColor.BODY);
                } else {
                    commentSpan.addClassNames(LumoUtility.TextColor.TERTIARY);
                }

                entryCard.add(headerLayout, timeSpan, commentSpan);
                timelineLayout.add(entryCard);
            }
        }

        // Add extra padding around the scroller content so the top/bottom cards don't touch the dialog edges
        timelineLayout.getStyle().set("padding", "var(--lumo-space-s)");

        Scroller scroller = new Scroller(timelineLayout);
        scroller.setMaxHeight("450px");
        scroller.getStyle().set("padding-right", "10px");

        dialog.add(scroller);

        Button closeButton = new Button("Close", e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(closeButton);

        dialog.open();
    }

    private String getRoleName(Integer level) {
        if (level == null) return "Approver";
        switch (level) {
            case 1:
                return "Manager";
            case 2:
                return "HR";
            case 3:
                return "Head of Department (HOD)";
            default:
                return "Approver Level " + level;
        }
    }

    private String formatSessionName(LeaveSession session) {
        if (session == null) return "";
        switch (session) {
            case FIRST_HALF:
                return "1st Half (Morning off)";
            case SECOND_HALF:
                return "2nd Half (Afternoon off)";
            case FULL_DAY:
                return "Full Day";
            default:
                return session.name();
        }
    }
}