package com.murali.views;
import com.murali.entity.RotationSequence;
import com.murali.entity.Shift;
import com.murali.entity.enums.RotationSegmentType;
import com.murali.entity.enums.Shifts;
import com.murali.entity.enums.WorkingDay;
import com.murali.service.ShiftService;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "add-shifts", layout = MainLayout.class)
@PageTitle("Manage Shifts")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class ShiftView extends VerticalLayout {

    private final ShiftService shiftService;

    private final Grid<Shift> grid = new Grid<>(Shift.class, false);
    private final TextField searchField = new TextField();
    private final Button addBtn = new Button("Add New Shift", new Icon(VaadinIcon.PLUS));

    private final Dialog formDialog = new Dialog();
    private final TextField nameField = new TextField("Shift Name");
    private final ComboBox<Shifts> shiftTypeField = new ComboBox<>("Shift Type");
    private final MultiSelectComboBox<WorkingDay> workingDaysField = new MultiSelectComboBox<>("Working Days");

    private final Checkbox isRotationalShiftField = new Checkbox("Is Custom Rotational Shift?");
    private final VerticalLayout rotationSequenceContainer = new VerticalLayout();

    private final TimePicker startTimeField = new TimePicker("Shift Start Time");
    private final TimePicker endTimeField = new TimePicker("Shift End Time");

    // New Fields
    private final TimePicker firstHalfEndTimeField = new TimePicker("1st Half Ends At");
    private final TimePicker secondHalfStartTimeField = new TimePicker("2nd Half Starts At");
    private final IntegerField gracePeriodField = new IntegerField("Required Work Time (Minutes)");

    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    // FIXED: Using standard Binder instead of BeanValidationBinder
    private final Binder<Shift> binder = new Binder<>(Shift.class);
    private Shift currentShift;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public ShiftView(ShiftService shiftService) {
        this.shiftService = shiftService;

        setSizeFull();
        configureGrid();
        configureForm();

        searchField.setPlaceholder("Search shifts");
        searchField.setTooltipText("Search Using Shift Name");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());

        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new Shift()));

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addBtn);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, searchField);

        add(new H2("Shift Configuration"), toolbar, grid);

        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(Shift::getName).setHeader("Name").setSortable(true).setAutoWidth(true);
        grid.addColumn(Shift::getShiftType).setHeader("Type").setSortable(true).setAutoWidth(true);

        grid.addColumn(shift -> Boolean.TRUE.equals(shift.getIsRotationalShift()) ? "Variable (Rotational)" :
                        shift.getStartTime().format(TIME_FORMATTER) + " - " + shift.getEndTime().format(TIME_FORMATTER))
                .setHeader("Timings").setAutoWidth(true);

        grid.addColumn(shift -> Boolean.TRUE.equals(shift.getIsRotationalShift()) ? "Variable" :
                        (shift.getRequiredWorkTime() != null ? shift.getRequiredWorkTime()+ " mins" : "None"))
                .setHeader("Required Work Time").setAutoWidth(true);

        grid.addColumn(shift -> Boolean.TRUE.equals(shift.getIsRotationalShift()) ? List.of("Variable") :
                        shift.getWorkingDays().stream().map(day -> day.name().substring(0,3)).toList())
                .setHeader("Working Days").setAutoWidth(true);

        grid.addComponentColumn(shift -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> {
                Shift freshShift = shiftService.getShiftWithSequences(shift.getId());
                openForm(freshShift);
            });

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> confirmAndDelete(shift));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Shift Details");
        formDialog.setWidth("600px"); // Wider to accommodate the side-by-side time fields

        // UI Setup
        shiftTypeField.setItems(Shifts.values());
        shiftTypeField.setAllowCustomValue(false);
        shiftTypeField.setItemLabelGenerator(type -> type.name().replace("_", " "));

        workingDaysField.setItems(WorkingDay.values());

        // Set Step Increments
        startTimeField.setStep(Duration.ofMinutes(15));
        endTimeField.setStep(Duration.ofMinutes(15));
        firstHalfEndTimeField.setStep(Duration.ofMinutes(15));
        secondHalfStartTimeField.setStep(Duration.ofMinutes(15));
        gracePeriodField.setStepButtonsVisible(true);
        startTimeField.setLocale(Locale.UK);
        endTimeField.setLocale(Locale.UK);
        firstHalfEndTimeField.setLocale(Locale.UK);
        secondHalfStartTimeField.setLocale(Locale.UK);

        gracePeriodField.setStepButtonsVisible(true);
        gracePeriodField.setMin(0);
        gracePeriodField.setTooltipText("The minimum total minutes an employee must be clocked in to avoid being marked absent (Total shift time minus 1-hour lunch).");

        HasValue.ValueChangeListener<AbstractField.ComponentValueChangeEvent<TimePicker, LocalTime>> timeChangeListener = event -> {
            LocalTime start = startTimeField.getValue();
            LocalTime end = endTimeField.getValue();

            if (start != null && end != null) {
                long durationMinutes = Duration.between(start, end).toMinutes();

                if (durationMinutes < 0) {
                    durationMinutes += 24 * 60;
                }

                if (durationMinutes > 60) {
                    long halfDuration = durationMinutes / 2;

                    LocalTime middleTime = start.plusMinutes(halfDuration);
                    firstHalfEndTimeField.setValue(middleTime);
                    secondHalfStartTimeField.setValue(middleTime);

                    long requiredWorkMins = durationMinutes - 60;
                    gracePeriodField.setValue((int) requiredWorkMins);
                } else {
                    gracePeriodField.setValue((int) durationMinutes);
                }
            }
        };
        startTimeField.addValueChangeListener(timeChangeListener);
        endTimeField.addValueChangeListener(timeChangeListener);



        FormLayout topLayout = new FormLayout(nameField, shiftTypeField, isRotationalShiftField, workingDaysField);
        topLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));
        topLayout.setColspan(workingDaysField, 2);

        H4 mainTimesHeader = new H4("Standard Shift Timings");
        mainTimesHeader.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.XSMALL);
        FormLayout mainTimesLayout = new FormLayout(startTimeField, endTimeField);
        mainTimesLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

        H4 sessionTimesHeader = new H4("Half-Day Session Boundaries");
        sessionTimesHeader.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.XSMALL);
        FormLayout sessionTimesLayout = new FormLayout(firstHalfEndTimeField, secondHalfStartTimeField);
        sessionTimesLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));


        isRotationalShiftField.addValueChangeListener(e -> {
            boolean isRotational = Boolean.TRUE.equals(e.getValue());
            workingDaysField.setVisible(!isRotational);
            mainTimesHeader.setVisible(!isRotational);
            mainTimesLayout.setVisible(!isRotational);
            sessionTimesHeader.setVisible(!isRotational);
            sessionTimesLayout.setVisible(!isRotational);
            gracePeriodField.setVisible(!isRotational);
            rotationSequenceContainer.setVisible(isRotational);

            // e.isFromClient() ensures this ONLY runs when the user manually clicks the checkbox,
            // preventing bugs when binder.readBean() loads data in the background.
            if (e.isFromClient() && isRotational && rotationSequenceContainer.getComponentCount() == 0) {
                rotationSequenceContainer.removeAll();
                rotationSequenceContainer.add(new H4("Rotation Segments"));

                Button addSegmentBtn = new Button("Add Segment", new Icon(VaadinIcon.PLUS));
                addSegmentBtn.addClickListener(ev ->
                        rotationSequenceContainer.addComponentAtIndex(rotationSequenceContainer.getComponentCount() - 1, new RotationSegmentEditor())
                );

                // We no longer auto-spawn an empty RotationSegmentEditor here.
                // It stays perfectly clean until the user clicks "Add Segment".
                rotationSequenceContainer.add(addSegmentBtn);
            }
        });

        FormLayout bottomLayout = new FormLayout(gracePeriodField);
        bottomLayout.addClassNames(LumoUtility.Margin.Top.MEDIUM);

        VerticalLayout dialogLayout = new VerticalLayout(topLayout, mainTimesHeader, mainTimesLayout, sessionTimesHeader, sessionTimesLayout, bottomLayout, rotationSequenceContainer);
        dialogLayout.setPadding(false);

        // Binder Bindings
        binder.forField(nameField).asRequired("Shift Name is required").bind(Shift::getName, Shift::setName);
        binder.forField(shiftTypeField).asRequired("Shift Type is required").bind(Shift::getShiftType, Shift::setShiftType);
        binder.forField(isRotationalShiftField).bind(Shift::getIsRotationalShift, Shift::setIsRotationalShift);

        // CONDITIONAL VALIDATORS: Only require these fields if it is NOT a rotational shift
        binder.forField(workingDaysField)
                .withValidator(days -> isRotationalShiftField.getValue() || (days != null && !days.isEmpty()), "Select at least one working day")
                .bind(Shift::getWorkingDays, Shift::setWorkingDays);

        binder.forField(startTimeField)
                .withValidator(time -> isRotationalShiftField.getValue() || time != null, "Start Time is required")
                .bind(Shift::getStartTime, Shift::setStartTime);

        binder.forField(endTimeField)
                .withValidator(time -> isRotationalShiftField.getValue() || time != null, "End Time is required")
                .bind(Shift::getEndTime, Shift::setEndTime);
        // New field bindings (Optional, so no .asRequired())
        binder.forField(firstHalfEndTimeField).bind(Shift::getFirstHalfEndTime, Shift::setFirstHalfEndTime);
        binder.forField(secondHalfStartTimeField).bind(Shift::getSecondHalfStartTime, Shift::setSecondHalfStartTime);
        binder.forField(gracePeriodField)
                .withConverter(
                        value -> value == null ? null : Long.valueOf(value),
                        value -> value == null ? null : value.intValue()
                )
                .bind(Shift::getRequiredWorkTime, Shift::setRequiredWorkTime);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveShift());
        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(dialogLayout);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(Shift shift) {
        currentShift = shift;

        rotationSequenceContainer.removeAll();

        binder.readBean(currentShift);

        if (Boolean.TRUE.equals(shift.getIsRotationalShift())) {
            rotationSequenceContainer.add(new H4("Rotation Segments"));

            if (shift.getRotationSequences() != null) {
                for (RotationSequence seq : shift.getRotationSequences()) {
                    RotationSegmentEditor editor = new RotationSegmentEditor();
                    editor.setSegment(seq);
                    rotationSequenceContainer.add(editor);
                }
            }
            Button addSegmentBtn = new Button("Add Segment", new Icon(VaadinIcon.PLUS));
            addSegmentBtn.addClickListener(ev ->
                    rotationSequenceContainer.addComponentAtIndex(rotationSequenceContainer.getComponentCount() - 1, new RotationSegmentEditor())
            );
            rotationSequenceContainer.add(addSegmentBtn);
        }

        formDialog.open();
    }

    private void saveShift() {
        try {
            boolean isRotational = Boolean.TRUE.equals(isRotationalShiftField.getValue());

            // Only run standard time validation if it is NOT a rotational shift
            if (!isRotational) {
                Shifts selectedShift = shiftTypeField.getValue();
                boolean isNightShift = Shifts.NIGHT_SHIFT.equals(selectedShift);

                LocalTime start = startTimeField.getValue();
                LocalTime end = endTimeField.getValue();
                LocalTime firstHalf = firstHalfEndTimeField.getValue();
                LocalTime secondHalf = secondHalfStartTimeField.getValue();

                if (!isNightShift) {
                    if (start != null && end != null && start.isAfter(end)) {
                        Notification.show("The start time cannot come after the end time for a day shift.").addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }
                    if (firstHalf != null && secondHalf != null && firstHalf.isAfter(secondHalf)) {
                        Notification.show("The 1st Half End Time cannot be after the 2nd Half Start Time.").addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }
                }

                if (start != null && end != null && firstHalf != null && secondHalf != null) {
                    if (!isTimeBetwee(firstHalf, start, end, isNightShift) || !isTimeBetwee(secondHalf, start, end, isNightShift)) {
                        Notification.show("Half-day boundaries must fall between the shift start and end times.").addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }
                }
            }

            // Vaadin checks our conditional validators here!
            binder.writeBean(currentShift);

            // --- NEW EXTRACTION LOGIC FOR CUSTOM SEGMENTS ---
            if (isRotational) {
                List<RotationSequence> sequences = new ArrayList<>();
                int order = 1;

                // Loop through the UI components to grab the segment data
                for (Component c : rotationSequenceContainer.getChildren().toList()) {
                    if (c instanceof RotationSegmentEditor) {
                        RotationSequence seq = ((RotationSegmentEditor) c).getSegment(order++);

                        // Safety Check: Ensure segment name isn't blank
                        if (seq.getName() == null || seq.getName().trim().isEmpty()) {
                            Notification.show("All segments must have a Segment Name.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                            return;
                        }

                        seq.setParentShift(currentShift); // Tie it to the parent!
                        sequences.add(seq);
                    }
                }

                if (sequences.isEmpty()) {
                    Notification.show("Rotational shifts require at least one segment.", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }

                // Ensure JPA properly removes orphaned sequences if they were deleted in the UI
                if (currentShift.getRotationSequences() != null) {
                    currentShift.getRotationSequences().clear();
                    currentShift.getRotationSequences().addAll(sequences);
                } else {
                    currentShift.setRotationSequences(sequences);
                }

            } else {
                // If they unchecked the box, clear out any attached sequences
                if (currentShift.getRotationSequences() != null) {
                    currentShift.getRotationSequences().clear();
                }
            }

            shiftService.addShift(currentShift);

            showNotification("Shift saved successfully", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();

        } catch (ValidationException e) {
            showNotification("Please check the form for errors", NotificationVariant.LUMO_ERROR);
        } catch (IllegalArgumentException e) {
            showNotification(e.getMessage(), NotificationVariant.LUMO_ERROR);
        } catch (DataIntegrityViolationException e) {
            showNotification("A shift with this name already exists.", NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            showNotification("An unexpected error occurred.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirmAndDelete(Shift shift) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Shift?");
        dialog.setText("Are you sure you want to permanently delete the shift '" + shift.getName() + "'?");

        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");

        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(event -> deleteShift(shift));
        dialog.open();
    }

    private void deleteShift(Shift shift) {
        try {
            shiftService.deleteShift(shift.getId());
            showNotification("Shift deleted", NotificationVariant.LUMO_SUCCESS);
            updateList();
        } catch (IllegalStateException e) {
            // Catches the foreign-key constraint exception specifically thrown by your service
            showNotification(e.getMessage(), NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            showNotification("An unexpected error occurred.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateList() {
        String searchTerm = searchField.getValue();
        if (searchTerm == null || searchTerm.isEmpty()) {
            grid.setItems(shiftService.getShifts());
        } else {
            grid.setItems(shiftService.search(searchTerm));
        }
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
    private boolean isTimeBetwee(LocalTime time, LocalTime start, LocalTime end, boolean isNightShift) {
        // Allow the boundary to be exactly on the start or end time if needed
        if (time.equals(start) || time.equals(end)) {
            return true;
        }

        if (!isNightShift) {
            // Standard Day Shift: time must be literally between start and end
            return time.isAfter(start) && time.isBefore(end);
        } else {
            // Night Shift (e.g., 22:00 to 06:00): time must be late at night OR early morning
            return time.isAfter(start) || time.isBefore(end);
        }
    }
    private class RotationSegmentEditor extends VerticalLayout {
        private final TextField segmentName = new TextField("Segment Name");
        private final ComboBox<RotationSegmentType> typeBox = new ComboBox<>("Type");
        private final IntegerField durationDays = new IntegerField("Duration (Days)");

        private final TimePicker start = new TimePicker("Start");
        private final TimePicker end = new TimePicker("End");
        private final IntegerField requiredMins = new IntegerField("Min Work Time");
        private final ComboBox<Shifts> shiftType = new ComboBox<>("Shift Type");
        private final TimePicker firstHalf = new TimePicker("1st Half Ends At");
        private final TimePicker secondHalf = new TimePicker("2nd Half Starts At");

        public RotationSegmentEditor() {
            typeBox.setItems(RotationSegmentType.values());
            shiftType.setItems(Shifts.values());
            // Inline CSS to create a Card look
            getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
            getStyle().set("border-radius", "var(--lumo-border-radius-m)");
            getStyle().set("padding", "var(--lumo-space-m)");
            getStyle().set("margin-bottom", "var(--lumo-space-m)");
            getStyle().set("box-shadow", "0 2px 4px rgba(0,0,0,0.05)");

            firstHalf.setStep(Duration.ofMinutes(15));
            secondHalf.setStep(Duration.ofMinutes(15));
            firstHalf.setLocale(Locale.UK);
            secondHalf.setLocale(Locale.UK);
            start.setStep(Duration.ofMinutes(15));
            end.setStep(Duration.ofMinutes(15));
            start.setLocale(Locale.UK);
            end.setLocale(Locale.UK);

            // Auto-calculate bounds and grace period
            HasValue.ValueChangeListener<AbstractField.ComponentValueChangeEvent<TimePicker, LocalTime>> timeCalc = event -> {
                LocalTime s = start.getValue();
                LocalTime e = end.getValue();
                if (s != null && e != null) {
                    long durationMinutes = Duration.between(s, e).toMinutes();
                    if (durationMinutes < 0) durationMinutes += 24 * 60; // Crosses midnight

                    if (durationMinutes > 60) {
                        long halfDuration = durationMinutes / 2;
                        LocalTime middleTime = s.plusMinutes(halfDuration);
                        firstHalf.setValue(middleTime);
                        secondHalf.setValue(middleTime);
                        requiredMins.setValue((int) (durationMinutes - 60));
                    } else {
                        requiredMins.setValue((int) durationMinutes);
                    }
                }
            };
            start.addValueChangeListener(timeCalc);
            end.addValueChangeListener(timeCalc);

            // Default settings
            durationDays.setMin(1);
            durationDays.setValue(1);
            typeBox.setValue(RotationSegmentType.WORK);

            typeBox.addValueChangeListener(e -> {
                boolean isWork = RotationSegmentType.WORK.equals(e.getValue());
                start.setVisible(isWork);
                end.setVisible(isWork);
                requiredMins.setVisible(isWork);
                shiftType.setVisible(isWork);
                firstHalf.setVisible(isWork);
                secondHalf.setVisible(isWork);
            });

            Button removeBtn = new Button(new Icon(VaadinIcon.TRASH));
            removeBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            removeBtn.addClickListener(e -> rotationSequenceContainer.remove(this));

            // Move fields into a responsive FormLayout instead of cramming into a row
            FormLayout formLayout = new FormLayout();
            formLayout.add(segmentName, typeBox, durationDays, shiftType, start, end, firstHalf, secondHalf, requiredMins);
            formLayout.setResponsiveSteps(
                    new FormLayout.ResponsiveStep("0", 1),
                    new FormLayout.ResponsiveStep("500px", 2),
                    new FormLayout.ResponsiveStep("800px", 3)
            );

            HorizontalLayout header = new HorizontalLayout(new H5("Segment Details"), removeBtn);
            header.setWidthFull();
            header.setJustifyContentMode(JustifyContentMode.BETWEEN);
            header.setAlignItems(Alignment.CENTER);

            add(header, formLayout);
            setWidthFull();
            setPadding(false);
        }

        public RotationSequence getSegment(int order) {
            RotationSequence seq = new RotationSequence();
            seq.setName(segmentName.getValue());
            seq.setSegmentType(typeBox.getValue());
            seq.setDurationDays(durationDays.getValue());
            seq.setSequenceOrder(order);

            if (RotationSegmentType.WORK.equals(typeBox.getValue())) {
                seq.setShiftType(shiftType.getValue());
                seq.setStartTime(start.getValue());
                seq.setEndTime(end.getValue());
                seq.setFirstHalfEndTime(firstHalf.getValue());
                seq.setSecondHalfStartTime(secondHalf.getValue());
                seq.setRequiredWorkTime(requiredMins.getValue() != null ? requiredMins.getValue().longValue() : null);
                seq.setCrossesMidnight(end.getValue() != null && start.getValue() != null && end.getValue().isBefore(start.getValue()));
            }
            return seq;
        }
        public void setSegment(RotationSequence seq) {
            segmentName.setValue(seq.getName() != null ? seq.getName() : "");
            typeBox.setValue(seq.getSegmentType() != null ? seq.getSegmentType() : RotationSegmentType.WORK);
            durationDays.setValue(seq.getDurationDays() != null ? seq.getDurationDays() : 1);

            if (seq.getShiftType() != null) shiftType.setValue(seq.getShiftType());
            if (seq.getStartTime() != null) start.setValue(seq.getStartTime());
            if (seq.getEndTime() != null) end.setValue(seq.getEndTime());
            if (seq.getFirstHalfEndTime() != null) firstHalf.setValue(seq.getFirstHalfEndTime());
            if (seq.getSecondHalfStartTime() != null) secondHalf.setValue(seq.getSecondHalfStartTime());
            if (seq.getRequiredWorkTime() != null) requiredMins.setValue(seq.getRequiredWorkTime().intValue());
        }
    }
}