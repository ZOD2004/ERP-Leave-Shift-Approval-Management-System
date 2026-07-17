package com.murali.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView() {
        addClassNames("login-view", "standard-view-container"); // Standard global layout margins
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        LoginI18n i18n = LoginI18n.createDefault();

        i18n.getForm().setUsername("Email");
        i18n.getForm().setTitle("Sign in");
        i18n.getForm().setSubmit("Login");
        i18n.getForm().setPassword("Password");

        login.setI18n(i18n);
        login.setForgotPasswordButtonVisible(false);
        login.setAction("login");
        login.addClassName("standard-surface"); // Applies standard border/shadow to login box

        H1 title = new H1("ERP Leave & Shift Approval Management System");
        title.getStyle().set("text-align", "center"); // Ensures title is centered above box

        add(title, login);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        if (beforeEnterEvent.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error")) {
            login.setError(true);
        }

    }
}
