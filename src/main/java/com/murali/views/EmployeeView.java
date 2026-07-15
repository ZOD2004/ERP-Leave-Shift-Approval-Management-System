package com.murali.views;

import com.murali.entity.*;
import com.murali.exception.*;
import com.murali.repository.LeaveTypeRepository;
import com.murali.service.*;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDate;
import java.util.stream.Collectors;

import java.util.List;
import java.util.Set;

@Route(value = "add-employees", layout = MainLayout.class)
@PageTitle("Employee Directory")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class EmployeeView extends VerticalLayout {

    private final EmployeeService employeeService;
    private final DepartmentService deptService;
    private final RoleService roleService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final ShiftService shiftService;
    private final LeaveBalanceService leaveBalanceService;

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
    private final ComboBox<Shift> defaultShift = new ComboBox<>("Default Shift");
    private final MultiSelectComboBox<LeaveType> applicableLeavesField = new MultiSelectComboBox<>("Applicable Leave Types");

    private final Button saveBtn = new Button("Save Employee");
    private final Button cancelBtn = new Button("Cancel");

    private final Binder<User> userBinder = new Binder<>(User.class);
    private final Binder<Employee> employeeBinder = new Binder<>(Employee.class);

    private Employee currentEmployee;
    private User currentUser;
    private boolean isExistingUserLinked = false;
    private Shift originalShift;

    public EmployeeView(EmployeeService employeeService, DepartmentService deptService, RoleService roleService, LeaveTypeRepository leaveTypeRepository, ShiftService shiftService, LeaveBalanceService leaveBalanceService) {
        this.employeeService = employeeService;
        this.deptService = deptService;
        this.roleService = roleService;
        this.leaveTypeRepository = leaveTypeRepository;
        this.shiftService = shiftService;
        this.leaveBalanceService = leaveBalanceService;

        setSizeFull();
        addClassName("standard-view-container"); // Standard global layout margins
        configureGrid();
        configureForm();

        searchField.setPlaceholder("Search by code");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());
        searchField.focus(); // Automatically focus search bar on view load

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
        grid.addClassName("standard-surface");
        grid.addItemDoubleClickListener(e -> openForm(e.getItem(), e.getItem().getUser() != null ? e.getItem().getUser() : new User())); // Invoke existing edit handler

        grid.addColumn(Employee::getEmployeeCode).setHeader("Code").setSortable(true).setTooltipGenerator(Employee::getEmployeeCode);
        grid.addColumn(Employee::getFirstName).setHeader("First Name").setSortable(true).setTooltipGenerator(Employee::getFirstName);
        grid.addColumn(emp -> emp.getDepartment() != null ? emp.getDepartment().getName() : "None").setHeader("Department").setTooltipGenerator(emp -> emp.getDepartment() != null ? emp.getDepartment().getName() : "None");
        grid.addColumn(emp -> emp.getManager() != null ? emp.getManager().getFirstName() : "None").setHeader("Manager").setTooltipGenerator(emp -> emp.getManager() != null ? emp.getManager().getFirstName() : "None");

        grid.addComponentColumn(employee -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openForm(employee, employee.getUser() != null ? employee.getUser() : new User()));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> softDeleteEmployee(employee));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions");
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Employee Onboarding");

        department.setItems(deptService.findAll());
        department.setItemLabelGenerator(Department::getName);
        role.setItemLabelGenerator(r -> formatRoleName(r.getName()));
        manager.setItemLabelGenerator(e -> e.getFirstName() + " (" + e.getEmployeeCode() + ")");
        manager.setClearButtonVisible(true);
        defaultShift.setItems(shiftService.findAll());
        defaultShift.setItemLabelGenerator(Shift::getName);

        HasValue.ValueChangeListener<AbstractField.ComponentValueChangeEvent<?, ?>> updateManagerList = event -> {
            if (event.isFromClient()) {
                Department selectedDept = department.getValue();
                Role selectedRole = role.getValue();

                if (selectedDept != null && selectedRole != null) {
                    manager.setItems(employeeService.findAvailableReportingManagers(selectedDept.getId(), selectedRole));
                } else {
                    manager.setItems(java.util.Collections.emptyList());
                }
            }
        };
        department.addValueChangeListener(updateManagerList);
        role.addValueChangeListener(updateManagerList);

        userBinder.forField(username).asRequired("Required").bind(User::getUsername, User::setUsername);
        userBinder.forField(email).asRequired("Required").bind(User::getEmail, User::setEmail);
        userBinder.forField(password).withValidator(pass -> {
            boolean isNewUser = (currentUser == null || currentUser.getId() == null);
            if (isNewUser) {
                return pass != null && !pass.isEmpty();
            }
            return true;
        }, "Password is required for new users").bind(user -> "", (user, pass) -> {
            if (pass != null && !pass.isEmpty()) {
                user.setPasswordHash(pass);
            }
        });
        userBinder.forField(role).asRequired("Role is required").bind(User::getRole, User::setRole);

        employeeBinder.forField(firstName).asRequired("Required").bind(Employee::getFirstName, Employee::setFirstName);
        employeeBinder.forField(employeeCode).asRequired("Required").bind(Employee::getEmployeeCode, Employee::setEmployeeCode);
        employeeBinder.forField(department).asRequired("Required").bind(Employee::getDepartment, Employee::setDepartment);
        employeeBinder.forField(manager).bind(Employee::getManager, Employee::setManager);
        employeeBinder.forField(defaultShift).bind(Employee::getDefaultShift, Employee::setDefaultShift);

        applicableLeavesField.setItems(leaveTypeRepository.findAll());
        applicableLeavesField.setItemLabelGenerator(LeaveType::getName);

        FormLayout userLayout = new FormLayout(username, email, password, role);
        FormLayout empLayout = new FormLayout(employeeCode, firstName, department, manager, defaultShift);

        VerticalLayout dialogBody = new VerticalLayout(new H3("User Identity"), userLayout, new H3("Work Profile"), empLayout, applicableLeavesField);
        dialogBody.setPadding(false);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> handleSave());
        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(dialogBody);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(Employee employee, User user) {
        currentEmployee = employee;
        currentUser = user;
        originalShift = employee.getDefaultShift();

        List<Role> allRoles = roleService.getRoles();
        boolean isCurrentSuperAdmin = user.getRole() != null && "ROLE_SUPER_ADMIN".equals(user.getRole().getName());

        if (isCurrentSuperAdmin) {
            role.setItems(allRoles);
            role.setReadOnly(true);
        } else {
            List<Role> filteredRoles = allRoles.stream().filter(r -> !"ROLE_SUPER_ADMIN".equals(r.getName())).collect(Collectors.toList());
            role.setItems(filteredRoles);
            role.setReadOnly(false);
        }

        if (currentEmployee.getDepartment() != null && currentUser.getRole() != null) {
            manager.setItems(employeeService.findAvailableReportingManagers(currentEmployee.getDepartment().getId(), currentUser.getRole()));
        } else {
            manager.setItems(java.util.Collections.emptyList());
        }

        employeeBinder.readBean(currentEmployee);
        userBinder.readBean(currentUser);
        username.setEnabled(currentEmployee.getId() == null);

        if (currentEmployee.getId() != null) {
            int currentYear = LocalDate.now().getYear();
            Set<Long> existingLeaveIds = leaveBalanceService.getBalancesForEmployee(currentEmployee.getId(), currentYear).stream().map(b -> b.getLeaveType().getId()).collect(Collectors.toSet());

            Set<LeaveType> itemsToSelect = applicableLeavesField.getListDataView().getItems().filter(leaveType -> existingLeaveIds.contains(leaveType.getId())).collect(Collectors.toSet());

            applicableLeavesField.setValue(itemsToSelect);
        } else {
            applicableLeavesField.clear();
        }

        boolean isNewUser = (currentUser.getId() == null);
        password.setEnabled(isNewUser);
        if (!isNewUser) {
            password.setPlaceholder("Managed by user profile");
        } else {
            password.setPlaceholder("Enter new password");
        }

        password.clear();
        formDialog.open();
    }

    private void handleSave() {
        try {
            userBinder.writeBean(currentUser);
            employeeBinder.writeBean(currentEmployee);
            Set<LeaveType> selectedLeaves = applicableLeavesField.getValue();

            // CHECK FOR SHIFT CHANGE
            boolean isExistingEmployee = currentEmployee.getId() != null;
            Shift newShift = currentEmployee.getDefaultShift();

            boolean shiftChanged = isExistingEmployee
                    && newShift != null
                    && (originalShift == null || !originalShift.getId().equals(newShift.getId()));

            if (shiftChanged) {
                openShiftEffectiveDateDialog(selectedLeaves);
            } else {
                // New employee or shift didn't change, just save
                if (!isExistingEmployee && currentEmployee.getShiftEffectiveDate() == null) {
                    currentEmployee.setShiftEffectiveDate(LocalDate.now()); // Default new hires to today
                }
                executeFinalSave(selectedLeaves);
            }

        } catch (ValidationException ex) {
            showNotification("Please fill in all required fields correctly.", NotificationVariant.LUMO_ERROR);
        } catch (Exception ex) {
            showNotification("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }
    private void openShiftEffectiveDateDialog(Set<LeaveType> selectedLeaves) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Shift Change Detected");

        Paragraph warning = new Paragraph("You have changed this employee's default shift. Please select when this new schedule should take effect.");

        DatePicker effectiveDatePicker = new DatePicker("Effective Date");

        if (currentEmployee.getShiftEffectiveDate() != null) {
            effectiveDatePicker.setValue(currentEmployee.getShiftEffectiveDate());
        } else {
            effectiveDatePicker.setValue(LocalDate.now());
        }

        Button confirmBtn = new Button("Confirm & Save", e -> {
            if (effectiveDatePicker.getValue() == null) {
                showNotification("Effective date is required.", NotificationVariant.LUMO_ERROR);
                return;
            }

            currentEmployee.setShiftEffectiveDate(effectiveDatePicker.getValue());
            dialog.close();
            executeFinalSave(selectedLeaves);
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.add(warning, effectiveDatePicker);
        dialog.getFooter().add(cancelBtn, confirmBtn);
        dialog.open();
    }

    private void executeFinalSave(Set<LeaveType> selectedLeaves) {
        try {
            employeeService.createOrUpdateEmployeeWithUser(currentEmployee, currentUser, isExistingUserLinked, selectedLeaves);

            showNotification("Saved successfully!", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();

        } catch (HodConflictException ex) {
            openHodSwapDialog(ex);
        } catch (HodDemotionConflictException ex) {
            openHodDemotionDialog(ex);
        } catch (ManagerDemotionConflictException ex) {
            openManagerDemotionDialog(ex);
        } catch (IllegalStateException ex) {
            showNotification(ex.getMessage(), NotificationVariant.LUMO_ERROR);
        } catch (Exception ex) {
            showNotification("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }
    private void openHodSwapDialog(HodConflictException ex) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("HOD Conflict");

        dialog.add(new Paragraph(ex.getMessage() + " The current HOD will be demoted to a standard employee."));

        Button confirmBtn = new Button("Yes, Swap HOD", e -> {
            try {
                employeeService.swapHodAndSave(currentEmployee, currentUser, isExistingUserLinked, applicableLeavesField.getValue(), ex.getCurrentHodId());
                showNotification("HOD Swapped successfully!", NotificationVariant.LUMO_SUCCESS);
                updateList();
                dialog.close();
                formDialog.close();
            } catch (Exception err) {
                showNotification("Error: " + err.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), confirmBtn);
        dialog.open();
    }

    private void openHodDemotionDialog(HodDemotionConflictException ex) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("HOD Demotion Conflict");
        dialog.add(new Paragraph(ex.getMessage()));

        ComboBox<Employee> replacementCombo = new ComboBox<>("Select New HOD");
        List<Employee> eligibleHods = employeeService.findAllActive().stream().filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(ex.getDepartmentId())).filter(e -> !e.getId().equals(currentEmployee.getId())).filter(e -> e.getUser() != null && e.getUser().getRole().getHierarchyWeight() >= 3).collect(Collectors.toList());

        replacementCombo.setItems(eligibleHods);
        replacementCombo.setItemLabelGenerator(Employee::getFirstName);

        Button confirmBtn = new Button("Replace HOD & Demote", e -> {
            try {
                employeeService.replaceHodAndDemote(currentEmployee, currentUser, isExistingUserLinked, applicableLeavesField.getValue(), replacementCombo.getValue().getId());
                showNotification("Demoted and HOD replaced successfully!", NotificationVariant.LUMO_SUCCESS);
                updateList();
                dialog.close();
                formDialog.close();
            } catch (Exception err) {
                showNotification("Error: " + err.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirmBtn.setEnabled(false);
        replacementCombo.addValueChangeListener(e -> confirmBtn.setEnabled(e.getValue() != null));

        dialog.add(replacementCombo);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), confirmBtn);
        dialog.open();
    }

    private void openManagerDemotionDialog(ManagerDemotionConflictException ex) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Manager Demotion Conflict");
        dialog.add(new Paragraph(ex.getMessage()));

        ComboBox<Employee> replacementCombo = new ComboBox<>("Select Replacement Manager");
        List<Employee> eligibleReplacements = employeeService.findEligibleReplacements(ex.getDepartmentId(), currentEmployee.getId(), currentEmployee.getUser().getRole().getHierarchyWeight());

        replacementCombo.setItems(eligibleReplacements);
        replacementCombo.setItemLabelGenerator(Employee::getFirstName);

        Button confirmBtn = new Button("Reassign Subordinates & Demote", e -> {
            try {
                employeeService.reassignSubordinatesAndDemote(currentEmployee, currentUser, isExistingUserLinked, applicableLeavesField.getValue(), replacementCombo.getValue().getId());
                showNotification("Demoted and Subordinates reassigned successfully!", NotificationVariant.LUMO_SUCCESS);
                updateList();
                dialog.close();
                formDialog.close();
            } catch (Exception err) {
                showNotification("Error: " + err.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirmBtn.setEnabled(false);
        replacementCombo.addValueChangeListener(e -> confirmBtn.setEnabled(e.getValue() != null));

        dialog.add(replacementCombo);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), confirmBtn);
        dialog.open();
    }

    private void softDeleteEmployee(Employee employee) {
        try {
            employeeService.deactivateEmployee(employee);
            showNotification("Employee deactivated.", NotificationVariant.LUMO_SUCCESS);
            updateList();
        } catch (HodDeleteConflictException ex) {
            openHodDeleteDialog(ex, employee);
        } catch (ManagerDeleteConflictException ex) {
            openManagerDeleteDialog(ex, employee);
        }
    }

    private void openHodDeleteDialog(HodDeleteConflictException ex, Employee employee) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Cannot Deactivate HOD");
        dialog.add(new Paragraph(ex.getMessage()));

        ComboBox<Employee> replacementCombo = new ComboBox<>("Select New HOD");
        int currentWeight = 0;
        if (employee.getUser() != null && employee.getUser().getRole() != null) {
            currentWeight = employee.getUser().getRole().getHierarchyWeight();
        }

        List<Employee> available = employeeService.findEligibleReplacements(ex.getDepartmentId(), employee.getId(), currentWeight);

        replacementCombo.setItems(available);
        replacementCombo.setItemLabelGenerator(Employee::getFirstName);

        Button confirmBtn = new Button("Replace & Deactivate", e -> {
            employeeService.replaceHodAndDeactivate(employee, replacementCombo.getValue().getId());
            showNotification("Replaced and deactivated.", NotificationVariant.LUMO_SUCCESS);
            updateList();
            dialog.close();
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirmBtn.setEnabled(false);
        replacementCombo.addValueChangeListener(e -> confirmBtn.setEnabled(e.getValue() != null));

        dialog.add(replacementCombo);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), confirmBtn);
        dialog.open();
    }

    private void openManagerDeleteDialog(ManagerDeleteConflictException ex, Employee employee) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Cannot Deactivate Manager");
        dialog.add(new Paragraph(ex.getMessage()));

        ComboBox<Employee> replacementCombo = new ComboBox<>("Select Replacement Manager");
        int currentWeight = 0;
        if (employee.getUser() != null && employee.getUser().getRole() != null) {
            currentWeight = employee.getUser().getRole().getHierarchyWeight();
        }
        List<Employee> available = employeeService.findEligibleReplacements(employee.getDepartment().getId(), employee.getId(), currentWeight);
        replacementCombo.setItems(available);
        replacementCombo.setItemLabelGenerator(Employee::getFirstName);

        Button confirmBtn = new Button("Reassign & Deactivate", e -> {
            employeeService.reassignSubordinatesAndDeactivate(employee, replacementCombo.getValue().getId());
            showNotification("Subordinates reassigned and deactivated.", NotificationVariant.LUMO_SUCCESS);
            updateList();
            dialog.close();
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirmBtn.setEnabled(false);
        replacementCombo.addValueChangeListener(e -> confirmBtn.setEnabled(e.getValue() != null));

        dialog.add(replacementCombo);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), confirmBtn);
        dialog.open();
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