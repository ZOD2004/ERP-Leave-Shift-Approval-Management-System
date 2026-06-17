package com.murali.views;

import com.murali.entity.*;
import com.murali.entity.enums.RotationSegmentType;
import com.murali.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
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



    private void openPolicyDialog(ShiftRotationPolicy policy) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(policy.getId() == null ? "Create Rotation Policy" : "Edit Rotation Policy");
        dialog.setWidth("800px");

        // --- 1. Main Policy Fields ---
        ComboBox<Employee> employeeBox = new ComboBox<>("Employee");
        employeeBox.setItems(employeeService.findAllActive());
        employeeBox.setItemLabelGenerator(emp -> emp.getFirstName() + " (" + emp.getEmployeeCode() + ")");

        DatePicker startDate = new DatePicker("Start Date");
        Checkbox activeCheck = new Checkbox("Active Policy");
        activeCheck.setValue(true);

        if (policy.getId() == null) {
            startDate.setMin(LocalDate.now());
        }

        FormLayout mainForm = new FormLayout(employeeBox, startDate, activeCheck);
        mainForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        Binder<ShiftRotationPolicy> binder = new Binder<>(ShiftRotationPolicy.class);
        binder.forField(employeeBox).asRequired("Employee is required").bind(ShiftRotationPolicy::getEmployee, ShiftRotationPolicy::setEmployee);
        binder.forField(startDate).asRequired("Start Date is required").bind(ShiftRotationPolicy::getStartDate, ShiftRotationPolicy::setStartDate);
        binder.bind(activeCheck, ShiftRotationPolicy::getActive, ShiftRotationPolicy::setActive);
        binder.readBean(policy);

        // --- 2. Dynamic Pattern Builder ---
        H3 sequenceHeader = new H3("Dynamic Shift Pattern");
        sequenceHeader.addClassNames(LumoUtility.Margin.Top.LARGE, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);
        Text helperText = new Text("Build the rotation loop. The system will cycle these segments consecutively, regardless of the day of the week.");

        VerticalLayout sequencesContainer = new VerticalLayout();
        sequencesContainer.setPadding(false);
        sequencesContainer.setSpacing(false);

        List<RotationSequence> memorySequences = new ArrayList<>();

        // If editing, load existing sequences
        if (policy.getSequences() != null) {
            for (RotationSequence seq : policy.getSequences()) {
                addSequenceRow(sequencesContainer, seq, memorySequences, policy);
            }
        } else {
            // Add one blank row for a new policy
            addSequenceRow(sequencesContainer, new RotationSequence(), memorySequences, policy);
        }

        Button addSegmentBtn = new Button("Add Segment", VaadinIcon.PLUS.create(), e -> {
            addSequenceRow(sequencesContainer, new RotationSequence(), memorySequences, policy);
        });
        addSegmentBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // --- 3. Save Logic ---
        Button saveBtn = new Button("Save", e -> {
            try {
                binder.writeBean(policy);

                if (memorySequences.isEmpty()) {
                    Notification.show("You must add at least one segment.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }

                policy.getSequences().clear();
                int order = 1;

                for (RotationSequence seq : memorySequences) {
                    if (seq.getSegmentType() == RotationSegmentType.WORK && seq.getShift() == null) {
                        Notification.show("Please select a shift for all WORK segments.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }
                    if (seq.getDurationDays() == null || seq.getDurationDays() < 1) {
                        Notification.show("All segments must have a duration of at least 1 day.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }

                    seq.setSequenceOrder(order++);
                    seq.setPolicy(policy);
                    policy.getSequences().add(seq);
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

        dialog.add(mainForm, sequenceHeader, helperText, sequencesContainer, addSegmentBtn);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

}