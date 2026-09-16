package cl.duoc.digitalfix.workorders.controller;

import cl.duoc.digitalfix.workorders.dto.StatusChangeDto;
import cl.duoc.digitalfix.workorders.dto.WorkOrderDto;
import cl.duoc.digitalfix.workorders.entity.WorkOrder;
import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;
import cl.duoc.digitalfix.workorders.service.Caller;
import cl.duoc.digitalfix.workorders.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API interna de ordenes de trabajo. No valida JWT: solo el BFF le habla
 * (por localhost) y propaga la identidad en X-User-Name y X-User-Roles.
 */
@RestController
@RequestMapping("/api/workorders")
public class WorkOrdersController {

    static final String USER_HEADER = "X-User-Name";
    static final String ROLES_HEADER = "X-User-Roles";
    private final WorkOrderService service;

    public WorkOrdersController(WorkOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkOrder> list(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestHeader(value = USER_HEADER, required = false) String user,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        return service.list(status, Caller.of(user, roles));
    }

    @GetMapping("/{id}")
    public WorkOrder getById(
            @PathVariable Long id,
            @RequestHeader(value = USER_HEADER, required = false) String user,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        return service.get(id, Caller.of(user, roles));
    }

    @PostMapping
    public ResponseEntity<WorkOrder> create(
            @Valid @RequestBody WorkOrderDto dto,
            @RequestHeader(value = USER_HEADER, required = false) String user,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, Caller.of(user, roles)));
    }

    @PutMapping("/{id}/status")
    public WorkOrder updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusChangeDto dto,
            @RequestHeader(value = USER_HEADER, required = false) String user,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        return service.changeStatus(id, dto, Caller.of(user, roles));
    }
}
