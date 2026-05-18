package com.murali.views;

import com.murali.dto.ShiftAssignmentDTO;
import com.murali.entity.LeaveBalance;
import com.murali.entity.LeaveRequest;
import com.murali.security.CustomUserDetails;
import com.murali.service.LeaveBalanceService;
import com.murali.service.LeaveRequestService;
import com.murali.service.SecurityService;
import com.murali.service.ShiftAssignmentService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@PermitAll // Update with your specific role e.g., @RolesAllowed("EMPLOYEE")
@PageTitle("My Dashboard")
@Route(value = "dashboard", layout = MainLayout.class)
public class EmployeeDashboardView extends VerticalLayout {

    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestService leaveRequestService;
    private final SecurityService securityService;
    private final ShiftAssignmentService shiftAssignmentService;

    public EmployeeDashboardView(LeaveBalanceService leaveBalanceService,
                                 LeaveRequestService leaveRequestService, SecurityService securityService, ShiftAssignmentService shiftAssignmentService) {
        this.leaveBalanceService = leaveBalanceService;
        this.leaveRequestService = leaveRequestService;
        this.securityService = securityService;
        this.shiftAssignmentService = shiftAssignmentService;

        setSizeFull();
        addClassNames(LumoUtility.Padding.LARGE);

        buildUI();
    }

    private void buildUI() {
        Long employeeId = securityService.getCurrentEmployeeId();
        if (employeeId == null) {
            return;
        }

        HorizontalLayout header = new HorizontalLayout(
                new H2("Welcome back, " + securityService.getAuthenticatedUser().getUsername() + "!"),
                new Button("New Leave Request", VaadinIcon.PLUS.create(),
                        e -> getUI().ifPresent(ui -> ui.navigate("apply-leave")))
        );

        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        Component balanceSection = createBalanceSection(employeeId);

        Component shiftSection = createShiftSection(employeeId);

        Component historySection = createHistorySection(employeeId);

        add(
                header,
                new Hr(),
                balanceSection,
                shiftSection,
                historySection
        );
    }

    private Component createBalanceSection(Long employeeId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        H3 title = new H3("Your Leave Balances");
        title.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.NONE);

        FlexLayout cards = createBalanceCards(employeeId, LocalDate.now().getYear());

        section.add(title, cards);
        return section;
    }

    private FlexLayout createBalanceCards(Long employeeId, int year) {
        FlexLayout cardsLayout = new FlexLayout();

        cardsLayout.setWidthFull();
        cardsLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cardsLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        cardsLayout.setAlignItems(FlexComponent.Alignment.START);

        cardsLayout.getStyle().set("gap", "var(--lumo-space-m)");

        List<LeaveBalance> balances =
                leaveBalanceService.getBalancesForEmployee(employeeId, year);

        for (LeaveBalance balance : balances) {
            cardsLayout.add(createSingleCard(balance));
        }

        return cardsLayout;
    }


    private Component createSingleCard(LeaveBalance balance) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(
                LumoUtility.Background.BASE,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.BoxShadow.SMALL,
                LumoUtility.Padding.LARGE,
                LumoUtility.Border.ALL
        );

        card.setWidth("260px");
        card.getStyle().set("flex", "1 1 260px");
        card.setMaxWidth("320px");

        card.setSpacing(false);

        BigDecimal total = balance.getTotalEntitled();
        BigDecimal remaining = leaveBalanceService.getEffectiveBalance(balance);
        double used = total.subtract(remaining).doubleValue();
        double percentUsed = (total.doubleValue() > 0) ? (used / total.doubleValue()) * 100 : 0;

        // Header
        Span typeName = new Span(balance.getLeaveType().getName());
        typeName.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.FontWeight.BOLD);

        H2 amount = new H2(remaining + " Days Left");
        amount.addClassNames(LumoUtility.Margin.Vertical.SMALL, LumoUtility.TextColor.PRIMARY);

        // Progress Bar (HTML5 progress or styled div)
        Span progressBar = new Span();
        progressBar.setWidthFull();
        progressBar.setHeight("8px");
        progressBar.addClassNames(LumoUtility.Background.CONTRAST_10, LumoUtility.BorderRadius.MEDIUM);
        progressBar.getStyle().set("position", "relative").set("overflow", "hidden");

        Span progressFill = new Span();
        progressFill.setHeightFull();
        progressFill.setWidth(percentUsed + "%");
        progressFill.addClassNames(LumoUtility.Background.PRIMARY);
        progressBar.add(progressFill);

        Span stats = new Span(String.format("Used: %s / %s total", used, total));
        stats.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.TERTIARY, LumoUtility.Margin.Top.SMALL);

        card.add(typeName, amount, progressBar, stats);
        return card;
    }

    private Grid<LeaveRequest> createHistoryGrid(Long employeeId) {
        Grid<LeaveRequest> grid = new Grid<>(LeaveRequest.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();

        grid.addColumn(request -> request.getLeaveType().getName())
                .setHeader("Leave Type")
                .setAutoWidth(true)
                .setFlexGrow(1);

        grid.addColumn(LeaveRequest::getStartDate)
                .setHeader("Start Date")
                .setAutoWidth(true);

        grid.addColumn(LeaveRequest::getEndDate)
                .setHeader("End Date")
                .setAutoWidth(true);

        grid.addColumn(LeaveRequest::getDurationDays)
                .setHeader("Days")
                .setAutoWidth(true);

        // Status Column using ComponentRenderer for Lumo Badges
        grid.addComponentColumn(this::createStatusBadge)
                .setHeader("Status")
                .setAutoWidth(true);

        grid.addColumn(LeaveRequest::getReason)
                .setHeader("Reason")
                .setTooltipGenerator(LeaveRequest::getReason)
                .setAutoWidth(true)
                .setFlexGrow(2);

        // Fetch and set items
        List<LeaveRequest> history = leaveRequestService.getLeaveHistoryForEmployee(employeeId);
        grid.setItems(history);

        return grid;
    }

    private Span createStatusBadge(LeaveRequest request) {
        Span badge = new Span(request.getStatus());
        badge.getElement().getThemeList().add("badge");

        String status = request.getStatus() != null ? request.getStatus().toUpperCase() : "";

        switch (status) {
            case "APPROVED":
                badge.getElement().getThemeList().add("success");
                break;
            case "REJECTED":
                badge.getElement().getThemeList().add("error");
                break;
            case "PENDING":
            case "ESCALATED":
            case "RETURNED_FOR_EDIT":
                badge.getElement().getThemeList().add("warning");
                break;
            case "CANCELLED":
                badge.getElement().getThemeList().add("contrast");
                break;
            default:
                // Default styling
                break;
        }
        return badge;
    }
    private Component createHistorySection(Long employeeId) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        H3 title = new H3("Leave Request History");

        ComboBox<String> statusFilter = new ComboBox<>("Filter by Status");
        statusFilter.setItems("PENDING", "APPROVED", "REJECTED", "CANCELLED");
        statusFilter.setClearButtonVisible(true);

        DatePicker yearFilter = new DatePicker("Starts After");

        HorizontalLayout toolbar = new HorizontalLayout(statusFilter, yearFilter);
        toolbar.setAlignItems(FlexComponent.Alignment.END);

        Grid<LeaveRequest> grid = createHistoryGrid(employeeId);

        statusFilter.addValueChangeListener(e -> {
            List<LeaveRequest> filtered = leaveRequestService.getLeaveHistoryForEmployee(employeeId).stream()
                    .filter(r -> e.getValue() == null || r.getStatus().equalsIgnoreCase(e.getValue()))
                    .toList();
            grid.setItems(filtered);
        });

        layout.add(title, toolbar, grid);
        return layout;
    }
    private Component createShiftSection(Long employeeId) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);

        H3 title = new H3("Your Upcoming Shift Schedule");
        title.addClassNames(LumoUtility.Margin.Top.MEDIUM, LumoUtility.Margin.Bottom.NONE);

        Grid<ShiftAssignmentDTO> shiftGrid = new Grid<>(ShiftAssignmentDTO.class, false);
        shiftGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        shiftGrid.setWidthFull();
        shiftGrid.setHeight("200px");

        shiftGrid.addColumn(ShiftAssignmentDTO::getAssignmentDate)
                .setHeader("Date")
                .setAutoWidth(true)
                .setSortable(true);

        shiftGrid.addColumn(ShiftAssignmentDTO::getShiftName)
                .setHeader("Shift Template")
                .setAutoWidth(true);

        shiftGrid.addColumn(dto -> dto.getStartTime() + " - " + dto.getEndTime())
                .setHeader("Working Hours")
                .setAutoWidth(true);

        shiftGrid.addComponentColumn(dto -> {
            if (Boolean.TRUE.equals(dto.getIsOverride())) {
                Span badge = new Span("Adjusted");
                badge.getElement().getThemeList().add("badge warning small");
                return badge;
            }
            return new Span("-");
        }).setHeader("Schedule Type").setAutoWidth(true);

        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);

        List<ShiftAssignmentDTO> myShifts = shiftAssignmentService.fetchAssignmentsForCalendarPivot(today, nextWeek)
                .stream()
                .filter(assignment -> assignment.getEmployeeId().equals(employeeId))
                .toList();

        for(ShiftAssignmentDTO i: myShifts){
            System.out.println(i.toString());
        }

        shiftGrid.setItems(myShifts);

        section.add(title, shiftGrid);
        return section;
    }
}
