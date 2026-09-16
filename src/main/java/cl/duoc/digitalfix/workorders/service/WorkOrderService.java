package cl.duoc.digitalfix.workorders.service;

import cl.duoc.digitalfix.workorders.client.AuditClient;
import cl.duoc.digitalfix.workorders.dto.StatusChangeDto;
import cl.duoc.digitalfix.workorders.dto.WorkOrderDto;
import cl.duoc.digitalfix.workorders.entity.WorkOrder;
import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;
import cl.duoc.digitalfix.workorders.repository.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Reglas de negocio de ordenes de trabajo (dueño exclusivo de work_orders).
 * Autorizacion a nivel de dato: un Cliente solo ve y crea sus propias ordenes
 * (clienteId = usuario del token). Admin/Supervisor ven todas.
 */
@Service
public class WorkOrderService {

    private final WorkOrderRepository repository;
    private final AuditClient audit;

    public WorkOrderService(WorkOrderRepository repository, AuditClient audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> list(WorkOrderStatus status, Caller caller) {
        if (caller.isCustomerOnly()) {
            String owner = caller.username();
            return status == null
                ? repository.findByClienteIdOrderByIdDesc(owner)
                : repository.findByClienteIdAndStatusOrderByIdDesc(owner, status);
        }
        return status == null
            ? repository.findAllByOrderByIdDesc()
            : repository.findByStatusOrderByIdDesc(status);
    }

    @Transactional(readOnly = true)
    public WorkOrder get(Long id, Caller caller) {
        WorkOrder order = repository.findById(id)
            .orElseThrow(() -> notFound(id));
        // A un cliente no se le revela la existencia de ordenes ajenas (404, no 403)
        if (caller.isCustomerOnly() && !caller.username().equalsIgnoreCase(order.getClienteId())) {
            throw notFound(id);
        }
        return order;
    }

    @Transactional
    public WorkOrder create(WorkOrderDto dto, Caller caller) {
        String clienteId;
        if (caller.isCustomerOnly()) {
            clienteId = caller.username();
        } else if (dto.clienteId() == null || dto.clienteId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta clienteId: indica el cliente de la orden");
        } else {
            clienteId = dto.clienteId().trim();
        }

        WorkOrder order = new WorkOrder();
        order.setDescripcion(dto.descripcion().trim());
        order.setClienteId(clienteId);
        order.setStatus(WorkOrderStatus.CREADA);
        WorkOrder saved = repository.save(order);
        audit.record(caller.username(), "CREO orden #" + saved.getId(), saved.getId());
        return saved;
    }

    @Transactional
    public WorkOrder changeStatus(Long id, StatusChangeDto dto, Caller caller) {
        if (caller.isCustomerOnly()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El rol Cliente no puede cambiar estados");
        }
        WorkOrder order = get(id, caller);
        WorkOrderStatus from = order.getStatus();
        WorkOrderStatus to = dto.status();

        if (!from.canTransitionTo(to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Transición inválida: %s -> %s (permitidas: %s)".formatted(from, to, from.next()));
        }

        if (to == WorkOrderStatus.ASIGNADA) {
            if (dto.tecnicoId() == null || dto.tecnicoId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Falta tecnicoId: no se puede asignar sin indicar el técnico");
            }
            order.setTecnicoId(dto.tecnicoId().trim());
        }
        if (to == WorkOrderStatus.CERRADA) {
            order.setClosedAt(Instant.now());
        }

        order.setStatus(to);
        WorkOrder saved = repository.saveAndFlush(order);

        String accion = to == WorkOrderStatus.ASIGNADA
            ? "ASIGNO orden #%d a %s".formatted(id, order.getTecnicoId())
            : "CAMBIO orden #%d: %s -> %s".formatted(id, from, to);
        audit.record(caller.username(), accion, id);
        return saved;
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + id);
    }
}
