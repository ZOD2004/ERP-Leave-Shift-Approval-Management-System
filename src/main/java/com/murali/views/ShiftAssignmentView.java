package com.murali.views;

import com.murali.dto.*;
import com.murali.entity.Employee;
import com.murali.entity.Shift;
import com.murali.service.EmployeeService;
import com.murali.service.ShiftAssignmentService;
import com.murali.service.ShiftService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.timepicker.TimePicker;
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

    private final Button batchPlanBtn = new Button("Batch Planning", new Icon(VaadinIcon.USERS));
    private final Dialog batchSetupDialog = new Dialog();
    private final MultiSelectComboBox<Employee> batchEmployees = new MultiSelectComboBox<>("Select Employees");
    private final ComboBox<Shift> batchShift = new ComboBox<>("Select Shift");
    private final DatePicker batchStartDate = new DatePicker("Start Date");
    private final ComboBox<String> batchDuration = new ComboBox<>("Duration");

    private final Dialog conflictDialog = new Dialog();
    private final Grid<ShiftConflictDTO> hardConflictGrid = new Grid<>(ShiftConflictDTO.class, false);
    private final Grid<ShiftConflictDTO> partialConflictGrid = new Grid<>(ShiftConflictDTO.class, false);

    private BatchPreviewResponse currentBatchPreview;

    public ShiftAssignmentView(ShiftAssignmentService assignmentService, EmployeeService employeeService, ShiftService shiftService) {
        this.assignmentService = assignmentService;
        this.employeeService = employeeService;
        this.shiftService = shiftService;


        setSizeFull();
        setPadding(false);

        buildDashboard();
        buildAssignmentDialog();
        buildBatchSetupDialog();

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
        assignBtn.getStyle().set("flex-shrink", "0");

        batchPlanBtn.addClickListener(e->batchSetupDialog.open());
        batchPlanBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        batchPlanBtn.getStyle().set("max-width", "100%");
        batchPlanBtn.getStyle().set("flex-shrink", "0");

        HorizontalLayout buttonWrapper = new HorizontalLayout(assignBtn, batchPlanBtn);
        buttonWrapper.setPadding(false);
        buttonWrapper.getStyle().set("flex-wrap", "wrap");

        HorizontalLayout header = new HorizontalLayout(new H2("Shift Operations"),buttonWrapper);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("padding", "1rem");
        header.getStyle().set("box-sizing", "border-box");

        add(header, dashboardLayout, tabs, contentArea);

        contentArea.add(buildListLayout());
        refreshListGrid();
    }

    private void buildDashboard() {
        dashboardLayout.setWidthFull();
        dashboardLayout.getStyle().set("padding", "1rem").set("background-color", "var(--lumo-contrast-5pct)");

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
        listGrid.addColumn(ShiftAssignmentDTO::getEmployeeName).setHeader("Employee");
        listGrid.addColumn(ShiftAssignmentDTO::getAssignmentDate).setHeader("Date");

        listGrid.addColumn(new ComponentRenderer<>(assignment -> {
            Span badge = new Span(assignment.getShiftName());
            String type = assignment.getShiftType().toLowerCase();

            badge.getElement().getThemeList().add("badge");

            if (type.contains("morning")) {
                badge.getElement().getThemeList().add("success");
            } else if (type.contains("night")) {
                badge.getElement().getThemeList().add("contrast");
            } else {
                badge.getElement().getThemeList().add("badge");
            }
            return badge;
        })).setHeader("Shift");

        listGrid.addComponentColumn(assignment -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openEditDialog(assignment));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> confirmDelete(assignment));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions");

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
                    assignmentService.validateShiftConflict(employeeCombo.getValue().getId(), event.getValue(),null);
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

                if(startDatePicker.getValue().isAfter(endDatePicker.getValue()) || startDatePicker.getValue().isEqual(endDatePicker.getValue())){
                    showNotification("Please select End Date greater than start date", NotificationVariant.LUMO_ERROR);
                    return;
                }

                currentBatchPreview = assignmentService.previewBatchAssignments(
                        List.of(employeeCombo.getValue().getId()),
                        shiftCombo.getValue().getId(),
                        startDatePicker.getValue(),
                        endDatePicker.getValue()
                );

                assignmentDialog.close();

                if (currentBatchPreview.getHardConflicts().isEmpty() && currentBatchPreview.getPartialConflicts().isEmpty()) {
                    executeFinalSave();
                } else {
                    buildAndOpenConflictDialog();
                }
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
    private void buildBatchSetupDialog() {
        batchSetupDialog.setHeaderTitle("Advanced Batch Planning");

        batchEmployees.setItems(employeeService.findAllActive());
        batchEmployees.setItemLabelGenerator(Employee::getFirstName);
        batchEmployees.setWidthFull();

        batchShift.setItems(shiftService.getShifts());
        batchShift.setItemLabelGenerator(Shift::getName);

        batchDuration.setItems("1 Week", "2 Weeks", "1 Month", "3 Months", "6 Months");

        FormLayout form = new FormLayout(batchEmployees, batchShift, batchStartDate, batchDuration);
        form.setColspan(batchEmployees, 2);

        Button checkBtn = new Button("Check Conflicts & Assign", e -> runValidationEngine());
        checkBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        batchSetupDialog.add(form);
        batchSetupDialog.getFooter().add(new Button("Cancel", e -> batchSetupDialog.close()), checkBtn);
    }

    private void runValidationEngine() {
        if (batchEmployees.getValue().isEmpty() || batchShift.getValue() == null ||
                batchStartDate.getValue() == null || batchDuration.getValue() == null) {
            showNotification("Please fill all fields", NotificationVariant.LUMO_ERROR);
            return;
        }

        // Extract IDs
        List<Long> empIds = batchEmployees.getValue().stream().map(Employee::getId).toList();

        // Call Service
        currentBatchPreview = assignmentService.previewBatchAssignments(
                empIds, batchShift.getValue().getId(), batchStartDate.getValue(), batchDuration.getValue()
        );

        batchSetupDialog.close();

        // If no conflicts, save immediately!
        if (currentBatchPreview.getHardConflicts().isEmpty() && currentBatchPreview.getPartialConflicts().isEmpty()) {
            executeFinalSave();
        } else {
            // Conflicts found, open resolution UI
            buildAndOpenConflictDialog();
        }
    }

    private void buildAndOpenConflictDialog() {
        conflictDialog.removeAll();
        conflictDialog.setHeaderTitle("Conflict Resolution Required");
        conflictDialog.setWidth("80vw");

        VerticalLayout layout = new VerticalLayout();

        // --- HARD CONFLICTS GRID (Holidays, Full Leaves, Overlaps) ---
        if (!currentBatchPreview.getHardConflicts().isEmpty()) {
            layout.add(new H4("Hard Conflicts (Will be Skipped)"));

            hardConflictGrid.removeAllColumns();
            hardConflictGrid.setItems(currentBatchPreview.getHardConflicts());
            hardConflictGrid.addColumn(ShiftConflictDTO::getEmployeeName).setHeader("Employee");
            hardConflictGrid.addColumn(ShiftConflictDTO::getConflictDate).setHeader("Date");
            hardConflictGrid.addColumn(ShiftConflictDTO::getConflictType).setHeader("Reason");

            // "Skip" Action just visually removes it from the grid
            hardConflictGrid.addComponentColumn(conflict -> {
                Button skipBtn = new Button("Skip", new Icon(VaadinIcon.CLOSE));
                skipBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
                skipBtn.addClickListener(e -> {
                    currentBatchPreview.getHardConflicts().remove(conflict);
                    hardConflictGrid.getDataProvider().refreshAll();
                });
                return skipBtn;
            }).setHeader("Action");

            layout.add(hardConflictGrid);
        }


        // --- PARTIAL CONFLICTS GRID (Half-Day Leaves) ---
        if (!currentBatchPreview.getPartialConflicts().isEmpty()) {
            layout.add(new H4("Partial Conflicts (Auto-Resolved)"));

            partialConflictGrid.removeAllColumns();
            partialConflictGrid.setItems(currentBatchPreview.getPartialConflicts());
            partialConflictGrid.addColumn(ShiftConflictDTO::getEmployeeName).setHeader("Employee");
            partialConflictGrid.addColumn(ShiftConflictDTO::getConflictDate).setHeader("Date");

            partialConflictGrid.addColumn(ShiftConflictDTO::getSystemResolution)
                    .setHeader("System Action")
                    .setAutoWidth(true);

            layout.add(partialConflictGrid);
        }

        Button proceedBtn = new Button("Proceed with Valid Assignments", e -> executeFinalSave());
        proceedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        conflictDialog.add(layout);
        conflictDialog.getFooter().add(new Button("Cancel", e -> conflictDialog.close()), proceedBtn);
        conflictDialog.open();
    }

    private void executeFinalSave() {
        List<ShiftAssignmentDTO> finalToSave = new ArrayList<>(currentBatchPreview.getReadyToSave());

        // Convert the adjusted Partial Conflicts into valid assignments
        for (ShiftConflictDTO partial : currentBatchPreview.getPartialConflicts()) {
            ShiftAssignmentDTO dto = new ShiftAssignmentDTO();
            dto.setEmployeeId(partial.getEmployeeId());
            dto.setShiftId(partial.getShiftId());
            dto.setAssignmentDate(partial.getConflictDate());

            dto.setOverrideStartTime(partial.getSuggestedOverrideStart());
            dto.setOverrideEndTime(partial.getSuggestedOverrideEnd());

            finalToSave.add(dto);
        }

        try {
            assignmentService.saveResolvedBatch(finalToSave);
            showNotification("Batch Scheduled Successfully", NotificationVariant.LUMO_SUCCESS);
            conflictDialog.close();
            refreshListGrid();
            refreshPivotGrid();
        } catch (Exception ex) {
            showNotification("Error saving batch: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void openEditDialog(ShiftAssignmentDTO assignment) {
        Dialog editDialog = new Dialog();
        editDialog.setHeaderTitle("Edit Shift for " + assignment.getEmployeeName());

        // Shift Selection
        ComboBox<Shift> shiftCombo = new ComboBox<>("Shift");
        shiftCombo.setItems(shiftService.getShifts());
        shiftCombo.setItemLabelGenerator(Shift::getName);
        // Pre-select current shift (Requires your shiftService to have a findById method)
        shiftService.getShiftById(assignment.getShiftId()).ifPresent(shiftCombo::setValue);

        // Date Selection
        DatePicker datePicker = new DatePicker("Assignment Date");
        datePicker.setValue(assignment.getAssignmentDate());

        // Overrides (Useful if they are resolving a half-day conflict)
        TimePicker overrideStart = new TimePicker("Override Start Time");
        overrideStart.setValue(assignment.getOverrideStartTime());
        overrideStart.setClearButtonVisible(true);

        TimePicker overrideEnd = new TimePicker("Override End Time");
        overrideEnd.setValue(assignment.getOverrideEndTime());
        overrideEnd.setClearButtonVisible(true);

        Button saveBtn = new Button("Save Changes", e -> {
            if (shiftCombo.getValue() == null || datePicker.getValue() == null) {
                Notification.show("Shift and Date are required.", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            // Update the DTO with new values
            assignment.setShiftId(shiftCombo.getValue().getId());
            assignment.setAssignmentDate(datePicker.getValue());
            assignment.setOverrideStartTime(overrideStart.getValue());
            assignment.setOverrideEndTime(overrideEnd.getValue());

            try {
                // Call your service layer.
                // IMPORTANT: This method must throw an Exception if there is a conflict!
                assignmentService.updateSingleAssignment(assignment);

                Notification.show("Assignment Updated", 3000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                editDialog.close();
                refreshListGrid();

            } catch (Exception ex) {
                // Conflict detected! The dialog stays open so the user can resolve it.
                Notification.show("Conflict: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> editDialog.close());

        FormLayout form = new FormLayout(shiftCombo, datePicker, overrideStart, overrideEnd);
        editDialog.add(form);
        editDialog.getFooter().add(cancelBtn, saveBtn);

        editDialog.open();
    }
    private void confirmDelete(ShiftAssignmentDTO assignment) {
        try {
            // Call your service layer to delete
            assignmentService.deleteAssignment(assignment.getId());

            Notification.show("Assignment Removed", 3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshListGrid();

        } catch (Exception ex) {
            Notification.show("Error deleting assignment: " + ex.getMessage(), 4000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}