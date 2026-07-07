package com.murali.views;

import com.murali.entity.Role;
import com.murali.service.RoleService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "add-role", layout = MainLayout.class)
@PageTitle("Manage Roles")
@RolesAllowed({"ROLE_SUPER_ADMIN"})
public class RoleView extends VerticalLayout {

    private final RoleService roleService;

    private static final Set<String> SYSTEM_ROLES = Set.of(
            "ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN", "ROLE_EMPLOYEE",
            "ROLE_MANAGER", "ROLE_AUDITOR", "ROLE_DEPT_HEAD"
    );

    private final Grid<Role> grid = new Grid<>(Role.class, false);
    private final TextField searchField = new TextField();
    private final Button addBtn = new Button("Add New Role", new Icon(VaadinIcon.PLUS));

    private final Dialog formDialog = new Dialog();
    private final TextField nameField = new TextField("Role Name");
    private final ComboBox<Integer> weightField = new ComboBox<>("Hierarchy Weight");

    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private final Binder<Role> binder = new BeanValidationBinder<>(Role.class);
    private Role currentRole;

    public RoleView(RoleService roleService) {
        this.roleService = roleService;

        setSizeFull();
        configureGrid();
        configureForm();

        searchField.setPlaceholder("Search roles...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());

        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new Role()));

        H2 title = new H2("Role Configuration");
        title.getStyle().set("margin-top", "0");

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addBtn);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setFlexGrow(1, searchField);

        add(title, toolbar, grid);
        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();

        grid.addColumn(Role::getName)
                .setHeader("Role Name")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addComponentColumn(role -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openForm(role));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> confirmAndDelete(role));

            boolean isSystemRole = role.getName() != null && SYSTEM_ROLES.contains(role.getName().toUpperCase());

            if (isSystemRole) {
                deleteBtn.setEnabled(false);
                deleteBtn.setTooltipText("System reserved roles cannot be deleted.");
            }

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setFlexGrow(0);
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Role Details");
        formDialog.setWidth("400px");

        nameField.setPlaceholder("e.g. ROLE_MANAGER");
        nameField.setPrefixComponent(new Icon(VaadinIcon.USER_STAR));
        nameField.setWidthFull();

        nameField.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                nameField.setValue(e.getValue().toUpperCase().replaceAll("\\s+", "_"));
            }
        });

        FormLayout formLayout = new FormLayout();
        formLayout.add(nameField, weightField);
        binder.forField(weightField)
                .asRequired("Weight is required")
                .bind(Role::getHierarchyWeight, Role::setHierarchyWeight);

        binder.forField(nameField)
                .asRequired("Role name is required")
                .withValidator(name -> {
                    if (currentRole != null && currentRole.getId() == null) {
                        return !SYSTEM_ROLES.contains(name.toUpperCase());
                    }
                    return true;
                }, "This role name is reserved by the system.")
                .bind(Role::getName, Role::setName);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveRole());

        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(formLayout);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(Role role) {
        this.currentRole = role;

        int totalRoles = roleService.findAll().size();
        int maxWeight = (role.getId() == null) ? totalRoles + 1 : totalRoles;

        List<Integer> weightOptions = java.util.stream.IntStream.rangeClosed(1, maxWeight)
                .boxed()
                .collect(Collectors.toList());

        weightField.setItems(weightOptions);
        boolean isSystemRole = role.getName() != null && SYSTEM_ROLES.contains(role.getName().toUpperCase());
        nameField.setReadOnly(isSystemRole);

        binder.readBean(currentRole);
        formDialog.open();
    }

    private void saveRole() {
        try {
            binder.writeBean(currentRole);

            Integer desiredWeight = currentRole.getHierarchyWeight();

            Role conflictingRole = roleService.getRoleByWeight(desiredWeight);

            if (conflictingRole != null && (currentRole.getId() == null || !conflictingRole.getId().equals(currentRole.getId()))) {
                promptSwapConfirmation(conflictingRole);
            } else {
                executeSave(false);
            }

        } catch (ValidationException e) {
            showNotification("Please check the form for errors", NotificationVariant.LUMO_ERROR);
        }
    }
    private void confirmAndDelete(Role role) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Role?");
        dialog.setText("Are you sure you want to permanently delete the role '" + role.getName() + "'?");

        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");

        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> deleteRole(role));

        dialog.open();
    }

    private void deleteRole(Role role) {
        try {
            roleService.delete(role);
            showNotification("Role deleted", NotificationVariant.LUMO_SUCCESS);
            updateList();
        } catch (Exception e) {
            showNotification("Cannot delete role while it is assigned to users.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateList() {
        List<Role> allRoles = roleService.findAll();
        String searchTerm = searchField.getValue();

        if (searchTerm == null || searchTerm.isEmpty()) {
            grid.setItems(allRoles);
        } else {
            List<Role> filteredRoles = allRoles.stream()
                    .filter(r -> r.getName() != null && r.getName().toLowerCase().contains(searchTerm.toLowerCase()))
                    .collect(Collectors.toList());
            grid.setItems(filteredRoles);
        }
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }

    private void promptSwapConfirmation(Role conflictingRole) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Swap Role Weights?");

        String currentName = currentRole.getName();
        if (currentName == null || currentName.trim().isEmpty()) {
            currentName = "the new role";
        }

        dialog.setText(String.format("Weight %d is currently assigned to %s. Do you want to swap weights between %s and %s?",
                currentRole.getHierarchyWeight(), conflictingRole.getName(), currentName, conflictingRole.getName()));

        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");

        dialog.setConfirmText("Yes, Swap");
        dialog.setConfirmButtonTheme("primary");

        dialog.addConfirmListener(event -> executeSave(true));

        dialog.open();
    }

    private void executeSave(boolean isSwapApproved) {
        try {
            roleService.save(currentRole, isSwapApproved);
            showNotification("Role saved successfully", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();
        } catch (Exception e) {
            showNotification("Error saving role. It might already exist.", NotificationVariant.LUMO_ERROR);
        }
    }
}