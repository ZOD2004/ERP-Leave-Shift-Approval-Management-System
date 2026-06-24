package com.murali.views;

import com.murali.entity.NavMenuItem;
import com.murali.service.NavigationService;
import com.murali.util.SecurityService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import java.util.List;

@PermitAll
public class MainLayout extends AppLayout {

    private final NavigationService navService;
    private final SecurityService securityService;
    private H1 viewTitle;

    public MainLayout(NavigationService navService, SecurityService securityService) {
        this.navService = navService;
        this.securityService = securityService;

        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addDrawerContent() {
        SideNav nav = createNavigation();
        Scroller scroller = new Scroller(nav);

        addToDrawer(scroller);
    }

    private SideNav createNavigation() {
        SideNav nav = new SideNav();
        nav.addClassNames(LumoUtility.Padding.SMALL);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<NavMenuItem> menuItems = navService.getMenuItemsForUser(auth);
        nav.addItem(new SideNavItem("My Dashboard", "dashboard", VaadinIcon.DASHBOARD.create()));

        for (NavMenuItem item : menuItems) {
            try {
                VaadinIcon icon = VaadinIcon.valueOf(item.getIconName().toUpperCase());
                nav.addItem(new SideNavItem(item.getLabel(), item.getPath(), icon.create()));
            } catch (Exception ex) {
                nav.addItem(new SideNavItem(item.getLabel(), item.getPath(), VaadinIcon.FILE.create()));
            }
        }
        return nav;
    }

    private void addHeaderContent() {
        viewTitle = new H1();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        String currentUsername = "USER";
        String currentRole = "ROLE";

        if (securityService.getPrincipal() != null) {
            currentUsername = securityService.getPrincipal().getUsername();
            currentRole = formatRoleName(securityService.getPrincipal().getRole());
        }

        Span nameSpan = new Span(currentUsername);
        nameSpan.addClassNames(LumoUtility.FontWeight.MEDIUM, LumoUtility.FontSize.MEDIUM);

        Span roleSpan = new Span(currentRole);
        roleSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout profileClickZone = new VerticalLayout(nameSpan, roleSpan);
        profileClickZone.setPadding(false);
        profileClickZone.setSpacing(false);
        profileClickZone.getStyle().set("cursor", "pointer");

        profileClickZone.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate("profile")));

        Button logoutButton = new Button("Logout", VaadinIcon.SIGN_OUT.create(), e -> {
            SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
            logoutHandler.logout(com.vaadin.flow.server.VaadinServletRequest.getCurrent().getHttpServletRequest(), null, null);
        });
        logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        logoutButton.setTooltipText("Log out");

        HorizontalLayout userHeaderMenu = new HorizontalLayout(profileClickZone, logoutButton);
        userHeaderMenu.setAlignItems(FlexComponent.Alignment.CENTER);
        userHeaderMenu.setSpacing(true);

        HorizontalLayout topRow = new HorizontalLayout(viewTitle, userHeaderMenu);
        topRow.setWidthFull();
        topRow.expand(viewTitle);
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);
        topRow.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM, LumoUtility.Height.MEDIUM);

        addToNavbar(true, topRow);
    }

    private String formatRoleName(String rawRole) {
        if (rawRole == null || rawRole.trim().isEmpty()) {
            return "Unknown Role";
        }

        switch (rawRole.toUpperCase()) {
            case "ROLE_SUPER_ADMIN":
                return "Super Admin";
            case "ROLE_HR_ADMIN":
                return "HR Admin";
            case "ROLE_EMPLOYEE":
                return "Employee";
            case "ROLE_MANAGER":
                return "Manager";
            case "ROLE_AUDITOR":
                return "Auditor";
            case "ROLE_DEPT_HEAD":
                return "Department Head";
            default:
                String cleanString = rawRole.replaceFirst("^ROLE_", "").replace("_", " ");
                String[] words = cleanString.split(" ");
                StringBuilder formatted = new StringBuilder();
                for (String word : words) {
                    if (!word.isEmpty()) {
                        formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
                    }
                }
                return formatted.toString().trim();

        }
    }
}