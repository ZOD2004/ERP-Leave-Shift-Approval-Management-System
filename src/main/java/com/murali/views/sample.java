package com.murali.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

@Route("")
//@RolesAllowed("Super Admin")
//@RolesAllowed("HR Admin")
//@RolesAllowed("Employee")
@RolesAllowed("Manager")
//@RolesAllowed("Auditor")

public class sample extends VerticalLayout {
    public sample(){
        add(new H1("Created"));
    }
}
