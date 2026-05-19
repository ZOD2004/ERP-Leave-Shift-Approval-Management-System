package com.murali.views;

import com.murali.entity.Role;
import com.murali.service.RoleService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "add-role", layout = MainLayout.class)
@PageTitle("Manage Roles")
@RolesAllowed({"ROLE_SUPER_ADMIN"})
public class RoleView extends VerticalLayout {

    private final RoleService roleService; // Assuming you have a RoleService

    private final Grid<Role> grid = new Grid<>(Role.class, false);
    private final Button addBtn = new Button("Add New Role", new Icon(VaadinIcon.PLUS));

    private final Dialog formDialog = new Dialog();
    private final TextField nameField = new TextField("Role Name");

    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private final Binder<Role> binder = new BeanValidationBinder<>(Role.class);
    private Role currentRole;

    public RoleView(RoleService roleService) {
        this.roleService = roleService;

        setSizeFull();
        configureGrid();
        configureForm();

        // Layout: Title on left, Add Button on right
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new Role()));

        H1 title = new H1("Role Configuration");
        title.getStyle().set("margin", "0");

        HorizontalLayout toolbar = new HorizontalLayout(title, addBtn);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        add(toolbar, grid);
        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(Role::getName).setHeader("Role Name").setSortable(true);

        // Action Column: Edit and Delete
        grid.addComponentColumn(role -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openForm(role));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> confirmAndDelete(role));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Role Details");

        nameField.setPlaceholder("e.g. ROLE_MANAGER");


        FormLayout formLayout = new FormLayout();
        formLayout.add(nameField);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        // Bindings
        binder.forField(nameField)
                .asRequired("Role name is required")
                .bind(Role::getName, Role::setName);


        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveRole());

        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(formLayout);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(Role role) {
        this.currentRole = role;
        binder.readBean(currentRole);
        formDialog.open();
    }

    private void saveRole() {
        try {
            binder.writeBean(currentRole);
            roleService.save(currentRole);

            showNotification("Role saved successfully", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();
        } catch (ValidationException e) {
            showNotification("Please check the form for errors", NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            showNotification("Error saving role: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
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
        grid.setItems(roleService.findAll());
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
}