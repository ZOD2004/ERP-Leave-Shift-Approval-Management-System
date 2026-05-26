package com.murali.views;

import com.murali.entity.NavMenuItem;
import com.murali.entity.User;
import com.murali.service.NavigationService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import java.util.List;

//TODO : dont commitpls
@PermitAll
public class MainLayout extends AppLayout {

    private final NavigationService navService;
    private H1 viewTitle;
    private Icon toggleIcon;

    public MainLayout(NavigationService navService) {
        this.navService = navService;

        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addHeaderContent() {
        viewTitle = new H1();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        HorizontalLayout topRow = new HorizontalLayout(viewTitle);
        topRow.setWidthFull();
        topRow.expand(viewTitle);
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);
        topRow.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM, LumoUtility.Height.MEDIUM);

        toggleIcon = VaadinIcon.CHEVRON_LEFT.create();
        Button toggleButton = new Button(toggleIcon, e -> {
            setDrawerOpened(!isDrawerOpened());
            updateToggleIcon();
        });
        toggleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        VerticalLayout navWrapper = new VerticalLayout(topRow);
        navWrapper.setPadding(false);
        navWrapper.setSpacing(false);

        toggleButton.getStyle().set("position", "fixed");
        toggleButton.getStyle().set("bottom", "10px");
        toggleButton.getStyle().set("left", "10px");
        toggleButton.getStyle().set("z-index", "10");

        addToNavbar(true, topRow, toggleButton);
    }

    private Footer createFooter() {
        Footer layout = new Footer();
        layout.addClassNames(
                LumoUtility.Display.FLEX,
                LumoUtility.AlignItems.CENTER,
                LumoUtility.Padding.SMALL,
                LumoUtility.Border.TOP,
                LumoUtility.BorderColor.CONTRAST_10);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = (auth != null) ? auth.getName() : "USER";

        Avatar avatar = new Avatar(currentUsername);
        avatar.addClassNames(LumoUtility.Margin.Right.SMALL);

        Span name = new Span(currentUsername);
        name.addClassNames(LumoUtility.FontWeight.MEDIUM, LumoUtility.FontSize.XSMALL, LumoUtility.Flex.GROW);

        HorizontalLayout avatarLayout = new HorizontalLayout(avatar, name);
        avatarLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        avatarLayout.setSpacing(false);

        MenuBar userMenu = new MenuBar();
        userMenu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        MenuItem avatarItem = userMenu.addItem(avatarLayout);
        SubMenu avatarSubMenu = avatarItem.getSubMenu();

        avatarSubMenu.addItem("My Profile", e -> getUI().ifPresent(ui -> ui.navigate("profile")));

        avatarSubMenu.addItem("Log out", e -> {
            SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
            logoutHandler.logout(
                    com.vaadin.flow.server.VaadinServletRequest.getCurrent().getHttpServletRequest(),
                    null,
                    null
            );
        });

        layout.add(userMenu);
        return layout;
    }

    private void addDrawerContent() {
        SideNav nav = createNavigation();
        Scroller scroller = new Scroller(nav);

        addToDrawer(scroller, createFooter());
    }

    private SideNav createNavigation() {
        SideNav nav = new SideNav();
        nav.addClassNames(LumoUtility.Padding.SMALL);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<NavMenuItem> menuItems = navService.getMenuItemsForUser(auth);
        nav.addItem(new SideNavItem("My Dashboard","dashboard",VaadinIcon.DASHBOARD.create()));

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

    private void updateToggleIcon() {
        if (isDrawerOpened()) {
            toggleIcon.getElement().setAttribute("icon", "vaadin:chevron-left");
        } else {
            toggleIcon.getElement().setAttribute("icon", "vaadin:chevron-right");
        }
    }
}