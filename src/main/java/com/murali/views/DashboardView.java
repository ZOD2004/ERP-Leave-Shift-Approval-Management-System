package com.murali.views;


import com.murali.entity.*;
import com.murali.util.SecurityService;
import com.murali.service.*;
import com.murali.views.dashboard.*;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.ObjectProvider;

@PermitAll
@PageTitle("My Dashboard")
@Route(value = "", layout = MainLayout.class)
public class DashboardView extends VerticalLayout {

    private final SecurityService securityService;
    private final ObjectProvider<SuperAdminWorkspace> superAdminWorkspaceProvider;
    private final ObjectProvider<HrAdminWorkspace> hrAdminWorkspaceProvider;
    private final ObjectProvider<HodWorkspace> hodWorkspaceProvider;
    private final ObjectProvider<ManagerWorkspace> managerWorkspaceProvider;
    private final ObjectProvider<EmployeeWorkspace> employeeWorkspaceProvider;

    public DashboardView(SecurityService securityService, ObjectProvider<SuperAdminWorkspace> superAdminWorkspaceProvider, ObjectProvider<HrAdminWorkspace> hrAdminWorkspaceProvider, ObjectProvider<HodWorkspace> hodWorkspaceProvider, ObjectProvider<ManagerWorkspace> managerWorkspaceProvider, ObjectProvider<EmployeeWorkspace> employeeWorkspaceProvider) {

        this.securityService = securityService;
        this.superAdminWorkspaceProvider = superAdminWorkspaceProvider;
        this.hrAdminWorkspaceProvider = hrAdminWorkspaceProvider;
        this.hodWorkspaceProvider = hodWorkspaceProvider;
        this.managerWorkspaceProvider = managerWorkspaceProvider;
        this.employeeWorkspaceProvider = employeeWorkspaceProvider;

        setSizeFull();
        setSpacing(false);
        addClassName("standard-view-container");

        routeUserToWorkspace();
    }

    private void routeUserToWorkspace() {
        if (securityService.hasRole("ROLE_SUPER_ADMIN")) {
            add(superAdminWorkspaceProvider.getObject());
        } else if (securityService.hasRole("ROLE_HR_ADMIN")) {
            add(hrAdminWorkspaceProvider.getObject());
        } else if (securityService.hasRole("ROLE_DEPT_HEAD")) {
            add(hodWorkspaceProvider.getObject());
        } else if (securityService.hasRole("ROLE_MANAGER")) {
            add(managerWorkspaceProvider.getObject());
        } else if (securityService.hasRole("ROLE_EMPLOYEE")) {
            add(employeeWorkspaceProvider.getObject());
        } else {
            add(new H2("No active role assigned. Contact Administrator."));
        }
    }
}