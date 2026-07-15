package com.murali.views;

import com.murali.dto.*;
import com.murali.entity.Employee;
import com.murali.entity.Shift;
import com.murali.entity.enums.LeaveSession;
import com.murali.service.EmployeeService;
import com.murali.service.ShiftAssignmentService;
import com.murali.service.ShiftService;
import com.murali.views.components.GlobalSearchComponent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route(value = "shift-assignments", layout = MainLayout.class)
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

    // Track empty states and search globally for the view's tabs
    private final Span listEmptyMsg = new Span("No shift assignments found.");
    private final Span pivotEmptyMsg = new Span("No schedule data found for this week.");
    private final Span monthlyEmptyMsg = new Span("No schedule data found for this month.");
    private GlobalSearchComponent searchBox;
    private String currentSearch = "";

    private final Grid<RowDTO> pivotGrid = new Grid<>(RowDTO.class, false);
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

    private final Grid<MonthlyRowDTO> monthlyGrid = new Grid<>(MonthlyRowDTO.class, false);
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

    private BatchPreviewResponse currentBatchPreview;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public ShiftAssignmentView(ShiftAssignmentService assignmentService, EmployeeService employeeService, ShiftService shiftService) {
        this.assignmentService = assignmentService;
        this.employeeService = employeeService;
        this.shiftService = shiftService;

        setSizeFull();
        addClassName("standard-view-container");
        contentArea.setSizeFull();
        contentArea.getStyle().set("overflow", "hidden");

        buildDashboard();
        buildAssignmentDialog();
        buildBatchSetupDialog();

        Tab listTab = new Tab("List View");
        Tab calendarTab = new Tab("Weekly Calendar View");
        Tab monthlyTab = new Tab("Monthly Calendar View");

        tabs.add(monthlyTab, calendarTab, listTab);

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

        batchPlanBtn.addClickListener(e -> batchSetupDialog.open());
        batchPlanBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        batchPlanBtn.getStyle().set("max-width", "100%");
        batchPlanBtn.getStyle().set("flex-shrink", "0");

        HorizontalLayout buttonWrapper = new HorizontalLayout(assignBtn, batchPlanBtn);
        buttonWrapper.setPadding(false);
        buttonWrapper.getStyle().set("flex-wrap", "wrap");

        HorizontalLayout header = new HorizontalLayout(new H2("Shift Operations"), buttonWrapper);
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        add(header, dashboardLayout, tabs, contentArea);
        setFlexGrow(1, contentArea);

        tabs.setSelectedTab(monthlyTab);
        contentArea.removeAll();
        refreshMonthlyGrid();
        contentArea.add(buildMonthlyLayout());
    }

    private void buildDashboard() {
        dashboardLayout.setWidthFull();
        dashboardLayout.getStyle().set("gap", "var(--app-padding)");
        dashboardLayout.getStyle().set("flex-wrap", "wrap");

        Map<String, Long> stats = assignmentService.getTodayShiftCounts(LocalDate.now());
        stats.forEach((shiftName, count) -> {
            // Converted to HorizontalLayout for a sleeker profile
            HorizontalLayout card = new HorizontalLayout();
            card.addClassNames("standard-surface", "hoverable");
            card.setAlignItems(FlexComponent.Alignment.CENTER);
            card.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

            // Added subtle border, rounded corners, and stopped stretching
            card.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)").set("border-radius", "var(--lumo-border-radius-m)").set("padding", "var(--lumo-space-s) var(--lumo-space-m)").set("flex", "0 1 auto");

            Span title = new Span(shiftName);
            title.addClassNames(com.vaadin.flow.theme.lumo.LumoUtility.FontSize.SMALL, com.vaadin.flow.theme.lumo.LumoUtility.TextColor.SECONDARY, com.vaadin.flow.theme.lumo.LumoUtility.FontWeight.BOLD);

            H4 value = new H4(count + " Emp");
            value.getStyle().set("margin", "0");

            card.add(title, value);
            dashboardLayout.add(card);
        });
    }

    private Component buildListLayout() {
        filterDate.setClearButtonVisible(true);
        filterDate.addValueChangeListener(e -> refreshListGrid());

        listEmptyMsg.addClassName("empty-grid-message");

        searchBox = new GlobalSearchComponent(term -> {
            currentSearch = term;
            refreshListGrid();
        });

        HorizontalLayout toolbar = new HorizontalLayout(filterDate, searchBox);
        toolbar.setWidthFull();
        toolbar.expand(searchBox);

        listGrid.setSizeFull();
        listGrid.addClassName("standard-surface");
        listGrid.addItemDoubleClickListener(e -> openListEditDialog(e.getItem()));
        listGrid.removeAllColumns();

        listGrid.addColumn(ShiftAssignmentDTO::getEmployeeName).setHeader("Employee").setTooltipGenerator(ShiftAssignmentDTO::getEmployeeName);
        listGrid.addColumn(dto -> {
            if (dto.getStartDate().equals(dto.getEndDate())) {
                return dto.getStartDate().toString();
            }
            return dto.getStartDate() + " to " + dto.getEndDate();
        }).setHeader("Dates").setTooltipGenerator(dto -> {
            if (dto.getStartDate().equals(dto.getEndDate())) {
                return dto.getStartDate().toString();
            }
            return dto.getStartDate() + " to " + dto.getEndDate();
        });

        listGrid.addColumn(new ComponentRenderer<>(assignment -> {
            Span badge = new Span(assignment.getShiftName());
            String type = assignment.getShiftType().name();

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

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

            editBtn.addClickListener(e -> openListEditDialog(assignment));

            if (!assignment.getStartDate().isAfter(LocalDate.now())) {
                deleteBtn.setEnabled(false);
                deleteBtn.setTooltipText("Locked: Past or active shifts cannot be deleted.");
            } else {
                deleteBtn.addClickListener(e -> openPartialDeleteDialog(assignment, assignment.getStartDate(), assignment.getEndDate()));
            }

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions");

        DataProvider<ShiftAssignmentDTO, Void> dataProvider = DataProvider.fromCallbacks(
                query -> assignmentService.fetchAssignmentsForGrid(query.getOffset(), query.getLimit(), filterDate.getValue(), currentSearch).stream(),
                query -> (int) assignmentService.fetchAssignmentsForGrid(0, Integer.MAX_VALUE, filterDate.getValue(), currentSearch).getTotalElements()
        );
        listGrid.setDataProvider(dataProvider);

        VerticalLayout layout = new VerticalLayout(toolbar, listGrid, listEmptyMsg);
        layout.setSizeFull();
        layout.setPadding(false);
        return layout;
    }

    private void refreshListGrid() {
        listGrid.getDataProvider().refreshAll();
        long totalElements = assignmentService.fetchAssignmentsForGrid(0, 1, filterDate.getValue(), currentSearch).getTotalElements();
        boolean isEmpty = (totalElements == 0);

        listGrid.setVisible(!isEmpty);
        listEmptyMsg.setVisible(isEmpty);

        if (searchBox != null) {
            searchBox.hideSpinner();
        }
    }

    private Component buildPivotLayout() {
        weekSelector.setValue(LocalDate.now());
        weekSelector.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                refreshPivotGrid();
            }
        });
        pivotGrid.setSizeFull();
        pivotGrid.addClassName("standard-surface");
        setupPivotColumns(LocalDate.now());

        pivotEmptyMsg.addClassName("empty-grid-message");
        VerticalLayout layout = new VerticalLayout(weekSelector, pivotGrid, pivotEmptyMsg);
        layout.setSizeFull();
        layout.setPadding(false);
        return layout;
    }

    private void setupPivotColumns(LocalDate selectedDate) {
        pivotGrid.removeAllColumns();
        pivotGrid.addColumn(RowDTO::getEmployeeName).setHeader("Employee").setFrozen(true).setTooltipGenerator(RowDTO::getEmployeeName);

        LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = startOfWeek.plusDays(i);
            String dayName = currentDate.getDayOfWeek().toString().substring(0, 3) + " (" + currentDate.getDayOfMonth() + ")";

            pivotGrid.addColumn(new ComponentRenderer<>(row -> {
                DailyCellDTO cell = row.getCellForDate(currentDate);
                Button cellBtn = new Button();

                // Set base styles and make it a Tertiary button
                cellBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                cellBtn.getStyle().set("padding", "0").set("margin", "0").set("width", "100%");

                // 1. EMPTY CELL
                if (cell == null) {
                    cellBtn.setText("-");
                    cellBtn.setTooltipText("No Schedule Data");
                    cellBtn.getStyle().set("color", "var(--app-text-secondary)");
                    cellBtn.setEnabled(false);
                    return cellBtn;
                }

                // 2. FULL DAY LEAVE
                if (cell.isOnLeave() && cell.getLeaveSession() == LeaveSession.FULL_DAY) {
                    cellBtn.setText("Leave");
                    cellBtn.getStyle().set("color", "var(--app-error-color)");
                    cellBtn.addClickListener(e -> showNotification("Employee on Full Day Leave", NotificationVariant.LUMO_WARNING));
                    return cellBtn;
                }

                // 3. SHIFT ASSIGNMENT
                if (cell.getAssignment() != null) {
                    String shortCode;
                    if (Boolean.TRUE.equals(cell.getAssignment().getIsRotational())) {
                        String seg = cell.getAssignment().getSegmentName();
                        shortCode = (seg != null && seg.length() > 7) ? seg.substring(0, 7) : (seg != null ? seg : "ROT");
                    } else {
                        String shiftName = cell.getAssignment().getShiftName();
                        shortCode = shiftName.length() > 7 ? shiftName.substring(0, 7) : shiftName;
                    }

                    if (cell.isOnLeave()) {
                        String sessionStr = cell.getLeaveSession() == LeaveSession.FIRST_HALF ? "L(1st)" : "L(2nd)";
                        cellBtn.setText(sessionStr + " / " + shortCode);
                        cellBtn.getStyle().set("color", "var(--app-warning-color)");
                    } else {
                        cellBtn.setText(shortCode);
                        cellBtn.getStyle().set("color", "var(--app-success-color)");
                    }

                    if (!cell.getDate().isAfter(LocalDate.now())) {
                        cellBtn.addClickListener(e -> showNotification("Locked: Cannot edit past or current day shifts.", NotificationVariant.LUMO_ERROR));
                    } else {
                        cellBtn.addClickListener(e -> openEditDialog(cell.getAssignment(), cell.getDate()));
                    }
                    return cellBtn;
                }

                // 4. HOLIDAY
                if (cell.isHoliday()) {
                    cellBtn.setText("H");
                    cellBtn.getStyle().set("color", "var(--app-error-color)");
                    cellBtn.addClickListener(e -> handleEmptyCellClick(cell));
                    return cellBtn;
                }

                // 5. OFF DAY (The invisible text fix)
                if (cell.isIntentionalOffDay()) {
                    cellBtn.setText("Off");
                    // We remove LUMO_CONTRAST and just force a grey text color
                    cellBtn.getStyle().set("color", "var(--app-text-secondary)");
                    cellBtn.addClickListener(e -> handleEmptyCellClick(cell));
                    return cellBtn;
                }

                // 6. DEFAULT UNASSIGNED
                cellBtn.setIcon(new Icon(VaadinIcon.PLUS));
                cellBtn.getStyle().set("color", "var(--app-primary-color)");
                if (!cell.getDate().isAfter(LocalDate.now())) {
                    cellBtn.setEnabled(false);
                    cellBtn.setTooltipText("Locked: Cannot assign shifts to past or current dates.");
                } else {
                    cellBtn.setTooltipText("Assign Shift");
                    cellBtn.addClickListener(e -> handleEmptyCellClick(cell));
                }

                return cellBtn;
            })).setHeader(dayName).setAutoWidth(true);
        }
    }

    private void refreshPivotGrid() {
        LocalDate selectedDate = weekSelector.getValue() != null ? weekSelector.getValue() : LocalDate.now();
        LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        setupPivotColumns(startOfWeek);
        List<DailyCellDTO> resolvedCells = assignmentService.getResolvedCalendarData(startOfWeek, endOfWeek);
        Map<String, RowDTO> pivotData = new HashMap<>();

        for (DailyCellDTO cell : resolvedCells) {
            RowDTO row = pivotData.computeIfAbsent(cell.getEmployeeName(), k -> new RowDTO(cell.getEmployeeName()));
            row.addCell(cell.getDate(), cell);
        }

        pivotGrid.setItems(pivotData.values());

        boolean isEmpty = pivotData.isEmpty();
        pivotGrid.setVisible(!isEmpty);
        pivotEmptyMsg.setVisible(isEmpty);
    }

    private void buildAssignmentDialog() {
        assignmentDialog.setHeaderTitle("Assign Shifts");
        dialogTabs.add(singleTab, bulkTab);
        LocalDate minDate = LocalDate.now().plusDays(1);
        singleDatePicker.setMin(minDate);
        startDatePicker.setMin(minDate);
        endDatePicker.setMin(minDate);
        assignmentDialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                employeeCombo.clear();
                shiftCombo.clear();
                singleDatePicker.clear();
                startDatePicker.clear();
                endDatePicker.clear();
                employeeCombo.setReadOnly(false);
                singleDatePicker.setReadOnly(false);
                shiftCombo.setItems(shiftService.getStandardShifts());
                dialogTabs.setVisible(true);
            }
        });

        employeeCombo.setItems(employeeService.findAllActive());
        employeeCombo.setItemLabelGenerator(Employee::getFirstName);

        shiftCombo.setItems(shiftService.getStandardShifts());
        shiftCombo.setItemLabelGenerator(shift -> shift.getName() + " (" + shift.getStartTime().format(TIME_FORMATTER) + " - " + shift.getEndTime().format(TIME_FORMATTER) + ")");

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

        FormLayout commonFields = new FormLayout(employeeCombo, shiftCombo);

        Button saveBtn = new Button("Assign", e -> saveAssignment());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelBtn = new Button("Cancel", e -> assignmentDialog.close());

        assignmentDialog.add(dialogTabs, commonFields, dialogContentArea);
        assignmentDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void saveAssignment() {
        if (employeeCombo.getValue() == null || shiftCombo.getValue() == null) {
            showNotification("Please select Employee and Shift", NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            LocalDate startDate;
            LocalDate endDate;
            LocalDate today = LocalDate.now();

            if (dialogTabs.getSelectedTab().equals(singleTab)) {
                if (singleDatePicker.getValue() == null) {
                    showNotification("Please select a Date", NotificationVariant.LUMO_ERROR);
                    return;
                }
                startDate = singleDatePicker.getValue();
                endDate = singleDatePicker.getValue();
            } else {
                if (startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
                    showNotification("Please select Start and End Dates", NotificationVariant.LUMO_ERROR);
                    return;
                }
                if (startDatePicker.getValue().isAfter(endDatePicker.getValue())) {
                    showNotification("End Date must be greater than or equal to Start Date", NotificationVariant.LUMO_ERROR);
                    return;
                }
                startDate = startDatePicker.getValue();
                endDate = endDatePicker.getValue();
            }
            if (!startDate.isAfter(today)) {
                showNotification("Cannot assign shifts to past dates or the current day.", NotificationVariant.LUMO_ERROR);
                return;
            }
            List<Long> employeeIds = new ArrayList<>();
            employeeIds.add(employeeCombo.getValue().getId());

            currentBatchPreview = assignmentService.previewBatchAssignments(employeeIds, shiftCombo.getValue().getId(), startDate, endDate);

            assignmentDialog.close();

            if (currentBatchPreview.getHardConflicts().isEmpty()) {
                executeFinalSave();
            } else {
                buildAndOpenConflictDialog();
            }

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
        monthlyGrid.addClassName("standard-surface");
        monthlyGrid.addClassName("monthly-calendar-grid");
        monthlyGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_COLUMN_BORDERS);

        monthlyEmptyMsg.addClassName("empty-grid-message");
        VerticalLayout layout = new VerticalLayout(controls, monthlyGrid, monthlyEmptyMsg);
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setFlexGrow(1, monthlyGrid);
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

        // Truncate Employee Name and reduce width to 120px
        monthlyGrid.addColumn(new ComponentRenderer<>(row -> {
            Span nameSpan = new Span(row.getEmployeeName());
            nameSpan.getStyle().set("white-space", "nowrap").set("overflow", "hidden").set("text-overflow", "ellipsis").set("display", "block").set("width", "100%");
            nameSpan.getElement().setProperty("title", row.getEmployeeName());
            return nameSpan;
        })).setHeader("Employee").setFrozen(true).setWidth("120px").setFlexGrow(1);

        int daysInMonth = currentMonth.lengthOfMonth();
        for (int i = 1; i <= daysInMonth; i++) {
            final int day = i;

            monthlyGrid.addColumn(new ComponentRenderer<>(row -> {
                DailyCellDTO cell = row.getCellForDay(day);
                Button cellBtn = new Button();

                // 1. UNIFORM STRUCTURE FOR EVERY BUTTON
                cellBtn.getStyle().set("padding", "0").set("margin", "0").set("min-width", "32px").set("height", "32px").set("border-radius", "6px").set("font-weight", "bold").set("font-size", "0.75rem");

                // Base Tertiary setup
                cellBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

                // 2. EMPTY CELL (No Data)
                if (cell == null) {
                    cellBtn.setText("-");
                    cellBtn.setTooltipText("No Schedule Data");
                    cellBtn.getStyle().set("color", "var(--lumo-contrast-30pct)");
                    cellBtn.setEnabled(false);
                    return cellBtn;
                }

                // 3. FULL DAY LEAVE
                if (cell.isOnLeave() && cell.getLeaveSession() == LeaveSession.FULL_DAY) {
                    cellBtn.setText("L");
                    cellBtn.setTooltipText("Full Day Leave");
                    cellBtn.getStyle().set("color", "var(--lumo-error-text-color)");
                    cellBtn.addClickListener(e -> showNotification("Employee on Full Day Leave", NotificationVariant.LUMO_WARNING));
                    return cellBtn;
                }

                // 4. HOLIDAY
                if (cell.isHoliday()) {
                    cellBtn.setText("H");
                    cellBtn.setTooltipText("Holiday");
                    cellBtn.getStyle().set("color", "var(--lumo-error-text-color)");
                    cellBtn.addClickListener(e -> handleEmptyCellClick(cell));
                    return cellBtn;
                }

                // 5. OFF DAY
                if (cell.isIntentionalOffDay()) {
                    cellBtn.setText("O");
                    cellBtn.setTooltipText("Off Day");
                    cellBtn.getStyle().set("color", "var(--lumo-contrast-50pct)");
                    cellBtn.addClickListener(e -> handleEmptyCellClick(cell));
                    return cellBtn;
                }

                // 6. SHIFT ASSIGNMENT
                if (cell.getAssignment() != null) {
                    String shiftTypeStr = cell.getAssignment().getShiftType() != null ? cell.getAssignment().getShiftType().name().toLowerCase() : "";

                    String shiftName;
                    String code;

                    if (Boolean.TRUE.equals(cell.getAssignment().getIsRotational())) {
                        shiftName = cell.getAssignment().getSegmentName();
                        code = (shiftName != null && !shiftName.isBlank()) ? shiftName.substring(0, 1).toUpperCase() : "R";
                    } else {
                        shiftName = cell.getAssignment().getShiftName();
                        code = (shiftName != null && !shiftName.isBlank()) ? shiftName.substring(0, 1).toUpperCase() : "S";
                    }

                    if (cell.isOnLeave()) {
                        cellBtn.setText("L");
                        cellBtn.setTooltipText("Half Leave / Shift: " + shiftName);
                        cellBtn.getStyle().set("color", "var(--lumo-warning-text-color)");
                    } else {
                        cellBtn.setText(code);
                        cellBtn.setTooltipText(shiftName);

                        // Color handling specifically for Tertiary (modifying text, not background)
                        if (shiftTypeStr.contains("morning")) {
                            cellBtn.getStyle().set("color", "var(--lumo-success-text-color)");
                        } else if (shiftTypeStr.contains("night")) {
                            cellBtn.getStyle().set("color", "var(--lumo-primary-text-color)");
                        } else {
                            cellBtn.getStyle().set("color", "var(--lumo-body-text-color)");
                        }
                    }

                    if (!cell.getDate().isAfter(LocalDate.now())) {
                        cellBtn.addClickListener(e -> showNotification("Locked: Cannot edit past shifts.", NotificationVariant.LUMO_ERROR));
                    } else {
                        cellBtn.addClickListener(e -> openEditDialog(cell.getAssignment(), cell.getDate()));
                    }
                    return cellBtn;
                }

                // 7. DEFAULT UNASSIGNED (Future assignable)
                cellBtn.setText("+");
                cellBtn.getStyle().set("color", "var(--lumo-primary-color)");

                if (!cell.getDate().isAfter(LocalDate.now())) {
                    cellBtn.setEnabled(false);
                    cellBtn.setTooltipText("Locked: Cannot assign shifts to past or current dates.");
                } else {
                    cellBtn.setTooltipText("Assign Shift");
                    cellBtn.addClickListener(e -> handleEmptyCellClick(cell));
                }
                return cellBtn;
            })).setHeader(String.valueOf(day)).setWidth("46px").setFlexGrow(1).setResizable(false).setTextAlign(ColumnTextAlign.CENTER);
        }
    }

    private void refreshMonthlyGrid() {
        setupMonthlyColumns();
        LocalDate startOfMonth = currentMonth.withDayOfMonth(1);
        LocalDate endOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth());
        List<DailyCellDTO> resolvedCells = assignmentService.getResolvedCalendarData(startOfMonth, endOfMonth);
        Map<String, MonthlyRowDTO> pivotData = new HashMap<>();

        for (DailyCellDTO cell : resolvedCells) {
            MonthlyRowDTO row = pivotData.computeIfAbsent(cell.getEmployeeName(), k -> new MonthlyRowDTO(cell.getEmployeeName()));

            if (cell.getDate().getMonthValue() == currentMonth.getMonthValue() && cell.getDate().getYear() == currentMonth.getYear()) {
                row.addCell(cell.getDate().getDayOfMonth(), cell);
            }
        }
        monthlyGrid.setItems(pivotData.values());

        boolean isEmpty = pivotData.isEmpty();
        monthlyGrid.setVisible(!isEmpty);
        monthlyEmptyMsg.setVisible(isEmpty);
    }

    private void buildBatchSetupDialog() {
        batchSetupDialog.setHeaderTitle("Advanced Batch Planning");
        batchStartDate.setMin(LocalDate.now());
        batchSetupDialog.addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                batchEmployees.clear();
                batchShift.clear();
                batchStartDate.clear();
                batchDuration.clear();
            }
        });

        batchEmployees.setItems(employeeService.findAllActive());
        batchEmployees.setItemLabelGenerator(Employee::getFirstName);
        batchEmployees.setWidthFull();

        batchShift.setItems(shiftService.getShifts());
        batchShift.setItemLabelGenerator(shift -> shift.getName() + " (" + shift.getStartTime().format(TIME_FORMATTER) + " - " + shift.getEndTime().format(TIME_FORMATTER) + ")");

        batchDuration.setItems("1 Week", "2 Weeks", "1 Month", "3 Months", "6 Months");

        FormLayout form = new FormLayout(batchEmployees, batchShift, batchStartDate, batchDuration);
        form.setColspan(batchEmployees, 2);

        Button checkBtn = new Button("Check Conflicts & Assign", e -> runValidationEngine());
        checkBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        batchSetupDialog.add(form);
        batchSetupDialog.getFooter().add(new Button("Cancel", e -> batchSetupDialog.close()), checkBtn);
    }

    private void runValidationEngine() {
        if (batchEmployees.getValue().isEmpty() || batchShift.getValue() == null || batchStartDate.getValue() == null || batchDuration.getValue() == null) {
            showNotification("Please fill all fields", NotificationVariant.LUMO_ERROR);
            return;
        }

        List<Long> empIds = batchEmployees.getValue().stream().map(Employee::getId).toList();

        LocalDate start = batchStartDate.getValue();
        LocalDate end = start;
        switch (batchDuration.getValue()) {
            case "1 Week" -> end = start.plusWeeks(1).minusDays(1);
            case "2 Weeks" -> end = start.plusWeeks(2).minusDays(1);
            case "1 Month" -> end = start.plusMonths(1).minusDays(1);
            case "3 Months" -> end = start.plusMonths(3).minusDays(1);
            case "6 Months" -> end = start.plusMonths(6).minusDays(1);
        }

        currentBatchPreview = assignmentService.previewBatchAssignments(empIds, batchShift.getValue().getId(), start, end);

        batchSetupDialog.close();

        if (currentBatchPreview.getHardConflicts().isEmpty()) {
            executeFinalSave();
        } else {
            buildAndOpenConflictDialog();
        }
    }

    private void buildAndOpenConflictDialog() {
        conflictDialog.removeAll();
        conflictDialog.setHeaderTitle("Conflict Resolution Required");
        conflictDialog.setWidth("80vw");

        VerticalLayout layout = new VerticalLayout();

        if (!currentBatchPreview.getHardConflicts().isEmpty()) {
            layout.add(new H4("Hard Conflicts (Will be Skipped)"));

            hardConflictGrid.removeAllColumns();
            hardConflictGrid.addClassName("standard-surface");
            hardConflictGrid.setItems(currentBatchPreview.getHardConflicts());
            hardConflictGrid.addColumn(ShiftConflictDTO::getEmployeeName).setHeader("Employee");
            hardConflictGrid.addColumn(ShiftConflictDTO::getConflictDate).setHeader("Date");
            hardConflictGrid.addColumn(ShiftConflictDTO::getConflictType).setHeader("Reason");

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

        Button proceedBtn = new Button("Proceed with Valid Assignments", e -> executeFinalSave());
        proceedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        conflictDialog.getFooter().removeAll();
        conflictDialog.add(layout);
        conflictDialog.getFooter().add(new Button("Cancel", e -> conflictDialog.close()), proceedBtn);
        conflictDialog.open();
    }

    private void executeFinalSave() {
        List<ShiftAssignmentDTO> finalToSave = new ArrayList<>(currentBatchPreview.getReadyToSave());

        if (finalToSave.isEmpty()) {
            showNotification("No valid assignments available. All selected dates are conflicts/holidays.", NotificationVariant.LUMO_ERROR);
            conflictDialog.close();
            return;
        }

        try {
            assignmentService.saveResolvedBatch(finalToSave, true);
            showNotification("Batch Scheduled Successfully", NotificationVariant.LUMO_SUCCESS);
            conflictDialog.close();
            refreshListGrid();
            refreshPivotGrid();
            refreshMonthlyGrid();

        } catch (Exception ex) {
            showNotification("Error saving batch: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void openEditDialog(ShiftAssignmentDTO assignment, LocalDate clickedDate) {
        Dialog editDialog = new Dialog();
        editDialog.setHeaderTitle("Edit Shift for " + assignment.getEmployeeName());

        ComboBox<Shift> shiftCombo = new ComboBox<>("Shift");
        shiftCombo.setItems(shiftService.getStandardShifts());
        shiftCombo.setItemLabelGenerator(shift -> shift.getName() + " (" + shift.getStartTime().format(TIME_FORMATTER) + " - " + shift.getEndTime().format(TIME_FORMATTER) + ")");
        shiftService.getShiftById(assignment.getShiftId()).ifPresent(shiftCombo::setValue);

        DatePicker datePicker = new DatePicker("Assignment Date");
        datePicker.setValue(clickedDate);
        datePicker.setMin(LocalDate.now().plusDays(1));

        final LocalDate originalDate = clickedDate;

        Button saveBtn = new Button("Save Changes", e -> {
            if (shiftCombo.getValue() == null || datePicker.getValue() == null) {
                showNotification("Shift and Date are required.", NotificationVariant.LUMO_ERROR);
                return;
            }

            LocalDate newDate = datePicker.getValue();
            Long shiftId = shiftCombo.getValue().getId();

            try {
                assignmentService.editOrMoveShiftSegment(assignment.getEmployeeId(), originalDate, newDate, shiftId);

                showNotification("Assignment Updated successfully.", NotificationVariant.LUMO_SUCCESS);
                editDialog.close();
                refreshListGrid();
                refreshPivotGrid();
                refreshMonthlyGrid();

            } catch (Exception ex) {
                showNotification("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", e -> editDialog.close());

        Button deleteBtn = new Button("Delete", new Icon(VaadinIcon.TRASH));
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        if (!originalDate.isAfter(LocalDate.now())) {
            deleteBtn.setEnabled(false);
            deleteBtn.setTooltipText("Locked: Past or active shifts cannot be deleted.");
        } else {
            deleteBtn.addClickListener(e -> {
                openPartialDeleteDialog(assignment, originalDate, originalDate);
                editDialog.close();
            });
        }

        FormLayout form = new FormLayout(shiftCombo, datePicker);
        editDialog.add(form);
        HorizontalLayout footerLayout = new HorizontalLayout(deleteBtn, cancelBtn, saveBtn);
        footerLayout.setWidthFull();
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        editDialog.getFooter().add(footerLayout);
        editDialog.open();
    }

    private void openPartialDeleteDialog(ShiftAssignmentDTO assignment, LocalDate defaultStart, LocalDate defaultEnd) {
        if (assignment.getEndDate().isBefore(LocalDate.now())) {
            showNotification("Cannot modify shifts entirely in the past.", NotificationVariant.LUMO_ERROR);
            return;
        }
        Dialog deleteDialog = new Dialog();
        deleteDialog.setHeaderTitle("Remove Shift Segment");

        Text infoText = new Text(String.format("Original Range: %s to %s", assignment.getStartDate(), assignment.getEndDate()));

        LocalDate today = LocalDate.now();
        LocalDate minSelectableDate = assignment.getStartDate().isBefore(today) ? today : assignment.getStartDate();

        DatePicker startPicker = new DatePicker("Start Date to Remove");
        startPicker.setMin(minSelectableDate);
        startPicker.setMax(assignment.getEndDate());
        startPicker.setValue(defaultStart != null && !defaultStart.isBefore(minSelectableDate) ? defaultStart : minSelectableDate);

        DatePicker endPicker = new DatePicker("End Date to Remove");
        endPicker.setValue(defaultEnd != null && !defaultEnd.isBefore(minSelectableDate) ? defaultEnd : assignment.getEndDate());
        endPicker.setMin(assignment.getStartDate());
        endPicker.setMax(assignment.getEndDate());

        Button cancelBtn = new Button("Cancel", e -> deleteDialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button deleteConfirmBtn = new Button("Remove Segment", e -> {
            LocalDate delStart = startPicker.getValue();
            LocalDate delEnd = endPicker.getValue();

            if (delStart == null || delEnd == null || delStart.isAfter(delEnd)) {
                showNotification("Please select a valid date range.", NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                if (delStart.equals(assignment.getStartDate()) && delEnd.equals(assignment.getEndDate())) {
                    assignmentService.deleteAssignment(assignment.getId());
                } else {
                    assignmentService.deleteAssignmentRange(assignment.getEmployeeId(), delStart, delEnd);
                }

                showNotification("Shift segment removed successfully.", NotificationVariant.LUMO_SUCCESS);
                refreshListGrid();
                refreshMonthlyGrid();
                refreshPivotGrid();
                deleteDialog.close();

            } catch (Exception ex) {
                showNotification("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        deleteConfirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        FormLayout form = new FormLayout(startPicker, endPicker);
        VerticalLayout layout = new VerticalLayout(infoText, form);
        layout.setPadding(false);

        deleteDialog.add(layout);
        deleteDialog.getFooter().add(cancelBtn, deleteConfirmBtn);
        deleteDialog.open();
    }

    private void handleEmptyCellClick(DailyCellDTO cell) {
        employeeService.findById(cell.getEmployeeId()).ifPresent(employeeCombo::setValue);
        employeeCombo.setReadOnly(true);

        dialogTabs.setSelectedTab(singleTab);
        dialogTabs.setVisible(false);
        singleDatePicker.setValue(cell.getDate());
        singleDatePicker.setReadOnly(true);

        String dayName = cell.getDate().getDayOfWeek().name();
        List<Shift> validShifts = shiftService.getStandardShifts().stream().filter(s -> s.getWorkingDays().stream().anyMatch(wd -> wd.name().equalsIgnoreCase(dayName))).toList();
        shiftCombo.setItems(validShifts);

        assignmentDialog.open();
    }

    private void openListEditDialog(ShiftAssignmentDTO assignment) {
        Dialog editDialog = new Dialog();
        editDialog.setHeaderTitle("Edit Assignment Block for " + assignment.getEmployeeName());

        ComboBox<Shift> shiftCombo = new ComboBox<>("Shift");
        shiftCombo.setItems(shiftService.getStandardShifts());
        shiftCombo.setItemLabelGenerator(shift -> shift.getName() + " (" + shift.getStartTime().format(TIME_FORMATTER) + " - " + shift.getEndTime().format(TIME_FORMATTER) + ")");
        shiftService.getShiftById(assignment.getShiftId()).ifPresent(shiftCombo::setValue);

        LocalDate today = LocalDate.now();
        LocalDate originalStart = assignment.getStartDate();
        LocalDate originalEnd = assignment.getEndDate();

        LocalDate minSelectable = originalStart.isAfter(today) ? originalStart : today.plusDays(1);

        DatePicker startPicker = new DatePicker("Effective Start Date");
        startPicker.setMin(minSelectable);

        if (!originalStart.isAfter(today)) {
            startPicker.setValue(minSelectable);
            startPicker.setHelperText("Original start date was in the past. Edits will apply from tomorrow onwards, preserving past attendance.");
        } else {
            startPicker.setValue(originalStart);
        }

        DatePicker endPicker = new DatePicker("End Date");
        LocalDate defaultEnd = originalEnd.isBefore(startPicker.getValue()) ? startPicker.getValue() : originalEnd;
        endPicker.setValue(defaultEnd);
        endPicker.setMin(startPicker.getValue());
        startPicker.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                endPicker.setMin(e.getValue());
                if (endPicker.getValue() != null && endPicker.getValue().isBefore(e.getValue())) {
                    endPicker.setValue(e.getValue());
                }
            }
        });

        Button saveBtn = new Button("Save Changes", e -> {
            if (shiftCombo.getValue() == null || startPicker.getValue() == null || endPicker.getValue() == null) {
                showNotification("All fields are required.", NotificationVariant.LUMO_ERROR);
                return;
            }

            if (startPicker.getValue().isAfter(endPicker.getValue())) {
                showNotification("Start Date cannot be after End Date.", NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                assignment.setShiftId(shiftCombo.getValue().getId());
                assignment.setStartDate(startPicker.getValue());
                assignment.setEndDate(endPicker.getValue());

                assignmentService.updateSingleAssignment(assignment);

                showNotification("Assignment block updated successfully.", NotificationVariant.LUMO_SUCCESS);
                editDialog.close();
                refreshListGrid();
                refreshPivotGrid();
                refreshMonthlyGrid();

            } catch (Exception ex) {
                showNotification("Error: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button cancelBtn = new Button("Cancel", e -> editDialog.close());

        FormLayout form = new FormLayout(shiftCombo, startPicker, endPicker);
        editDialog.add(form);

        HorizontalLayout footerLayout = new HorizontalLayout(cancelBtn, saveBtn);
        footerLayout.setWidthFull();
        footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        editDialog.getFooter().add(footerLayout);
        editDialog.open();
    }
}