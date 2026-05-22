package com.murali.views;

import com.murali.entity.*;
import com.murali.repository.LeaveTypeRepository;
import com.murali.service.DepartmentService;
import com.murali.service.EmployeeService;
import com.murali.service.RoleService;
import com.murali.service.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Route(value = "add-employees",layout = MainLayout.class)
@PageTitle("Employee Directory")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class EmployeeView extends VerticalLayout {

    private final EmployeeService employeeService;
    private final DepartmentService deptService;
    private final RoleService roleService;
    private final UserService userService;
    private final LeaveTypeRepository leaveTypeRepository;

    private final Grid<Employee> grid = new Grid<>(Employee.class, false);
    private final TextField searchField = new TextField();
    private final Button addBtn = new Button("Onboard New Employee", new Icon(VaadinIcon.PLUS));

    private final Dialog formDialog = new Dialog();
    private final TextField username = new TextField("Username");
    private final PasswordField password = new PasswordField("Password");
    private final TextField email = new TextField("Email");
    private final ComboBox<Role> role = new ComboBox<>("Security Role");

    private final TextField firstName = new TextField("First Name");
    private final TextField employeeCode = new TextField("Employee Code");
    private final ComboBox<Department> department = new ComboBox<>("Department");
    private final ComboBox<Employee> manager = new ComboBox<>("Reporting Manager");

    private final Button saveBtn = new Button("Save Employee");
    private final Button cancelBtn = new Button("Cancel");

    private final Binder<User> userBinder = new BeanValidationBinder<>(User.class);
    private final Binder<Employee> employeeBinder = new BeanValidationBinder<>(Employee.class);

    MultiSelectComboBox<LeaveType> applicableLeavesField = new MultiSelectComboBox<>("Applicable Leave Types");

    private Employee currentEmployee;
    private User currentUser;
    private boolean isExistingUserLinked = false;

    public EmployeeView(EmployeeService employeeService, DepartmentService deptService,
                        RoleService roleService, UserService userService, LeaveTypeRepository leaveTypeRepository) {
        this.employeeService = employeeService;
        this.deptService = deptService;
        this.roleService = roleService;
        this.userService = userService;
        this.leaveTypeRepository = leaveTypeRepository;

        setSizeFull();
        configureGrid();
        configureForm();

        searchField.setPlaceholder("Search by code");
        searchField.setTooltipText("Search by Employee code");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());

        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new Employee(), new User()));

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addBtn);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, searchField);

        add(new H2("Employee Directory"), toolbar, grid);
        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(Employee::getEmployeeCode).setHeader("Code").setSortable(true);
        grid.addColumn(Employee::getFirstName).setHeader("First Name").setSortable(true);

        grid.addColumn(emp -> emp.getDepartment() != null ? emp.getDepartment().getName() : "None")
                .setHeader("Department");

        grid.addColumn(emp -> emp.getManager() != null ? emp.getManager().getFirstName() : "None")
                .setHeader("Manager");

        grid.addComponentColumn(employee -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openForm(employee, employee.getUser() != null ? employee.getUser() : new User()));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.setTooltipText("Only soft Deletes changes is_active to false");
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> softDeleteEmployee(employee));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions");
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Employee Onboarding");

        department.setItems(deptService.findAll());
        department.setItemLabelGenerator(Department::getName);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean canManageDepartments = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                        a.getAuthority().equals("ROLE_HR_ADMIN"));

        HorizontalLayout departmentWrapper = new HorizontalLayout(department);
        departmentWrapper.setWidthFull();
        departmentWrapper.setAlignItems(Alignment.BASELINE);
        department.setWidthFull();

        if (canManageDepartments) {
            Button addDeptBtn = new Button(new Icon(VaadinIcon.PLUS));
            addDeptBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            addDeptBtn.setTooltipText("Add Department");

            addDeptBtn.getStyle().set("color", "var(--lumo-secondary-text-color)");

            addDeptBtn.addClickListener(e -> {
                formDialog.close();
                UI.getCurrent().navigate("add-departments");
            });

            departmentWrapper.add(addDeptBtn);
        }

        role.setItems(roleService.getRoles());
        role.setItemLabelGenerator(Role::getName);

        manager.setItemLabelGenerator(e -> e.getFirstName() + " (" + e.getEmployeeCode() + ")");
        manager.setClearButtonVisible(true);

        department.addValueChangeListener(event -> {
            Department selectedDept = event.getValue();
            if (selectedDept != null) {
                manager.setItems(employeeService.findAvailableManagers(selectedDept.getId()));
            } else {
                manager.setItems(java.util.Collections.emptyList());
            }
        });

        username.setValueChangeMode(ValueChangeMode.TIMEOUT);
        username.setValueChangeTimeout(2500);
        username.addValueChangeListener(event -> {
            String inputUsername = event.getValue();
            if (inputUsername != null && !inputUsername.isEmpty()) {
                User existingUser = userService.findByUsername(inputUsername);

                if (existingUser != null) {
                    currentUser = existingUser;
                    userBinder.readBean(currentUser);
                    isExistingUserLinked = true;
                    toggleUserFields(false);
                    showNotification("Existing user found. Linking to profile.", NotificationVariant.LUMO_SUCCESS);
                } else {
                    isExistingUserLinked = false;
                    toggleUserFields(true);
                }
            }
        });

        userBinder.forField(username).asRequired("Required").bind(User::getUsername, User::setUsername);
        userBinder.forField(email).asRequired("Required").bind(User::getEmail, User::setEmail);
        userBinder.forField(password).bind(User::getPasswordHash, User::setPasswordHash);

        userBinder.forField(role).asRequired("Role is required").bind(User::getRole, User::setRole);

        employeeBinder.forField(firstName).asRequired("Required").bind(Employee::getFirstName, Employee::setFirstName);
        employeeBinder.forField(employeeCode).asRequired("Required").bind(Employee::getEmployeeCode, Employee::setEmployeeCode);
        employeeBinder.forField(department).asRequired("Required").bind(Employee::getDepartment, Employee::setDepartment);
        employeeBinder.forField(manager).bind(Employee::getManager, Employee::setManager);

        applicableLeavesField.setItems(leaveTypeRepository.findAll());
        applicableLeavesField.setItemLabelGenerator(LeaveType::getName);
        applicableLeavesField.setPlaceholder("Defaults to ALL if left blank");

        FormLayout userLayout = new FormLayout(username, email, password, role);
        FormLayout empLayout = new FormLayout(employeeCode, firstName, departmentWrapper, manager);

        VerticalLayout dialogBody = new VerticalLayout(
                new H3("User Identity"), userLayout,
                new H3("Work Profile"), empLayout,applicableLeavesField
        );
        dialogBody.setPadding(false);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> handleSave());
        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(dialogBody);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void toggleUserFields(boolean enabled) {
        password.setEnabled(enabled);
        email.setEnabled(enabled);
        role.setEnabled(true);
        if (enabled && !isExistingUserLinked) {
            password.clear();
            email.clear();
            role.clear();
        }
    }

    private void openForm(Employee employee, User user) {
        currentEmployee = employee;
        currentUser = user;

        if (currentEmployee.getDepartment() != null) {
            manager.setItems(employeeService.findAvailableManagers(currentEmployee.getDepartment().getId()));
        } else {
            manager.setItems(java.util.Collections.emptyList());
        }

        employeeBinder.readBean(currentEmployee);
        userBinder.readBean(currentUser);

        if (currentEmployee.getApplicableLeaveTypes() != null) {
            applicableLeavesField.setValue(currentEmployee.getApplicableLeaveTypes());
        } else {
            applicableLeavesField.clear();
        }

        username.setEnabled(currentEmployee.getId() == null);

        formDialog.open();
    }

    private void handleSave() {
        try {
            userBinder.writeBean(currentUser);
            employeeBinder.writeBean(currentEmployee);

            java.util.Set<LeaveType> selectedLeaves = applicableLeavesField.getValue();
            employeeService.createOrUpdateEmployeeWithUser(currentEmployee, currentUser, isExistingUserLinked, selectedLeaves);

            showNotification("Saved successfully!", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();
        } catch (Exception ex) {
            showNotification("Please check the form for errors.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void softDeleteEmployee(Employee employee) {
        employeeService.deactivateEmployee(employee);
        showNotification("Employee deactivated.", NotificationVariant.LUMO_SUCCESS);
        updateList();
    }

    private void updateList() {
        String searchTerm = searchField.getValue();
        if (searchTerm == null || searchTerm.isEmpty()) {
            grid.setItems(employeeService.findAllActive());
        } else {
            grid.setItems(employeeService.searchActive(searchTerm));
        }
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
}