package com.murali.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

@PermitAll
public class MainLayout extends AppLayout {

    private final AuthenticationContext authContext;
    private Button logout;

    public MainLayout(AuthenticationContext authContext) {
        this.authContext = authContext;
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        H1 logo = new H1("Leave & Shift Approval Management System");

        logout = new Button("Log out", e -> logoutProcedure());
        org.springframework.security.core.userdetails.User currUser = (org.springframework.security.core.userdetails.User)authContext.getAuthenticatedUser(Object.class).get();
        Span userLabel = new Span("Hello, " + currUser.getUsername());
        userLabel.getStyle().set("margin-right", "10px");

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), logo, userLabel,logout);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(logo);
        header.setWidthFull();

        addToNavbar(header);
    }

    private void logoutProcedure(){
        UI.getCurrent().getSession().close();
        authContext.logout();
        UI.getCurrent().getPage().setLocation("/login");
    }

    private void createDrawer() {
        VerticalLayout sideMenu = new VerticalLayout();

//        // 1. Common View (Dashboard)
//        sideMenu.add(new RouterLink("Dashboard", DashboardView.class));
//
//        // 2. Super Admin Only: System & Role Management
//        if (authContext.hasAuthority("Super Admin")) {
//            sideMenu.add(new RouterLink("System Roles", RoleManagementView.class));
//        }
//
//        // 3. Super Admin or HR Admin: User Management
//        if (authContext.hasAnyAuthority("Super Admin", "HR Admin")) {
//            sideMenu.add(new RouterLink("User Directory", UserManagementView.class));
//        }
//
//        // 4. Employee or Manager: Leave Management
//        if (authContext.hasAnyAuthority("Employee", "Manager")) {
//            sideMenu.add(new RouterLink("Leave Requests", LeaveRequestView.class));
//        }
//
//        // 5. Auditor or Super Admin: Reports/Audit
//        if (authContext.hasAnyAuthority("Auditor", "Super Admin")) {
//            sideMenu.add(new RouterLink("Audit Logs", AuditLogView.class));
//        }

        addToDrawer(sideMenu);
    }
}
