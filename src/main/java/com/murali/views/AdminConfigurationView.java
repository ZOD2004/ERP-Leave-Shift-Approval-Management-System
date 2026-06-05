package com.murali.views;

import com.murali.entity.*;
import com.murali.entity.enums.RotationSegmentType;
import com.murali.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.renderer.LocalDateRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RolesAllowed({"ROLE_SUPER_ADMIN","ROLE_HR_ADMIN"})
@PageTitle("System Configuration")
@Route(value = "admin-config", layout = MainLayout.class)
public class AdminConfigurationView extends VerticalLayout {

    private final HolidayService holidayService;
    private final LeaveApprovalRuleService ruleService;
    private final LeaveTypeService leaveTypeService;
    private final ShiftRotationService shiftRotationService;
    private final EmployeeService employeeService;
    private final ShiftService shiftService;

    private final VerticalLayout contentContainer = new VerticalLayout();

    private final Grid<Holiday> holidayGrid = new Grid<>(Holiday.class, false);
    private final Grid<LeaveApprovalRule> ruleGrid = new Grid<>(LeaveApprovalRule.class, false);
    private final Grid<ShiftRotationPolicy> policyGrid = new Grid<>(ShiftRotationPolicy.class, false);

    public AdminConfigurationView(HolidayService holidayService, LeaveApprovalRuleService ruleService, LeaveTypeService leaveTypeService, ShiftRotationService shiftRotationService, EmployeeService employeeService, ShiftService shiftService) {
        this.holidayService = holidayService;
        this.ruleService = ruleService;
        this.leaveTypeService = leaveTypeService;
        this.shiftRotationService = shiftRotationService;
        this.employeeService = employeeService;
        this.shiftService = shiftService;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        buildUI();
    }

    private void buildUI() {
        H2 header = new H2("System Configuration");
        header.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.MEDIUM);

        Tab holidaysTab = new Tab("Public Holidays");
        Tab rulesTab = new Tab("Approval Rules");
        Tab policiesTab = new Tab("Rotation Policies");
        Tabs tabs = new Tabs(holidaysTab, rulesTab,policiesTab);

        contentContainer.setSizeFull();
        contentContainer.setPadding(false);

        // Initial view
        showHolidaysView();

        tabs.addSelectedChangeListener(event -> {
            contentContainer.removeAll();
            if (event.getSelectedTab().equals(holidaysTab)) {
                showHolidaysView();
            } else if (event.getSelectedTab().equals(rulesTab)) {
                showRulesView();
            } else {
                showPoliciesView();
            }
        });

        add(header, tabs, contentContainer);
        expand(contentContainer);
    }

    private void showHolidaysView() {
        contentContainer.removeAll();

        LocalDate today = LocalDate.now();

        long upcomingCount = holidayService.countUpcomingHolidaysInMonth(today,today.getMonthValue(),today.getYear());

        // 1. Stats Row
        HorizontalLayout statsRow = new HorizontalLayout(
                createStatsCard("Total Holidays", String.valueOf(holidayService.getAllHolidays().size()), VaadinIcon.CALENDAR, "var(--lumo-primary-color)"),
                createStatsCard("Upcoming", String.valueOf(upcomingCount), VaadinIcon.CLOCK, "var(--lumo-success-color)") // Note: "3" is hardcoded visually, could be dynamically calculated
        );
        statsRow.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        // 2. Search & Action Toolbar
        TextField searchField = new TextField();
        searchField.setPlaceholder("Search holidays...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> {
            holidayGrid.setItems(holidayService.getAllHolidays().stream()
                    .filter(h -> h.getName().toLowerCase().contains(e.getValue().toLowerCase()))
                    .toList());
        });

        Button addBtn = new Button("Add Holiday", VaadinIcon.PLUS.create());
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openHolidayDialog(new Holiday()));

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addBtn);
        toolbar.setWidthFull();
        toolbar.expand(searchField);

        // 3. Grid Enhancements
        holidayGrid.removeAllColumns();
        holidayGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        holidayGrid.setSizeFull();

        holidayGrid.addColumn(Holiday::getName).setHeader("Holiday Name").setSortable(true).setFlexGrow(1);
        holidayGrid.addColumn(new LocalDateRenderer<>(Holiday::getHolidayDate, "dd MMM yyyy")).setHeader("Date").setAutoWidth(true).setSortable(true);

        holidayGrid.addComponentColumn(holiday -> createDeleteButton("holiday '" + holiday.getName() + "'", () -> {
            holidayService.deleteHoliday(holiday.getId());
            refreshHolidays();
        })).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        refreshHolidays();
        contentContainer.add(statsRow, toolbar, holidayGrid);
    }

    private void openHolidayDialog(Holiday holiday) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(holiday.getId() == null ? "New Holiday" : "Edit Holiday");

        TextField nameField = new TextField("Holiday Name");
        DatePicker dateField = new DatePicker("Date");

        FormLayout form = new FormLayout(nameField, dateField);

        Binder<Holiday> binder = new Binder<>(Holiday.class);
        binder.forField(nameField).asRequired("Name is required").bind(Holiday::getName, Holiday::setName);
        binder.forField(dateField).asRequired("Date is required").bind(Holiday::getHolidayDate, Holiday::setHolidayDate);
        binder.readBean(holiday);

        Button saveBtn = new Button("Save", e -> {
            try {
                binder.writeBean(holiday);
                holidayService.saveHoliday(holiday);
                Notification.show("Holiday saved.", 3000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshHolidays();
            } catch (ValidationException ex) {
                Notification.show("Please fix errors.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void refreshHolidays() {
        holidayGrid.setItems(holidayService.getAllHolidays());
    }


    private void showRulesView() {
        contentContainer.removeAll();
        HorizontalLayout statsRow = new HorizontalLayout(
                createStatsCard("Active Rules", String.valueOf(ruleService.getAllRules().size()), VaadinIcon.FILE_TEXT, "var(--lumo-primary-color)"),
                createStatsCard("Complexity", "Multi-Tier", VaadinIcon.CHART_GRID, "var(--lumo-error-color)")
        );
        statsRow.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        ComboBox<LeaveType> typeFilter = new ComboBox<>("Filter by Type");
        typeFilter.setItems(ruleService.getAllLeaveTypes());
        typeFilter.setItemLabelGenerator(LeaveType::getName);
        typeFilter.setClearButtonVisible(true);
        typeFilter.addValueChangeListener(e -> {
            if (e.getValue() == null) refreshRules();
            else ruleGrid.setItems(ruleService.getAllRules().stream()
                    .filter(r -> Objects.equals(r.getLeaveType().getId(), e.getValue().getId())).toList());
        });

        Button addBtn = new Button("New Rule", VaadinIcon.PLUS.create());
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        addBtn.addClickListener(e -> openRuleDialog(new LeaveApprovalRule()));


        HorizontalLayout toolbar = new HorizontalLayout(typeFilter, addBtn);
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.END);
        toolbar.expand(typeFilter);

        ruleGrid.removeAllColumns();
        ruleGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        ruleGrid.setSizeFull();

        ruleGrid.addColumn(rule -> rule.getLeaveType().getName())
                .setHeader("Leave Type")
                .setPartNameGenerator(rule -> "font-weight-bold")
                .setAutoWidth(true);

        ruleGrid.addColumn(rule -> String.format("%s to %s days", rule.getMinDays(), rule.getMaxDays()))
                .setHeader("Duration Range")
                .setAutoWidth(true);

        ruleGrid.addComponentColumn(rule -> {
            Span badge = new Span("Level " + rule.getApprovalLevel());
            badge.getElement().getThemeList().add("badge contrast");
            return badge;
        }).setHeader("Approval Tier").setAutoWidth(true);

        ruleGrid.addColumn(rule -> rule.getRequiredRole().getName())
                .setHeader("Required Role")
                .setAutoWidth(true);

        ruleGrid.addComponentColumn(rule -> {
            // 1. Create the Edit Button
            Button editBtn = new Button(VaadinIcon.EDIT.create());
            editBtn.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openRuleDialog(rule)); // Calls your existing dialog!

            // 2. Keep your existing Delete Button
            Button deleteBtn = createDeleteButton("approval rule for '" + rule.getLeaveType().getName() + "'", () -> {
                ruleService.deleteRule(rule.getId());
                refreshRules();
            });

            // 3. Return both in a layout
            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        refreshRules();

        contentContainer.add(statsRow, toolbar, ruleGrid);
    }

    private void openRuleDialog(LeaveApprovalRule rule) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Configure Approval Rule");

        ComboBox<LeaveType> typeBox = new ComboBox<>("Leave Type");
        typeBox.setItems(ruleService.getAllLeaveTypes());
        typeBox.setItemLabelGenerator(LeaveType::getName);

        NumberField minDays = new NumberField("Min Days");
        NumberField maxDays = new NumberField("Max Days");
        IntegerField approvalLevel = new IntegerField("Approval Level (e.g., 1)");
        minDays.setMin(0.5);
        minDays.setMax(99.9);
        maxDays.setMin(0.5);
        maxDays.setMax(99.9);

        ComboBox<Role> roleBox = new ComboBox<>("Required Role");
        roleBox.setItems(ruleService.getAllRoles());
        roleBox.setItemLabelGenerator(Role::getName);

        FormLayout form = new FormLayout(typeBox, minDays, maxDays, approvalLevel, roleBox);

        Binder<LeaveApprovalRule> binder = new Binder<>(LeaveApprovalRule.class);
        binder.forField(typeBox).asRequired("Required").bind(LeaveApprovalRule::getLeaveType, LeaveApprovalRule::setLeaveType);

        binder.forField(minDays)
                .asRequired("Required")
                .withConverter(
                        value -> value == null ? null : BigDecimal.valueOf(value),
                        value -> value == null ? null : value.doubleValue()
                )
                .bind(LeaveApprovalRule::getMinDays, LeaveApprovalRule::setMinDays);

        binder.forField(maxDays)
                .asRequired("Required")
                .withConverter(
                        value -> value == null ? null : BigDecimal.valueOf(value),
                        value -> value == null ? null : value.doubleValue()
                )
                .bind(LeaveApprovalRule::getMaxDays, LeaveApprovalRule::setMaxDays);

        binder.forField(approvalLevel).asRequired("Required").bind(LeaveApprovalRule::getApprovalLevel, LeaveApprovalRule::setApprovalLevel);
        binder.forField(roleBox).asRequired("Required").bind(LeaveApprovalRule::getRequiredRole, LeaveApprovalRule::setRequiredRole);

        binder.readBean(rule);

        Button saveBtn = new Button("Save", e -> {
            try {
                binder.writeBean(rule);
                ruleService.saveRule(rule);
                Notification.show("Rule saved.", 3000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshRules();
            } catch (ValidationException ex) {
                Notification.show("Please fix errors.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.add(form);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void refreshRules() {
        ruleGrid.setItems(ruleService.getAllRules());
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    private Button createDeleteButton(String itemName, Runnable deleteAction) {
        Button deleteBtn = new Button(VaadinIcon.TRASH.create());
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        deleteBtn.addClickListener(e -> {
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Confirm Delete");
            dialog.setText("Are you sure you want to permanently delete the " + itemName + "?");

            dialog.setCancelable(true);
            dialog.setCancelText("Cancel");

            dialog.setConfirmText("Delete");
            dialog.setConfirmButtonTheme("error primary");

            // Execute the action only when confirmed
            dialog.addConfirmListener(event -> deleteAction.run());

            dialog.open();
        });

        return deleteBtn;
    }

    private Component createStatsCard(String title, String value, VaadinIcon icon, String color) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(LumoUtility.Background.BASE, LumoUtility.Border.ALL,
                LumoUtility.BorderColor.CONTRAST_10, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Padding.MEDIUM, LumoUtility.BoxShadow.SMALL);
        card.setSpacing(false);
        card.setWidth("250px");

        Icon iconComp = icon.create();
        iconComp.getStyle().set("color", color);

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD);

        H3 valueH3 = new H3(value);
        valueH3.addClassNames(LumoUtility.Margin.Vertical.SMALL);

        HorizontalLayout header = new HorizontalLayout(iconComp, titleSpan);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        card.add(header, valueH3);
        return card;
    }
    private void showPoliciesView() {
        contentContainer.removeAll();

        // 1. Stats Row
        HorizontalLayout statsRow = new HorizontalLayout(
                createStatsCard("Active Policies", String.valueOf(shiftRotationService.getAllPolicies().stream().filter(ShiftRotationPolicy::getActive).count()), VaadinIcon.USERS, "var(--lumo-primary-color)"),
                createStatsCard("Total Policies", String.valueOf(shiftRotationService.getAllPolicies().size()), VaadinIcon.FILE_TREE, "var(--lumo-secondary-color)")
        );
        statsRow.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        // 2. Search & Action Toolbar
        Button addBtn = new Button("New Rotation Policy", VaadinIcon.PLUS.create());
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openPolicyDialog(new ShiftRotationPolicy()));

        HorizontalLayout toolbar = new HorizontalLayout(addBtn);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        // 3. Grid Setup
        policyGrid.removeAllColumns();
        policyGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        policyGrid.setSizeFull();

        policyGrid.addColumn(policy -> policy.getEmployee().getFirstName() + " (" + policy.getEmployee().getEmployeeCode() + ")")
                .setHeader("Employee").setSortable(true).setAutoWidth(true);

        policyGrid.addColumn(new LocalDateRenderer<>(ShiftRotationPolicy::getStartDate, "dd MMM yyyy"))
                .setHeader("Start Date").setAutoWidth(true);

        policyGrid.addColumn(policy -> policy.getEndDate() != null ? policy.getEndDate().toString() : "Indefinite")
                .setHeader("End Date").setAutoWidth(true);

        policyGrid.addComponentColumn(policy -> {
            Span badge = new Span(policy.getActive() ? "Active" : "Inactive");
            badge.getElement().getThemeList().add(policy.getActive() ? "badge success" : "badge error");
            return badge;
        }).setHeader("Status").setAutoWidth(true);

        policyGrid.addComponentColumn(policy -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create());
            editBtn.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> {
                ShiftRotationPolicy fullyLoadedPolicy = shiftRotationService.getPolicyWithSequences(policy.getId());
                openPolicyDialog(fullyLoadedPolicy);
            });

            Button deleteBtn = createDeleteButton("policy for " + policy.getEmployee().getFirstName(), () -> {
                shiftRotationService.deletePolicy(policy.getId());
                refreshPolicies();
            });

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        refreshPolicies();
        contentContainer.add(statsRow, toolbar, policyGrid);
    }

    private void refreshPolicies() {
        policyGrid.setItems(shiftRotationService.getAllPolicies());
    }
    private void addSequenceRow(VerticalLayout container, RotationSequence seq, List<RotationSequence> memorySequences, ShiftRotationPolicy policy) {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(FlexComponent.Alignment.END);
        row.setWidthFull();
        row.addClassNames(LumoUtility.Padding.Bottom.SMALL);

        ComboBox<RotationSegmentType> typeBox = new ComboBox<>("Type", RotationSegmentType.values());
        typeBox.setWidth("120px");

        ComboBox<Shift> shiftBox = new ComboBox<>("Shift", shiftService.getShifts());
        shiftBox.setItemLabelGenerator(Shift::getName);
        shiftBox.setWidth("200px");

        IntegerField durationField = new IntegerField("Days");
        durationField.setMin(1);
        durationField.setWidth("100px");
        durationField.setStepButtonsVisible(true);

        // 1. Set initial values if they exist
        if (seq.getSegmentType() != null) typeBox.setValue(seq.getSegmentType());
        if (seq.getShift() != null) shiftBox.setValue(seq.getShift());
        if (seq.getDurationDays() != null) durationField.setValue(seq.getDurationDays());

        // 2. Handle specific UI logic (Disable Shift if OFF)
        typeBox.addValueChangeListener(e -> {
            if (e.getValue() == RotationSegmentType.OFF) {
                shiftBox.clear();
                shiftBox.setEnabled(false);
            } else {
                shiftBox.setEnabled(true);
            }
            seq.setSegmentType(e.getValue()); // Update object
        });

        // Trigger initial state
        if (typeBox.getValue() == RotationSegmentType.OFF) shiftBox.setEnabled(false);

        // Update object on change
        shiftBox.addValueChangeListener(e -> seq.setShift(e.getValue()));
        durationField.addValueChangeListener(e -> seq.setDurationDays(e.getValue()));

        // 3. Remove Button logic
        Button removeBtn = new Button(VaadinIcon.TRASH.create(), e -> {
            container.remove(row);
            memorySequences.remove(seq);
        });
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);

        row.add(typeBox, shiftBox, durationField, removeBtn);
        row.expand(shiftBox);

        container.add(row);
        memorySequences.add(seq);
    }
    // ========================================================================
    // SHIFT ROTATION POLICY DIALOG & CASCADING DATES
    // ========================================================================

    // A lightweight wrapper to track UI components for cascading updates
    private static class SequenceRowContext {
        RotationSequence sequence;
        HorizontalLayout layout;
        DatePicker endDatePicker;
        Span infoBadge;
    }

    private void openPolicyDialog(ShiftRotationPolicy policy) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(policy.getId() == null ? "Create Rotation Policy" : "Edit Rotation Policy");
        dialog.setWidth("800px");

        // --- 1. Main Policy Fields ---
        ComboBox<Employee> employeeBox = new ComboBox<>("Employee");
        employeeBox.setItems(employeeService.findAllActive());
        employeeBox.setItemLabelGenerator(emp -> emp.getFirstName() + " (" + emp.getEmployeeCode() + ")");

        DatePicker startDate = new DatePicker("Start Date");
        DatePicker endDate = new DatePicker("End Date (Optional)");
        Checkbox activeCheck = new Checkbox("Active Policy");
        activeCheck.setValue(true);

        // Date Constraints
        if (policy.getId() == null) {
            startDate.setMin(LocalDate.now()); // Disable past dates for new policies
        }

        startDate.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                endDate.setMin(e.getValue());
                if (endDate.getValue() != null && endDate.getValue().isBefore(e.getValue())) {
                    endDate.clear();
                }
            }
        });

        FormLayout mainForm = new FormLayout(employeeBox, startDate, endDate, activeCheck);
        mainForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        Binder<ShiftRotationPolicy> binder = new Binder<>(ShiftRotationPolicy.class);
        binder.forField(employeeBox).asRequired("Employee is required").bind(ShiftRotationPolicy::getEmployee, ShiftRotationPolicy::setEmployee);
        binder.forField(startDate).asRequired("Start Date is required").bind(ShiftRotationPolicy::getStartDate, ShiftRotationPolicy::setStartDate);
        binder.bind(endDate, ShiftRotationPolicy::getEndDate, ShiftRotationPolicy::setEndDate);
        binder.bind(activeCheck, ShiftRotationPolicy::getActive, ShiftRotationPolicy::setActive);
        binder.readBean(policy);

        // --- 2. Dynamic Sequence Builder ---
        H3 sequenceHeader = new H3("Rotation Sequences");
        sequenceHeader.addClassNames(LumoUtility.Margin.Top.LARGE, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);

        VerticalLayout sequenceContainer = new VerticalLayout();
        sequenceContainer.setPadding(false);
        sequenceContainer.setSpacing(false);

        List<SequenceRowContext> uiRows = new ArrayList<>();

        // Trigger cascading updates whenever the policy start date changes
        startDate.addValueChangeListener(e -> recalculateSequenceDates(startDate.getValue(), endDate.getValue(), uiRows));
        endDate.addValueChangeListener(e -> recalculateSequenceDates(startDate.getValue(), endDate.getValue(), uiRows));

        Button addStepBtn = new Button("Add Step", VaadinIcon.PLUS.create(), e -> {
            if (startDate.getValue() == null) {
                Notification.show("Please select a Policy Start Date first.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            RotationSequence newSeq = new RotationSequence();
            addSequenceRow(sequenceContainer, newSeq, uiRows, startDate, endDate);
            recalculateSequenceDates(startDate.getValue(), endDate.getValue(), uiRows);
        });
        addStepBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // Load existing sequences if editing
        if (policy.getSequences() != null && !policy.getSequences().isEmpty()) {
            for (RotationSequence seq : policy.getSequences()) {
                addSequenceRow(sequenceContainer, seq, uiRows, startDate, endDate);
            }
            // Defer initial calculation slightly to let components bind
            sequenceContainer.addAttachListener(e -> recalculateSequenceDates(startDate.getValue(), endDate.getValue(), uiRows));
        }

        // --- 3. Save Logic ---
        Button saveBtn = new Button("Save", e -> {
            try {
                binder.writeBean(policy);

                if (uiRows.isEmpty()) {
                    Notification.show("Please add at least one sequence step.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }

                int totalSequenceDays = 0;
                policy.getSequences().clear();

                for (int i = 0; i < uiRows.size(); i++) {
                    SequenceRowContext ctx = uiRows.get(i);
                    RotationSequence seq = ctx.sequence;

                    if (seq.getSegmentType() == RotationSegmentType.WORK && seq.getShift() == null) {
                        Notification.show("Step " + (i + 1) + " is WORK but missing a Shift.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }
                    if (seq.getDurationDays() == null || seq.getDurationDays() <= 0) {
                        Notification.show("Step " + (i + 1) + " is missing a valid End Date.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }

                    totalSequenceDays += seq.getDurationDays();
                    seq.setSequenceOrder(i + 1);
                    seq.setPolicy(policy);
                    policy.getSequences().add(seq);
                }

                // Strict Validation: Total days must match if End Date is provided
                if (policy.getEndDate() != null) {
                    long policyTotalDays = ChronoUnit.DAYS.between(policy.getStartDate(), policy.getEndDate()) + 1;
                    if (totalSequenceDays != policyTotalDays) {
                        Notification.show("Sequence total (" + totalSequenceDays + " days) must exactly match Policy window (" + policyTotalDays + " days).",
                                5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }
                }

                shiftRotationService.createAndKickstartPolicy(policy);

                Notification.show("Policy saved successfully.", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshPolicies();

            } catch (ValidationException ex) {
                Notification.show("Please fix the top form errors.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.add(mainForm, sequenceHeader, sequenceContainer, addStepBtn);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void addSequenceRow(VerticalLayout container, RotationSequence seq, List<SequenceRowContext> uiRows, DatePicker policyStartDate, DatePicker policyEndDate) {
        SequenceRowContext ctx = new SequenceRowContext();
        ctx.sequence = seq;
        ctx.layout = new HorizontalLayout();
        ctx.layout.setAlignItems(FlexComponent.Alignment.END);
        ctx.layout.setWidthFull();
        ctx.layout.addClassNames(LumoUtility.Padding.Bottom.SMALL);

        ComboBox<RotationSegmentType> typeBox = new ComboBox<>("Type", RotationSegmentType.values());
        typeBox.setWidth("120px");

        ComboBox<Shift> shiftBox = new ComboBox<>("Shift", shiftService.getShifts());
        shiftBox.setItemLabelGenerator(Shift::getName);
        shiftBox.setWidth("180px");

        ctx.endDatePicker = new DatePicker("Step End Date");
        ctx.endDatePicker.setWidth("160px");

        ctx.infoBadge = new Span("Pending...");
        ctx.infoBadge.getElement().getThemeList().add("badge");
        ctx.infoBadge.getStyle().set("margin-bottom", "10px"); // Align visually with fields

        if (seq.getSegmentType() != null) typeBox.setValue(seq.getSegmentType());
        if (seq.getShift() != null) shiftBox.setValue(seq.getShift());

        typeBox.addValueChangeListener(e -> {
            if (e.getValue() == RotationSegmentType.OFF) {
                shiftBox.clear();
                shiftBox.setEnabled(false);
            } else {
                shiftBox.setEnabled(true);
            }
            seq.setSegmentType(e.getValue());
        });

        if (typeBox.getValue() == RotationSegmentType.OFF || typeBox.getValue() == null) {
            shiftBox.setEnabled(false);
        }

        shiftBox.addValueChangeListener(e -> seq.setShift(e.getValue()));

        // When this row's end date changes, cascade the dates down the list
        // When this row's end date changes, cascade the dates down the list
        ctx.endDatePicker.addValueChangeListener(e -> recalculateSequenceDates(policyStartDate.getValue(), policyEndDate.getValue(), uiRows));

        Button removeBtn = new Button(VaadinIcon.TRASH.create(), e -> {
            container.remove(ctx.layout);
            uiRows.remove(ctx);
            recalculateSequenceDates(policyStartDate.getValue(), policyEndDate.getValue(), uiRows);
        });
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);

        ctx.layout.add(typeBox, shiftBox, ctx.endDatePicker, ctx.infoBadge, removeBtn);
        ctx.layout.expand(shiftBox);

        container.add(ctx.layout);
        uiRows.add(ctx);
    }

    /**
     * Core Engine for Cascading Dates.
     * Iterates through all sequences, dynamically setting their Start Date based on the previous End Date.
     */
    private void recalculateSequenceDates(LocalDate rootStartDate, LocalDate rootEndDate, List<SequenceRowContext> uiRows)  {
        if (rootStartDate == null) return;

        LocalDate currentStartDate = rootStartDate;

        for (int i = 0; i < uiRows.size(); i++) {
            SequenceRowContext ctx = uiRows.get(i);

            // 1. Constrain the End Date picker so it can't be set before its cascading Start Date
            ctx.endDatePicker.setMin(currentStartDate);
            if (rootEndDate != null) {
                ctx.endDatePicker.setMax(rootEndDate);
            } else {
                ctx.endDatePicker.setMax(null);
            }

            // 2. If editing existing and we have duration, pre-fill the picker (only happens on first load)
            if (ctx.endDatePicker.getValue() == null && ctx.sequence.getDurationDays() != null && ctx.sequence.getDurationDays() > 0) {
                ctx.endDatePicker.setValue(currentStartDate.plusDays(ctx.sequence.getDurationDays() - 1));
            }

            // 3. If a valid End Date is selected, calculate duration and set up the NEXT row
            LocalDate selectedEndDate = ctx.endDatePicker.getValue();
            if (selectedEndDate != null && !selectedEndDate.isBefore(currentStartDate)) {

                long days = ChronoUnit.DAYS.between(currentStartDate, selectedEndDate) + 1;
                ctx.sequence.setDurationDays((int) days);

                // Update UI Feedback
                ctx.infoBadge.setText("Start: " + currentStartDate.format(DateTimeFormatter.ofPattern("MMM dd")) + " (" + days + " days)");
                ctx.infoBadge.getElement().getThemeList().add("badge success");

                // Cascade the start date for the NEXT loop iteration
                currentStartDate = selectedEndDate.plusDays(1);
            } else {
                // If invalid or empty, halt cascade and show warning
                ctx.sequence.setDurationDays(0);
                ctx.infoBadge.setText("Starts: " + currentStartDate.format(DateTimeFormatter.ofPattern("MMM dd")));
                ctx.infoBadge.getElement().getThemeList().add("badge contrast");

                // We don't know when this step ends, so we can't reliably calculate start dates for subsequent steps
                currentStartDate = null;
            }
        }
    }
}