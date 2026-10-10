package cl.duoc.digitalfix.workorders.event;

import cl.duoc.digitalfix.workorders.entity.WorkOrder;
import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;

import java.time.Instant;

/**
 * Evento de dominio: una orden se creo (from = null) o cambio de estado.
 * Se publica con el ApplicationEventPublisher de Spring; el servicio de
 * negocio NO conoce RabbitMQ. La capa messaging lo traduce a comandos
 * asincronos despues del commit (ver WorkOrderCommandTranslator).
 */
public record WorkOrderStatusChangedEvent(
    Long orderId,
    WorkOrderStatus from,
    WorkOrderStatus to,
    String clienteId,
    String tecnicoId,
    String descripcion,
    Instant createdAt,
    Instant closedAt,
    String actor
) {
    public static WorkOrderStatusChangedEvent of(WorkOrder order, WorkOrderStatus from, String actor) {
        return new WorkOrderStatusChangedEvent(order.getId(), from, order.getStatus(), order.getClienteId(),
            order.getTecnicoId(), order.getDescripcion(), order.getCreatedAt(), order.getClosedAt(), actor);
    }

    public boolean isCreation() {
        return from == null;
    }
}
