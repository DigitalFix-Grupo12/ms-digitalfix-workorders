package cl.duoc.digitalfix.workorders.streaming;

import cl.duoc.digitalfix.workorders.entity.WorkOrderStatus;
import cl.duoc.digitalfix.workorders.event.WorkOrderStatusChangedEvent;
import cl.duoc.digitalfix.workorders.streaming.config.WorkOrderKafkaProperties;
import cl.duoc.digitalfix.workorders.streaming.event.Actor;
import cl.duoc.digitalfix.workorders.streaming.event.WorkOrderEvent;
import cl.duoc.digitalfix.workorders.streaming.event.WorkOrderEventType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Publica cada hecho de la orden en Kafka DESPUES del commit: si la transaccion
 * falla (ej. transicion invalida) no sale ningun evento, y nunca se informa a
 * reporteria o auditoria algo que no quedo guardado.
 *
 * <pre>
 *  creacion              -> CREATED
 *  -> ASIGNADA           -> ASSIGNED        (trae technician)
 *  -> EN_DESPLAZAMIENTO  -> STATUS_CHANGED  (previousStatus, status)
 *  -> EN_EJECUCION       -> STATUS_CHANGED
 *  -> CERRADA            -> CLOSED          (base del KPI de tiempo de resolucion)
 *  -> CANCELADA          -> CANCELLED
 * </pre>
 *
 * Convive con WorkOrderCommandTranslator (RabbitMQ): Rabbit recibe comandos
 * ("envia este email") y Kafka el hecho ("la orden cambio de estado").
 */
@Component
public class WorkOrderEventStreamer {

    private final WorkOrderEventPublisher publisher;
    private final WorkOrderKafkaProperties props;

    public WorkOrderEventStreamer(WorkOrderEventPublisher publisher, WorkOrderKafkaProperties props) {
        this.publisher = publisher;
        this.props = props;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(WorkOrderStatusChangedEvent e) {
        publisher.publish(toEvent(e));
    }

    WorkOrderEvent toEvent(WorkOrderStatusChangedEvent e) {
        return new WorkOrderEvent(
                UUID.randomUUID().toString(),
                typeOf(e),
                props.schemaVersion(),
                e.occurredAt(),
                UUID.randomUUID().toString().substring(0, 16),
                props.source(),
                e.orderId(),
                new Actor(e.actor(), e.actor(), e.actorRole()),
                e.to().name(),
                e.from() == null ? null : e.from().name(),
                e.tecnicoId(),
                e.clienteId(),
                null);
    }

    static WorkOrderEventType typeOf(WorkOrderStatusChangedEvent e) {
        if (e.isCreation()) {
            return WorkOrderEventType.CREATED;
        }
        WorkOrderStatus to = e.to();
        return switch (to) {
            case ASIGNADA -> WorkOrderEventType.ASSIGNED;
            case CERRADA -> WorkOrderEventType.CLOSED;
            case CANCELADA -> WorkOrderEventType.CANCELLED;
            default -> WorkOrderEventType.STATUS_CHANGED;
        };
    }
}
