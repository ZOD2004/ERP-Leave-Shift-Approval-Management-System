package com.murali.views;

import com.murali.entity.LeaveApprovalPolicy;
import com.murali.entity.LeaveType;
import com.murali.service.LeaveApprovalRuleService;
import com.murali.service.LeaveTypeService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;

@Route(value = "add-leave-types", layout = MainLayout.class)
@PageTitle("Manage Leave Types")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class LeaveTypeView extends VerticalLayout {

    private final LeaveTypeService leaveTypeService;
    private final LeaveApprovalRuleService ruleService;

    private static final Set<String> SYSTEM_CODES = Set.of("HDL-001", "EMG-001", "SL-001","CL-001","EL-001","WFH-001", "UPL-001");

    private final Grid<LeaveType> grid = new Grid<>(LeaveType.class, false);
    private final TextField searchField = new TextField();
    private final Button addBtn = new Button("Add New Leave Type", new Icon(VaadinIcon.PLUS));
    private final Button bulkDeleteBtn = new Button("Delete Selected", new Icon(VaadinIcon.TRASH));

    private final Dialog formDialog = new Dialog();
    private final TextField nameField = new TextField("Name");
    private final TextField codeField = new TextField("Code");
    private final Checkbox paidCheckbox = new Checkbox("Is Paid");
    private final IntegerField maxDaysField = new IntegerField("Max Days Per Year");

    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private final Binder<LeaveType> binder = new Binder<>(LeaveType.class);
    private final ComboBox<LeaveApprovalPolicy> policyBox = new ComboBox<>("Approval Policy");
    private LeaveType currentLeaveType;

    public LeaveTypeView(LeaveTypeService leaveTypeService, LeaveApprovalRuleService ruleService) {
        this.leaveTypeService = leaveTypeService;
        this.ruleService = ruleService;

        setSizeFull();
        addClassName("standard-view-container");
        configureGrid();
        configureForm();
        searchField.setPlaceholder("Search by name or code...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());
        searchField.focus();

        bulkDeleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        bulkDeleteBtn.setEnabled(false);
        bulkDeleteBtn.setTooltipText("Select one or more leave types from the grid to enable bulk deletion.");
        bulkDeleteBtn.addClickListener(e -> confirmAndBulkDelete(grid.getSelectedItems()));

        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new LeaveType()));

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addBtn, bulkDeleteBtn);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, searchField);
        add(new H2("Leave Types Configuration"), toolbar, grid);
        updateList();
    }

    private void configureGrid() {

        grid.setSizeFull();
        grid.addClassName("standard-surface");

        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.addSelectionListener(e -> {
            boolean hasSelection = !e.getAllSelectedItems().isEmpty();
            bulkDeleteBtn.setEnabled(hasSelection);
            if (hasSelection) {
                bulkDeleteBtn.setTooltipText("Click to delete selected leave types.");
            } else {
                bulkDeleteBtn.setTooltipText("Select one or more leave types from the grid to enable bulk deletion.");
            }
        });
        grid.addItemDoubleClickListener(e -> openForm(e.getItem()));

        grid.addItemDoubleClickListener(e -> openForm(e.getItem()));

        grid.addColumn(LeaveType::getName).setHeader("Name").setSortable(true).setTooltipGenerator(LeaveType::getName);
        grid.addColumn(LeaveType::getCode).setHeader("Code").setSortable(true).setTooltipGenerator(LeaveType::getCode);
        grid.addColumn(leaveType -> leaveType.getPaid() ? "Paid" : "Unpaid").setHeader("Status")
                .setTooltipGenerator(leaveType -> leaveType.getPaid() ? "Paid" : "Unpaid");
        grid.addColumn(LeaveType::getMaxDaysPerYear).setHeader("Max Days");
        grid.addColumn(leaveType -> leaveType.getApprovalPolicy() != null ?
                        leaveType.getApprovalPolicy().getName() : "No Policy")
                .setHeader("Approval Policy").setSortable(true)
                .setTooltipGenerator(leaveType -> leaveType.getApprovalPolicy() != null ?
                        leaveType.getApprovalPolicy().getName() : "No Policy");

        grid.addComponentColumn(leaveType -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openForm(leaveType));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> confirmAndDelete(leaveType));
            if (leaveType.getCode() != null && SYSTEM_CODES.contains(leaveType.getCode().toUpperCase())) {
                deleteBtn.setEnabled(false);
                deleteBtn.setTooltipText("System reserved leave type cannot be deleted.");
                editBtn.setTooltipText("System code cannot be changed during edit.");
            }

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions");
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Leave Type Details");
        policyBox.setItems(ruleService.getAllPolicies());
        policyBox.setItemLabelGenerator(LeaveApprovalPolicy::getName);

        FormLayout formLayout = new FormLayout();
        formLayout.add(nameField, codeField, maxDaysField, paidCheckbox, policyBox);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );
        formLayout.setColspan(paidCheckbox, 2);


        codeField.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                codeField.setValue(e.getValue().toUpperCase());
            }
        });
        binder.forField(policyBox)
                .asRequired("An Approval Policy is required")
                .bind(LeaveType::getApprovalPolicy, LeaveType::setApprovalPolicy);

        binder.forField(nameField)
                .asRequired("Name is required")
                .bind(LeaveType::getName, LeaveType::setName);

        binder.forField(codeField)
                .asRequired("Code is required")
                .withValidator(code -> {
                    if (currentLeaveType != null && currentLeaveType.getId() != null) {
                        return true;
                    }
                    return code == null || !SYSTEM_CODES.contains(code.toUpperCase());
                }, "This code is reserved for system use")
                .bind(LeaveType::getCode, LeaveType::setCode);

        binder.forField(maxDaysField)
                .asRequired("Max days is required")
                .withValidator(days -> days >= 0, "Days must be 0 or greater")
                .bind(LeaveType::getMaxDaysPerYear, LeaveType::setMaxDaysPerYear);

        binder.forField(paidCheckbox)
                .bind(LeaveType::getPaid, LeaveType::setPaid);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveLeaveType());

        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(formLayout);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(LeaveType leaveType) {
        currentLeaveType = leaveType;

        policyBox.setItems(ruleService.getAllPolicies());

        binder.readBean(currentLeaveType);

        boolean isSystemCode = leaveType.getCode() != null && SYSTEM_CODES.contains(leaveType.getCode().toUpperCase());
        codeField.setReadOnly(isSystemCode);

        formDialog.open();
    }
    private void saveLeaveType() {
        try {
            binder.writeBean(currentLeaveType);

            if (currentLeaveType.getId() == null) {
                leaveTypeService.addLeaveType(currentLeaveType);
            } else {
                leaveTypeService.editLeaveType(currentLeaveType.getId(), currentLeaveType);
            }

            showNotification("Leave Type saved successfully", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();

        } catch (ValidationException e) {
            showNotification("Please check the form for errors", NotificationVariant.LUMO_ERROR);
        } catch (DataIntegrityViolationException e) {
            showNotification("Failed to save. Leave code or name already exists.", NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            showNotification("An unexpected error occurred: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void deleteLeaveType(LeaveType leaveType) {
        if (leaveType.getCode() != null && SYSTEM_CODES.contains(leaveType.getCode().toUpperCase())) {
            showNotification("System reserved leave types cannot be deleted.", NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            leaveTypeService.deleteLeaveType(leaveType.getId());
            showNotification("Leave Type deleted", NotificationVariant.LUMO_SUCCESS);
            updateList();
        } catch (DataIntegrityViolationException e) {
            showNotification("Cannot delete this type as it is already assigned to employees or leave requests.", NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            showNotification("An unexpected error occurred: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }
    private void confirmAndDelete(LeaveType leaveType) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Leave Type?");
        dialog.setText("Are you sure you want to permanently delete the leave type '" + leaveType.getName() + "'?");

        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");

        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(event -> deleteLeaveType(leaveType));

        dialog.open();
    }

    private void updateList() {
        String searchTerm = searchField.getValue();
        if (searchTerm == null || searchTerm.isEmpty()) {
            grid.setItems(leaveTypeService.getAllLeaveTypes());
        } else {
            grid.setItems(leaveTypeService.search(searchTerm));
        }
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
    private void confirmAndBulkDelete(Set<LeaveType> selectedLeaveTypes) {
        if (selectedLeaveTypes == null || selectedLeaveTypes.isEmpty()) {
            return;
        }

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete " + selectedLeaveTypes.size() + " Leave Types?");
        dialog.setText("Are you sure you want to permanently delete the selected leave types? System reserved types will be skipped.");

        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");

        dialog.setConfirmText("Delete All");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(event -> executeBulkDelete(selectedLeaveTypes));

        dialog.open();
    }

    private void executeBulkDelete(Set<LeaveType> selectedLeaveTypes) {
        int successCount = 0;
        int systemSkipCount = 0;
        int inUseSkipCount = 0;

        for (LeaveType leaveType : selectedLeaveTypes) {
            if (leaveType.getCode() != null && SYSTEM_CODES.contains(leaveType.getCode().toUpperCase())) {
                systemSkipCount++;
                continue;
            }

            try {
                leaveTypeService.deleteLeaveType(leaveType.getId());
                successCount++;
            } catch (DataIntegrityViolationException e) {
                inUseSkipCount++;
            } catch (Exception e) {
                showNotification("Error deleting " + leaveType.getName() + ": " + e.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        }

        StringBuilder summary = new StringBuilder("Deleted " + successCount + " leave types.");
        if (systemSkipCount > 0) {
            summary.append(" Skipped ").append(systemSkipCount).append(" system types.");
        }
        if (inUseSkipCount > 0) {
            summary.append(" Skipped ").append(inUseSkipCount).append(" types currently in use.");
        }

        NotificationVariant variant = (successCount > 0) ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_WARNING;
        showNotification(summary.toString(), variant);

        grid.deselectAll();
        updateList();
    }
}