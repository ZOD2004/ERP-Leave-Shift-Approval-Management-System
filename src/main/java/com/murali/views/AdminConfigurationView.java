package com.murali.views;

import com.murali.entity.Holiday;
import com.murali.entity.LeaveApprovalRule;
import com.murali.entity.LeaveType;
import com.murali.entity.Role;
import com.murali.service.HolidayService;
import com.murali.service.LeaveApprovalRuleService;
import com.murali.service.LeaveTypeService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import java.util.Objects;

@RolesAllowed({"ROLE_SUPER_ADMIN","ROLE_HR_ADMIN"})
@PageTitle("System Configuration")
@Route(value = "admin-config", layout = MainLayout.class)
public class AdminConfigurationView extends VerticalLayout {

    private final HolidayService holidayService;
    private final LeaveApprovalRuleService ruleService;
    private final LeaveTypeService leaveTypeService;

    private final VerticalLayout contentContainer = new VerticalLayout();

    private final Grid<Holiday> holidayGrid = new Grid<>(Holiday.class, false);
    private final Grid<LeaveApprovalRule> ruleGrid = new Grid<>(LeaveApprovalRule.class, false);

    public AdminConfigurationView(HolidayService holidayService, LeaveApprovalRuleService ruleService, LeaveTypeService leaveTypeService) {
        this.holidayService = holidayService;
        this.ruleService = ruleService;
        this.leaveTypeService = leaveTypeService;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        buildUI();
    }

    private void buildUI() {
        H2 header = new H2("System Configuration");
        header.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.MEDIUM);

        Tab holidaysTab = new Tab("Public Holidays");
        Tab rulesTab = new Tab("Approval Rules");
        Tabs tabs = new Tabs(holidaysTab, rulesTab);

        contentContainer.setSizeFull();
        contentContainer.setPadding(false);

        // Initial view
        showHolidaysView();

        tabs.addSelectedChangeListener(event -> {
            contentContainer.removeAll();
            if (event.getSelectedTab().equals(holidaysTab)) {
                showHolidaysView();
            } else {
                showRulesView();
            }
        });

        add(header, tabs, contentContainer);
        expand(contentContainer);
    }

    private void showHolidaysView() {
        contentContainer.removeAll();

        // 1. Stats Row
        HorizontalLayout statsRow = new HorizontalLayout(
                createStatsCard("Total Holidays", String.valueOf(holidayService.getAllHolidays().size()), VaadinIcon.CALENDAR, "var(--lumo-primary-color)"),
                createStatsCard("Upcoming", "3", VaadinIcon.CLOCK, "var(--lumo-success-color)") // Note: "3" is hardcoded visually, could be dynamically calculated
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

        ruleGrid.addComponentColumn(rule -> createDeleteButton("approval rule for '" + rule.getLeaveType().getName() + "'", () -> {
            ruleService.deleteRule(rule.getId());
            refreshRules();
        })).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

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
}