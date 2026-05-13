package com.murali.views;

import com.murali.dto.MonthlyPivotRowDTO;
import com.murali.dto.PivotRowDTO;
import com.murali.dto.ShiftAssignmentDTO;
import com.murali.entity.Employee;
import com.murali.entity.Shift;
import com.murali.service.EmployeeService;
import com.murali.service.ShiftAssignmentService;
import com.murali.service.ShiftService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route(value = "shift-assignments",layout = MainLayout.class)
@PageTitle("Shift Management")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class ShiftAssignmentView extends VerticalLayout {

    private final ShiftAssignmentService assignmentService;
    private final EmployeeService employeeService;
    private final ShiftService shiftService;

    private final HorizontalLayout dashboardLayout = new HorizontalLayout();
    private final Tabs tabs = new Tabs();
    private final VerticalLayout contentArea = new VerticalLayout();


    private final Grid<ShiftAssignmentDTO> listGrid = new Grid<>(ShiftAssignmentDTO.class, false);
    private final DatePicker filterDate = new DatePicker("Filter by Date");
    private final TextField searchEmployee = new TextField("Search Employee");

    private final Grid<PivotRowDTO> pivotGrid = new Grid<>(PivotRowDTO.class, false);
    private final DatePicker weekSelector = new DatePicker("Select Week");

    private final Dialog assignmentDialog = new Dialog();
    private final ComboBox<Employee> employeeCombo = new ComboBox<>("Employee");
    private final ComboBox<Shift> shiftCombo = new ComboBox<>("Shift");
    private final DatePicker startDatePicker = new DatePicker("Start Date");
    private final DatePicker endDatePicker = new DatePicker("End Date");

    private final Tabs dialogTabs = new Tabs();
    private final Tab singleTab = new Tab("Single Shift");
    private final Tab bulkTab = new Tab("Bulk Assignment");
    private final VerticalLayout dialogContentArea = new VerticalLayout();
    private final DatePicker singleDatePicker = new DatePicker("Assignment Date");

    private final Grid<MonthlyPivotRowDTO> monthlyGrid = new Grid<>(MonthlyPivotRowDTO.class, false);
    private LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
    private final Span monthLabel = new Span();

    public ShiftAssignmentView(ShiftAssignmentService assignmentService, EmployeeService employeeService, ShiftService shiftService) {
        this.assignmentService = assignmentService;
        this.employeeService = employeeService;
        this.shiftService = shiftService;


        setSizeFull();
        setPadding(false);

        buildDashboard();
        buildAssignmentDialog();

        Tab listTab = new Tab("List View");
        Tab calendarTab = new Tab("Weekly Calendar View");
        Tab monthlyTab = new Tab("Monthly Calendar View");

        tabs.add(listTab, calendarTab, monthlyTab);

        contentArea.setSizeFull();

        tabs.addSelectedChangeListener(event -> {
            contentArea.removeAll();
            if (event.getSelectedTab().equals(listTab)) {
                contentArea.add(buildListLayout());
                refreshListGrid();
            } else if (event.getSelectedTab().equals(calendarTab)) {
                contentArea.add(buildPivotLayout());
                refreshPivotGrid();
            } else if (event.getSelectedTab().equals(monthlyTab)) {
                contentArea.add(buildMonthlyLayout());
                refreshMonthlyGrid();
            }
        });

        Button assignBtn = new Button("Assign Shifts", new Icon(VaadinIcon.CALENDAR_CLOCK));
        assignBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        assignBtn.addClickListener(e -> assignmentDialog.open());

        HorizontalLayout header = new HorizontalLayout(new H2("Shift Operations"), assignBtn);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("padding", "0 1rem");

        add(header, dashboardLayout, tabs, contentArea);

        contentArea.add(buildListLayout());
        refreshListGrid();
    }

    private void buildDashboard() {
        dashboardLayout.setWidthFull();
        dashboardLayout.getStyle().set("padding", "1rem").set("background-color", "var(--lumo-contrast-5pct)");

        // Fetch stats for today
        Map<String, Long> stats = assignmentService.getTodayShiftCounts(LocalDate.now());
        stats.forEach((shiftName, count) -> {
            Div card = new Div();
            card.getStyle()
                    .set("background", "white")
                    .set("padding", "1rem")
                    .set("border-radius", "8px")
                    .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)");

            card.add(new H4(shiftName));
            card.add(new Span(count + " Employees"));
            dashboardLayout.add(card);
        });
    }

    private Component buildListLayout() {
        filterDate.setClearButtonVisible(true);
        filterDate.addValueChangeListener(e -> refreshListGrid());

        searchEmployee.setPlaceholder("Employee Name...");
        searchEmployee.setClearButtonVisible(true);
        searchEmployee.setValueChangeMode(ValueChangeMode.LAZY);
        searchEmployee.addValueChangeListener(e -> refreshListGrid());

        HorizontalLayout toolbar = new HorizontalLayout(filterDate, searchEmployee);
        toolbar.setWidthFull();

        listGrid.setSizeFull();
        listGrid.removeAllColumns();
        listGrid.addColumn(ShiftAssignmentDTO::getEmployeeName).setHeader("Employee").setSortable(true);
        listGrid.addColumn(ShiftAssignmentDTO::getAssignmentDate).setHeader("Date").setSortable(true);

        listGrid.addColumn(new ComponentRenderer<>(assignment -> {
            Span badge = new Span(assignment.getShiftName());
            String type = assignment.getShiftType().toLowerCase();

            if (type.contains("morning")) {
                badge.getElement().getThemeList().add("badge success");
            } else if (type.contains("night")) {
                badge.getElement().getThemeList().add("badge contrast");
            } else {
                badge.getElement().getThemeList().add("badge");
            }
            return badge;
        })).setHeader("Shift");

        DataProvider<ShiftAssignmentDTO, Void> dataProvider = DataProvider.fromCallbacks(
                query -> assignmentService.fetchAssignmentsForGrid(
                        query.getOffset(),
                        query.getLimit(),
                        filterDate.getValue(),
                        searchEmployee.getValue()
                ).stream(),
                query -> (int) assignmentService.fetchAssignmentsForGrid(
                        0, Integer.MAX_VALUE, filterDate.getValue(), searchEmployee.getValue()
                ).getTotalElements()
        );
        listGrid.setDataProvider(dataProvider);

        VerticalLayout layout = new VerticalLayout(toolbar, listGrid);
        layout.setSizeFull();
        layout.setPadding(false);
        return layout;
    }

    private void refreshListGrid() {
        listGrid.getDataProvider().refreshAll();
    }

    private Component buildPivotLayout() {
        weekSelector.setValue(LocalDate.now());
        weekSelector.addValueChangeListener(e -> refreshPivotGrid());

        pivotGrid.setSizeFull();
        setupPivotColumns(LocalDate.now());

        VerticalLayout layout = new VerticalLayout(weekSelector, pivotGrid);
        layout.setSizeFull();
        layout.setPadding(false);
        return layout;
    }

    private void setupPivotColumns(LocalDate selectedDate) {
        pivotGrid.removeAllColumns();
        pivotGrid.addColumn(PivotRowDTO::getEmployeeName).setHeader("Employee").setFrozen(true);

        LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = startOfWeek.plusDays(i);
            String dayName = currentDate.getDayOfWeek().toString().substring(0, 3) + " (" + currentDate.getDayOfMonth() + ")";

            pivotGrid.addColumn(row -> row.getShiftForDate(currentDate))
                    .setHeader(dayName);
        }
    }

    private void refreshPivotGrid() {
        LocalDate startOfWeek = weekSelector.getValue()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        setupPivotColumns(startOfWeek);

        List<PivotRowDTO> rows = new ArrayList<>();
        Map<String, Map<LocalDate, String>> weeklyPivotData =
                assignmentService.getWeeklyPivotData(startOfWeek, endOfWeek);

        if (weeklyPivotData != null && !weeklyPivotData.isEmpty()) {

            for (Map.Entry<String, Map<LocalDate, String>> entry : weeklyPivotData.entrySet()) {

                String employeeName = entry.getKey();
                Map<LocalDate, String> shifts = entry.getValue();

                PivotRowDTO row = new PivotRowDTO(employeeName);

                for (Map.Entry<LocalDate, String> shiftEntry : shifts.entrySet()) {
                    row.addShift(shiftEntry.getKey(), shiftEntry.getValue());
                }

                rows.add(row);
            }

        } else {
            List<ShiftAssignmentDTO> flatAssignments =
                    assignmentService.fetchAssignmentsForCalendarPivot(startOfWeek, endOfWeek);

            Map<String, PivotRowDTO> pivotData = new HashMap<>();

            for (ShiftAssignmentDTO dto : flatAssignments) {

                PivotRowDTO row = pivotData.computeIfAbsent(
                        dto.getEmployeeName(),
                        k -> new PivotRowDTO(dto.getEmployeeName())
                );

                row.addShift(dto.getAssignmentDate(), dto.getShiftName());
            }

            rows = new ArrayList<>(pivotData.values());
        }

        pivotGrid.setItems(rows);
    }


    private void buildAssignmentDialog() {
        assignmentDialog.setHeaderTitle("Assign Shifts");
        dialogTabs.add(singleTab, bulkTab);

        employeeCombo.setItems(employeeService.findAllActive());
        employeeCombo.setItemLabelGenerator(Employee::getFirstName);

        shiftCombo.setItems(shiftService.getShifts());
        shiftCombo.setItemLabelGenerator(Shift::getName);

        FormLayout singleForm = new FormLayout(singleDatePicker);
        FormLayout bulkForm = new FormLayout(startDatePicker, endDatePicker);



        dialogContentArea.add(singleForm);
        dialogContentArea.setPadding(false);

        dialogTabs.addSelectedChangeListener(event -> {
            dialogContentArea.removeAll();
            if (event.getSelectedTab().equals(singleTab)) {
                dialogContentArea.add(singleForm);
            } else {
                dialogContentArea.add(bulkForm);
            }
        });

        singleDatePicker.addValueChangeListener(event -> {
            if (event.getValue() != null && employeeCombo.getValue() != null) {
                try {
                    assignmentService.validateShiftConflict(employeeCombo.getValue().getId(), event.getValue());
                    singleDatePicker.setErrorMessage(null);
                    singleDatePicker.setInvalid(false);
                } catch (Exception ex) {
                    singleDatePicker.setErrorMessage("Conflict: " + ex.getMessage());
                    singleDatePicker.setInvalid(true);
                }
            }
        });

        FormLayout commonFields = new FormLayout(employeeCombo, shiftCombo);

        Button saveBtn = new Button("Assign", e -> saveAssignment());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelBtn = new Button("Cancel", e -> assignmentDialog.close());

        assignmentDialog.add(dialogTabs,commonFields,dialogContentArea);
        assignmentDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void saveAssignment() {
        if (employeeCombo.getValue() == null || shiftCombo.getValue() == null) {
            showNotification("Please select Employee and Shift", NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            if (dialogTabs.getSelectedTab().equals(singleTab)) {
                if (singleDatePicker.getValue() == null) {
                    showNotification("Please select a Date", NotificationVariant.LUMO_ERROR);
                    return;
                }

                ShiftAssignmentDTO dto = new ShiftAssignmentDTO();
                dto.setEmployeeId(employeeCombo.getValue().getId());
                dto.setShiftId(shiftCombo.getValue().getId());
                dto.setAssignmentDate(singleDatePicker.getValue());

                assignmentService.assignSingleShift(dto);
                showNotification("Single Shift Assigned", NotificationVariant.LUMO_SUCCESS);

            } else {
                if (startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
                    showNotification("Please select Start and End Dates", NotificationVariant.LUMO_ERROR);
                    return;
                }

                if(startDatePicker.getValue().isAfter(endDatePicker.getValue())){
                    showNotification("Please select End Date greater than start date", NotificationVariant.LUMO_ERROR);
                    return;
                }

                assignmentService.assignShiftsBulk(
                        employeeCombo.getValue().getId(),
                        shiftCombo.getValue().getId(),
                        startDatePicker.getValue(),
                        endDatePicker.getValue()
                );
                showNotification("Bulk Shifts Assigned", NotificationVariant.LUMO_SUCCESS);
            }

            assignmentDialog.close();
            refreshListGrid();
            refreshPivotGrid();

        } catch (Exception ex) {
            showNotification(ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void showNotification(String message, NotificationVariant notificationVariant) {
        Notification.show(message).addThemeVariants(notificationVariant);
    }

    private Component buildMonthlyLayout() {
        Button prevBtn = new Button(new Icon(VaadinIcon.ANGLE_LEFT), e -> shiftMonth(-1));
        Button nextBtn = new Button(new Icon(VaadinIcon.ANGLE_RIGHT), e -> shiftMonth(1));

        monthLabel.getStyle().set("font-weight", "bold").set("font-size", "1.2em").set("min-width", "150px");
        monthLabel.getStyle().set("text-align", "center");
        updateMonthLabel();

        HorizontalLayout controls = new HorizontalLayout(prevBtn, monthLabel, nextBtn);
        controls.setAlignItems(FlexComponent.Alignment.CENTER);
        controls.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        controls.setWidthFull();

        monthlyGrid.setSizeFull();

        monthlyGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_COLUMN_BORDERS);

        VerticalLayout layout = new VerticalLayout(controls, monthlyGrid);
        layout.setSizeFull();
        layout.setPadding(false);
        return layout;
    }

    private void shiftMonth(int monthsToAdd) {
        currentMonth = currentMonth.plusMonths(monthsToAdd);
        updateMonthLabel();
        refreshMonthlyGrid();
    }

    private void updateMonthLabel() {
        String monthString = currentMonth.getMonth().name();
        monthString = monthString.substring(0, 1).toUpperCase() + monthString.substring(1).toLowerCase();
        monthLabel.setText(monthString + " " + currentMonth.getYear());
    }

    private void setupMonthlyColumns() {
        monthlyGrid.removeAllColumns();

        monthlyGrid.addColumn(MonthlyPivotRowDTO::getEmployeeName)
                .setHeader("Employee")
                .setFrozen(true)
                .setWidth("180px")
                .setFlexGrow(0);

        int daysInMonth = currentMonth.lengthOfMonth();
        for (int i = 1; i <= daysInMonth; i++) {
            final int day = i;
            monthlyGrid.addColumn(row -> row.getShiftForDay(day))
                    .setHeader(String.valueOf(day))
                    .setAutoWidth(true);
//                    .setFlexGrow(0);
        }
    }

    private void refreshMonthlyGrid() {
        setupMonthlyColumns();

        LocalDate startOfMonth = currentMonth.withDayOfMonth(1);
        LocalDate endOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth());

        List<ShiftAssignmentDTO> flatAssignments = assignmentService.fetchAssignmentsForCalendarPivot(startOfMonth, endOfMonth);

        java.util.Map<String, MonthlyPivotRowDTO> pivotData = new java.util.HashMap<>();

        for (ShiftAssignmentDTO dto : flatAssignments) {
            MonthlyPivotRowDTO row = pivotData.computeIfAbsent(
                    dto.getEmployeeName(),
                    k -> new MonthlyPivotRowDTO(dto.getEmployeeName())
            );

            int dayOfMonth = dto.getAssignmentDate().getDayOfMonth();
            row.addShift(dayOfMonth, dto.getShiftName());
        }

        monthlyGrid.setItems(pivotData.values());
    }
}
