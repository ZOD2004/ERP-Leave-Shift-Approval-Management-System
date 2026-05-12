package com.murali.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "",layout = MainLayout.class)
//@RolesAllowed("ROLE_SUPER_ADMIN")
//@RolesAllowed("ROLE_HR_ADMIN")
//@RolesAllowed("Employee")
//@RolesAllowed("Manager")
//@RolesAllowed("ROLE_AUDITOR")
@PermitAll

public class sample extends VerticalLayout {
    public sample(){
        add(new H1("Created"));
    }
}
