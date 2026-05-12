package com.murali.views;

import com.murali.entity.Role;
import com.murali.service.RoleService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "add-role",layout = MainLayout.class)
@RolesAllowed("ROLE_SUPER_ADMIN")
public class RoleFormView extends VerticalLayout {

    private final RoleService roleService;

    private TextField name = new TextField("Role Name");
    private Button save = new Button("Save Role");

    private BeanValidationBinder<Role> binder = new BeanValidationBinder<>(Role.class);

    public RoleFormView(RoleService roleService) {
        this.roleService = roleService;
        add(new H1("Add new Role"));
        FormLayout formLayout = new FormLayout(name, save);
        formLayout.setMaxWidth("400px");
        add(formLayout);

        binder.bindInstanceFields(this);

        binder.setBean(new Role());

        save.addClickListener(event -> {
            if (binder.isValid()) {
                Role roleToSave = binder.getBean();
                roleService.addRole(roleToSave);

                Notification.show("Role '" + roleToSave.getName() + "' saved!");
                binder.setBean(new Role());
            } else {
                Notification.show("Please fix validation errors");
            }
        });
    }
}
