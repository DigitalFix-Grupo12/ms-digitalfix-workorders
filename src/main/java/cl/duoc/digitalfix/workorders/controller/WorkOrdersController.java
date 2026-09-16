package cl.duoc.digitalfix.workorders.controller;

import cl.duoc.digitalfix.workorders.dto.StatusChangeDto;
import cl.duoc.digitalfix.workorders.dto.WorkOrderDto;
import cl.duoc.digitalfix.workorders.entity.WorkOrder;
import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;
import cl.duoc.digitalfix.workorders.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API interna de ordenes de trabajo. No valida JWT: solo el BFF le habla
 * (por localhost). El BFF propaga la identidad del usuario en X-User-Name.
 */
@RestController
@RequestMapping("/api/workorders")
public class WorkOrdersController {

    static final String USER_HEADER = "X-User-Name";
    private final WorkOrderService service;

    public WorkOrdersController(WorkOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkOrder> list(@RequestParam(required = false) WorkOrderStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public WorkOrder getById(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<WorkOrder> create(
            @Valid @RequestBody WorkOrderDto dto,
            @RequestHeader(value = USER_HEADER, defaultValue = "desconocido") String usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, usuario));
    }

    @PutMapping("/{id}/status")
    public WorkOrder updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusChangeDto dto,
            @RequestHeader(value = USER_HEADER, defaultValue = "desconocido") String usuario) {
        return service.changeStatus(id, dto, usuario);
    }
}
