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
import com.vaadin.flow.component.html.H4;
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
import java.util.*;
import java.util.stream.Collectors;

@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
@PageTitle("System Configuration")
@Route(value = "admin-config", layout = MainLayout.class)
public class AdminConfigurationView extends VerticalLayout {

    private final HolidayService holidayService;
    private final LeaveApprovalRuleService ruleService;

    private final VerticalLayout contentContainer = new VerticalLayout();

    private final Grid<LeaveApprovalPolicy> policyGrid = new Grid<>(LeaveApprovalPolicy.class, false);
    private final Grid<Holiday> holidayGrid = new Grid<>(Holiday.class, false);

    public AdminConfigurationView(HolidayService holidayService, LeaveApprovalRuleService ruleService) {
        this.holidayService = holidayService;
        this.ruleService = ruleService;

        setSizeFull();
        addClassName("standard-view-container"); // Standard global layout margins

        buildUI();
    }

    private void buildUI() {
        H2 header = new H2("System Configuration");
        header.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.MEDIUM);

        Tab holidaysTab = new Tab("Public Holidays");
        Tab policiesTab = new Tab("Approval Policies");

        Tabs tabs = new Tabs(holidaysTab, policiesTab);

        contentContainer.setSizeFull();
        contentContainer.setPadding(false);

        showHolidaysView();

        tabs.addSelectedChangeListener(event -> {
            contentContainer.removeAll();
            if (event.getSelectedTab().equals(holidaysTab)) {
                showHolidaysView();
            } else if (event.getSelectedTab().equals(policiesTab)) {
                showPoliciesView();
            }
        });

        add(header, tabs, contentContainer);
        expand(contentContainer);
    }

    private void showHolidaysView() {
        contentContainer.removeAll();

        LocalDate today = LocalDate.now();

        long upcomingCount = holidayService.countUpcomingHolidaysInMonth(today, today.getMonthValue(), today.getYear());

        HorizontalLayout statsRow = new HorizontalLayout(createStatsCard("Total Holidays", String.valueOf(holidayService.getAllHolidays().size()), VaadinIcon.CALENDAR, "var(--app-primary-color)"), createStatsCard("Upcoming", String.valueOf(upcomingCount), VaadinIcon.CLOCK, "#24a148") // Standardized success green
        );
        statsRow.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        TextField searchField = new TextField();
        searchField.setPlaceholder("Search holidays...");
        searchField.focus(); // Automatically focus search bar on view load
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> {
            holidayGrid.setItems(holidayService.getAllHolidays().stream().filter(h -> h.getName().toLowerCase().contains(e.getValue().toLowerCase())).toList());
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
        holidayGrid.addClassName("standard-surface");
        holidayGrid.addItemDoubleClickListener(e -> openHolidayDialog(e.getItem())); // Invoke existing edit handler

        holidayGrid.addColumn(Holiday::getName).setHeader("Holiday Name").setSortable(true).setFlexGrow(1);
        holidayGrid.addColumn(new LocalDateRenderer<>(Holiday::getHolidayDate, "dd MMM yyyy")).setHeader("Date").setAutoWidth(true).setSortable(true);

        holidayGrid.addComponentColumn(holiday -> createDeleteButton("holiday '" + holiday.getName() + "'", () -> {
            holidayService.deleteHoliday(holiday.getId());
            refreshHolidays();
        })).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        holidayGrid.getStyle().set("--vaadin-grid-row-height", "60px");
        VerticalLayout gridContainer = new VerticalLayout(toolbar, holidayGrid);
        gridContainer.addClassName("standard-surface");
        gridContainer.setPadding(true);
        gridContainer.setSizeFull();

        refreshHolidays();
        contentContainer.setSpacing(true);
        contentContainer.add(statsRow, gridContainer);
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
                Notification.show("Holiday saved.", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshHolidays();
            } catch (ValidationException ex) {
                Notification.show("Please fix errors.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
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
        card.addClassNames("standard-surface", "hoverable");
        card.setSpacing(false);
        card.setWidth("250px");
        card.getStyle().set("border-top", "4px solid " + color).set("min-height", "110px");

        Icon iconComp = icon.create();
        iconComp.getStyle().set("color", color);

        Span titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD);

        H3 valueH3 = new H3(value);
        valueH3.addClassNames(LumoUtility.Margin.Vertical.SMALL);
        valueH3.getStyle().set("font-size", "32px");

        HorizontalLayout header = new HorizontalLayout(iconComp, titleSpan);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        card.add(header, valueH3);
        return card;
    }

    private void showPoliciesView() {
        contentContainer.removeAll();

        HorizontalLayout statsRow = new HorizontalLayout(createStatsCard("Active Policies", String.valueOf(ruleService.getAllPolicies().size()), VaadinIcon.FILE_TEXT, "var(--app-primary-color)"));
        statsRow.addClassNames(LumoUtility.Margin.Bottom.MEDIUM);

        Button addBtn = new Button("New Policy", VaadinIcon.PLUS.create());
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openPolicyDialog(new LeaveApprovalPolicy()));

        HorizontalLayout toolbar = new HorizontalLayout(addBtn);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        policyGrid.removeAllColumns();
        policyGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        policyGrid.setSizeFull();
        policyGrid.addClassName("standard-surface");
        policyGrid.addItemDoubleClickListener(e -> openPolicyDialog(ruleService.getPolicyById(e.getItem().getId()))); // Invoke existing edit handler

        policyGrid.addComponentColumn(this::createPolicyCard).setHeader("Policy Details").setAutoWidth(true).setFlexGrow(1);

        policyGrid.addColumn(policy -> policy.getRules() != null ? policy.getRules().size() + " Rules" : "0 Rules").setHeader("Complexity").setAutoWidth(true);

        policyGrid.addComponentColumn(policy -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create());
            editBtn.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> {
                LeaveApprovalPolicy fullyLoadedPolicy = ruleService.getPolicyById(policy.getId());
                openPolicyDialog(fullyLoadedPolicy);
            });

            Button deleteBtn = createDeleteButton("policy '" + policy.getName() + "'", () -> {
                ruleService.deletePolicy(policy.getId());
                refreshPolicies();
            });

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        policyGrid.getStyle().set("--vaadin-grid-row-height", "70px");
        VerticalLayout gridContainer = new VerticalLayout(toolbar, policyGrid);
        gridContainer.addClassName("standard-surface");
        gridContainer.setPadding(true);
        gridContainer.setSizeFull();

        refreshPolicies();
//        contentContainer.setSpacing(true);
        contentContainer.add(statsRow, gridContainer);
    }

    private void openPolicyDialog(LeaveApprovalPolicy policy) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(policy.getId() == null ? "Create Approval Policy" : "Edit Approval Policy");
        dialog.setWidth("900px");

        TextField nameField = new TextField("Policy Name");
        nameField.setWidthFull();

        Binder<LeaveApprovalPolicy> binder = new Binder<>(LeaveApprovalPolicy.class);
        binder.forField(nameField).asRequired("Name is required").bind(LeaveApprovalPolicy::getName, LeaveApprovalPolicy::setName);
        binder.readBean(policy);

        H3 rulesHeader = new H3("Duration Groups & Approvers");
        rulesHeader.addClassNames(LumoUtility.Margin.Top.LARGE, LumoUtility.Margin.Bottom.SMALL, LumoUtility.FontSize.MEDIUM);

        VerticalLayout tiersContainer = new VerticalLayout();
        tiersContainer.setPadding(false);
        tiersContainer.setSpacing(true);

        List<TierUIContext> memoryTiers = new ArrayList<>();

        if (policy.getRules() != null && !policy.getRules().isEmpty()) {
            // Group existing rules by min/max to recreate the UI structure
            Map<String, List<LeaveApprovalRule>> groupedRules = policy.getRules().stream().collect(Collectors.groupingBy(r -> r.getMinDays() + "_" + r.getMaxDays()));

            for (List<LeaveApprovalRule> tierRules : groupedRules.values()) {
                tierRules.sort(Comparator.comparing(LeaveApprovalRule::getApprovalLevel));
                addTierGroup(tiersContainer, memoryTiers, tierRules.get(0).getMinDays(), tierRules.get(0).getMaxDays(), tierRules);
            }
        } else {
            addTierGroup(tiersContainer, memoryTiers, null, null, new ArrayList<>());
        }

        Button addTierBtn = new Button("Add Duration Group", VaadinIcon.PLUS.create(), e -> {
            addTierGroup(tiersContainer, memoryTiers, null, null, new ArrayList<>());
        });
        addTierBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button saveBtn = new Button("Save", e -> {
            try {
                binder.writeBean(policy);

                List<LeaveApprovalRule> newRulesList = extractAndValidateRules(memoryTiers);
                if (newRulesList == null) return; // Validation failed, notification already shown

                if (policy.getRules() == null) policy.setRules(new ArrayList<>());
                policy.getRules().clear();

                for (LeaveApprovalRule r : newRulesList) {
                    r.setPolicy(policy);
                    policy.getRules().add(r);
                }

                ruleService.savePolicy(policy);
                Notification.show("Policy saved.", 3000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshPolicies();
            } catch (ValidationException ex) {
                Notification.show("Please fix the policy name.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.add(nameField, rulesHeader, tiersContainer, addTierBtn);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    // --- DYNAMIC NESTED UI HELPERS ---

    private static class TierUIContext {
        NumberField minField;
        NumberField maxField;
        List<ComboBox<Role>> roleBoxes = new ArrayList<>();
    }

    private void addTierGroup(VerticalLayout container, List<TierUIContext> memoryTiers, BigDecimal min, BigDecimal max, List<LeaveApprovalRule> existingApprovers) {
        TierUIContext tierContext = new TierUIContext();

        VerticalLayout tierCard = new VerticalLayout();
        tierCard.addClassNames("standard-surface", "hoverable");
        tierCard.getStyle().set("border-left", "4px solid var(--app-primary-color)");
        HorizontalLayout headerRow = new HorizontalLayout();
        headerRow.setWidthFull();
        headerRow.setAlignItems(FlexComponent.Alignment.END);

        tierContext.minField = new NumberField("Min Days");
        tierContext.minField.setWidth("120px");
        if (min != null) tierContext.minField.setValue(min.doubleValue());

        // UPDATE: Make Max Days optional and show Infinity placeholder
        tierContext.maxField = new NumberField("Max Days (Empty = ∞)");
        tierContext.maxField.setWidth("180px");
        tierContext.maxField.setPlaceholder("∞ (Infinity)");
        tierContext.maxField.setClearButtonVisible(true);

        // Only fill the field if it's less than our infinity threshold (99.0)
        if (max != null && max.doubleValue() < 99.0) {
            tierContext.maxField.setValue(max.doubleValue());
        }

        Button removeTierBtn = new Button(VaadinIcon.TRASH.create(), e -> {
            container.remove(tierCard);
            memoryTiers.remove(tierContext);
        });
        removeTierBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);

        headerRow.add(tierContext.minField, tierContext.maxField, removeTierBtn);
        headerRow.expand(tierContext.maxField);

        VerticalLayout approversContainer = new VerticalLayout();
        approversContainer.setPadding(false);
        approversContainer.setSpacing(false);
        approversContainer.getStyle().set("padding-left", "24px");

        if (existingApprovers != null && !existingApprovers.isEmpty()) {
            for (LeaveApprovalRule rule : existingApprovers) {
                addApproverRow(approversContainer, tierContext, rule.getRequiredRole());
            }
        } else {
            addApproverRow(approversContainer, tierContext, null);
        }

        Button addApproverBtn = new Button("Add Approver", VaadinIcon.PLUS.create(), e -> addApproverRow(approversContainer, tierContext, null));
        addApproverBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        addApproverBtn.addClassNames(LumoUtility.Margin.Top.MEDIUM);

        tierCard.add(headerRow, new H4("Approver Sequence"), approversContainer, addApproverBtn);
        container.add(tierCard);
        memoryTiers.add(tierContext);
    }

    private void addApproverRow(VerticalLayout container, TierUIContext tierContext, Role selectedRole) {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setWidthFull();

        Span levelBadge = new Span(VaadinIcon.ARROW_DOWN.create());
        levelBadge.addClassNames(LumoUtility.TextColor.TERTIARY);

        ComboBox<Role> roleBox = new ComboBox<>();
        roleBox.setItems(ruleService.getAllRoles());
        roleBox.setItemLabelGenerator(Role::getName);
        roleBox.setPlaceholder("Select Role...");
        roleBox.setWidthFull();
        if (selectedRole != null) roleBox.setValue(selectedRole);

        tierContext.roleBoxes.add(roleBox);

        Button removeBtn = new Button(VaadinIcon.CLOSE.create(), e -> {
            container.remove(row);
            tierContext.roleBoxes.remove(roleBox);
        });
        removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);

        row.add(levelBadge, roleBox, removeBtn);
        row.expand(roleBox);
        container.add(row);
    }

    private List<LeaveApprovalRule> extractAndValidateRules(List<TierUIContext> memoryTiers) {
        List<LeaveApprovalRule> newRules = new ArrayList<>();
        List<double[]> durationRanges = new ArrayList<>();

        if (memoryTiers.isEmpty()) {
            Notification.show("Please add at least one duration group.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            return null;
        }

        double highestMaxFound = -1.0;

        for (TierUIContext context : memoryTiers) {
            if (context.minField.getValue() == null) {
                Notification.show("All Min day fields must be filled.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return null;
            }

            double min = context.minField.getValue();

            // UPDATE: If Max is empty, treat it as Infinity (999.0)
            double max = context.maxField.getValue() != null ? context.maxField.getValue() : 999.0;

            if (min > max) {
                Notification.show("Min days (" + min + ") cannot be greater than Max days (" + max + ").", 4000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return null;
            }

            for (double[] existingRange : durationRanges) {
                if (min <= existingRange[1] && max >= existingRange[0]) {
                    Notification.show("Duration groups cannot overlap! Group (" + min + " to " + (max >= 99.0 ? "∞" : max) + ") conflicts with another.", 5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return null;
                }
            }
            durationRanges.add(new double[]{min, max});

            if (max > highestMaxFound) {
                highestMaxFound = max;
            }

            int levelSequence = 1;
            boolean hasApprovers = false;

            for (ComboBox<Role> roleBox : context.roleBoxes) {
                if (roleBox.getValue() != null) {
                    LeaveApprovalRule rule = new LeaveApprovalRule();
                    rule.setMinDays(BigDecimal.valueOf(min));
                    rule.setMaxDays(BigDecimal.valueOf(max));
                    rule.setApprovalLevel(levelSequence++);
                    rule.setRequiredRole(roleBox.getValue());
                    newRules.add(rule);
                    hasApprovers = true;
                }
            }

            if (!hasApprovers) {
                Notification.show("Duration group (" + min + " to " + (max >= 99.0 ? "∞" : max) + " days) is missing approvers.", 4000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return null;
            }
        }

        if (highestMaxFound < 999.0) {
            for (LeaveApprovalRule rule : newRules) {
                if (rule.getMaxDays().doubleValue() == highestMaxFound) {
                    rule.setMaxDays(BigDecimal.valueOf(999.0));
                }
            }
            Notification.show("Note: The final duration group was automatically extended to ∞ to prevent gaps.", 5000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }

        return newRules;
    }

    private void refreshPolicies() {
        policyGrid.setItems(ruleService.getAllPolicies());
    }

    private Component createPolicyCard(LeaveApprovalPolicy policy) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(false);
        card.setSpacing(false);

        H3 nameSpan = new H3(policy.getName());
        nameSpan.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD, LumoUtility.TextColor.BODY);

        VerticalLayout tiersLayout = new VerticalLayout();
        tiersLayout.setPadding(false);
        tiersLayout.setSpacing(true);
        tiersLayout.addClassNames(LumoUtility.Margin.Top.SMALL);

        if (policy.getRules() == null || policy.getRules().isEmpty()) {
            Span emptyBadge = new Span("No rules configured");
            emptyBadge.getElement().getThemeList().add("badge error");
            tiersLayout.add(emptyBadge);
        } else {
            Map<String, List<LeaveApprovalRule>> groupedRules = policy.getRules().stream().sorted(Comparator.comparing(LeaveApprovalRule::getMinDays)).collect(Collectors.groupingBy(r -> r.getMinDays() + "_" + r.getMaxDays(), LinkedHashMap::new, Collectors.toList()));

            for (List<LeaveApprovalRule> tierRules : groupedRules.values()) {
                tierRules.sort(Comparator.comparing(LeaveApprovalRule::getApprovalLevel));

                String range = tierRules.get(0).getMinDays() + " - " + (tierRules.get(0).getMaxDays().doubleValue() >= 99.0 ? "∞" : tierRules.get(0).getMaxDays()) + " Days";

                String approverChain = tierRules.stream().map(r -> r.getRequiredRole().getName().replace("ROLE_", "").replace("_", " ")).collect(Collectors.joining(" → "));

                HorizontalLayout tierRow = new HorizontalLayout();
                tierRow.setAlignItems(FlexComponent.Alignment.CENTER);
                tierRow.addClassNames(LumoUtility.Margin.Bottom.XSMALL);

                Span rangeBadge = new Span(range);
                rangeBadge.getElement().getThemeList().add("badge");
                rangeBadge.getStyle().set("min-width", "110px");
                rangeBadge.getStyle().set("text-align", "center");

                Icon arrowIcon = VaadinIcon.ANGLE_DOUBLE_RIGHT.create();
                arrowIcon.setSize("12px");
                arrowIcon.addClassNames(LumoUtility.TextColor.TERTIARY);

                Span chainText = new Span(approverChain);
                chainText.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.MEDIUM);

                tierRow.add(rangeBadge, arrowIcon, chainText);
                tiersLayout.add(tierRow);
            }
        }

        card.add(nameSpan, tiersLayout);
        return card;
    }
}