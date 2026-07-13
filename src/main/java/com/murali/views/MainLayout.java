package com.murali.views;

import com.murali.entity.NavMenuItem;
import com.murali.service.NavigationService;
import com.murali.util.SecurityService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.contextmenu.ContextMenu;
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

    public MainLayout(NavigationService navService, SecurityService securityService) {
        this.navService = navService;
        this.securityService = securityService;
        this.getStyle().set("padding-top","0");
        this.getStyle().set("padding-right","0");
        this.getStyle().set("padding-bottom","0");


        setPrimarySection(Section.DRAWER);
        addDrawerContent();
    }

    private void addDrawerContent() {
        VerticalLayout drawerWrapper = new VerticalLayout();
        drawerWrapper.setSizeFull();
        drawerWrapper.setPadding(false);
        drawerWrapper.setSpacing(false);


        SideNav nav = createNavigation();
        Scroller scroller = new Scroller(nav);
        scroller.addClassNames(LumoUtility.Padding.SMALL);

        Component userCard = createUserCard();

        drawerWrapper.add(scroller, userCard);
        drawerWrapper.expand(scroller);

        addToDrawer(drawerWrapper);
    }

    private SideNav createNavigation() {
        SideNav nav = new SideNav();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<NavMenuItem> menuItems = navService.getMenuItemsForUser(auth);

        nav.addItem(new SideNavItem("My Dashboard", "/", VaadinIcon.DASHBOARD.create()));

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

    private Component createUserCard() {
        String currentUsername = "USER@gmail.com";
        String currentRole = "ROLE";

        if (securityService.getPrincipal() != null) {
            currentUsername = securityService.getPrincipal().getUsername();
            currentRole = formatRoleName(securityService.getPrincipal().getRole());
        }

        Avatar avatar = new Avatar(currentUsername);

        Span nameSpan = new Span(currentUsername);
        nameSpan.addClassNames(LumoUtility.FontWeight.MEDIUM, LumoUtility.FontSize.SMALL);

        Span roleSpan = new Span(currentRole);
        roleSpan.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);

        VerticalLayout textLayout = new VerticalLayout(nameSpan, roleSpan);
        textLayout.setPadding(false);
        textLayout.setSpacing(false);

        HorizontalLayout userCard = new HorizontalLayout(avatar, textLayout);
        userCard.setWidthFull();
        userCard.setAlignItems(FlexComponent.Alignment.CENTER);

        // Replaced inline styles and Lumo border utility with centralized theme classes
        userCard.addClassName("user-card-container");

        ContextMenu contextMenu = new ContextMenu();
        contextMenu.setTarget(userCard);
        contextMenu.setOpenOnClick(true);

        com.vaadin.flow.component.icon.Icon profileIcon = VaadinIcon.USER.create();
        profileIcon.setSize("16px");
        HorizontalLayout profileContent = new HorizontalLayout(profileIcon, new Span("Profile"));
        // Use native flex gap instead of negative margins for clean alignment
        profileContent.setSpacing(true);
        profileContent.setAlignItems(FlexComponent.Alignment.CENTER);

        contextMenu.addItem(profileContent, e -> getUI().ifPresent(ui -> ui.navigate("profile")));

        com.vaadin.flow.component.icon.Icon logoutIcon = VaadinIcon.SIGN_OUT.create();
        logoutIcon.setSize("16px");
        HorizontalLayout logoutContent = new HorizontalLayout(logoutIcon, new Span("Logout"));
        logoutContent.setSpacing(true);
        logoutContent.setAlignItems(FlexComponent.Alignment.CENTER);
        logoutContent.addClassNames(LumoUtility.TextColor.ERROR);

        contextMenu.addItem(logoutContent, e -> {
            SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
            logoutHandler.logout(com.vaadin.flow.server.VaadinServletRequest.getCurrent().getHttpServletRequest(), null, null);
        });

        return userCard;
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