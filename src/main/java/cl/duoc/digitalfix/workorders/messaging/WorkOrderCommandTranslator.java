package cl.duoc.digitalfix.workorders.messaging;

import cl.duoc.digitalfix.workorders.event.WorkOrderStatusChangedEvent;
import cl.duoc.digitalfix.workorders.messaging.payload.DispatchStatusPayload;
import cl.duoc.digitalfix.workorders.messaging.payload.DispatchTicketPayload;
import cl.duoc.digitalfix.workorders.messaging.payload.EmailPayload;
import cl.duoc.digitalfix.workorders.messaging.payload.WorkReportPayload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Traduce eventos de dominio de ordenes a comandos asincronos.
 * Se ejecuta DESPUES del commit: si la transaccion falla no se envia nada,
 * y nunca se notifica algo que no quedo guardado.
 *
 * Rutas de mensajeria por transicion:
 * <pre>
 *  CREADA            -> EMAIL_CREATED
 *  ASIGNADA          -> EMAIL_ASSIGNED + DISPATCH_TICKET
 *  EN_DESPLAZAMIENTO -> EMAIL_ENROUTE  + DISPATCH_STATUS
 *  EN_EJECUCION      -> DISPATCH_STATUS
 *  CERRADA           -> EMAIL_CLOSED   + DISPATCH_STATUS + WORK_REPORT
 *  CANCELADA         -> EMAIL_CANCELLED (+ DISPATCH_STATUS si tenia tecnico)
 * </pre>
 */
@Component
public class WorkOrderCommandTranslator {

    private final CommandPublisher publisher;

    public WorkOrderCommandTranslator(CommandPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(WorkOrderStatusChangedEvent e) {
        String correlationId = "orden-" + e.orderId();
        String traceId = UUID.randomUUID().toString();

        if (e.isCreation()) {
            email(Publication.EMAIL_CREATED, "CREATED", e, correlationId, traceId);
            return;
        }
        switch (e.to()) {
            case ASIGNADA -> {
                email(Publication.EMAIL_ASSIGNED, "ASSIGNED", e, correlationId, traceId);
                publisher.publish(Publication.DISPATCH_TICKET, CommandType.DISPATCH_TICKET, correlationId, traceId,
                    new DispatchTicketPayload(e.orderId(), e.tecnicoId(), e.clienteId(), e.descripcion()));
            }
            case EN_DESPLAZAMIENTO -> {
                email(Publication.EMAIL_ENROUTE, "ENROUTE", e, correlationId, traceId);
                dispatchStatus(e, correlationId, traceId);
            }
            case EN_EJECUCION -> dispatchStatus(e, correlationId, traceId);
            case CERRADA -> {
                email(Publication.EMAIL_CLOSED, "CLOSED", e, correlationId, traceId);
                dispatchStatus(e, correlationId, traceId);
                publisher.publish(Publication.WORK_REPORT, CommandType.WORK_REPORT, correlationId, traceId,
                    new WorkReportPayload(e.orderId(), e.clienteId(), e.tecnicoId(), e.descripcion(),
                        e.createdAt(), e.closedAt(), e.actor()));
            }
            case CANCELADA -> {
                email(Publication.EMAIL_CANCELLED, "CANCELLED", e, correlationId, traceId);
                if (e.tecnicoId() != null) dispatchStatus(e, correlationId, traceId);
            }
            case CREADA -> { /* no hay transicion hacia CREADA */ }
        }
    }

    private void email(Publication publication, String template, WorkOrderStatusChangedEvent e,
                       String correlationId, String traceId) {
        publisher.publish(publication, CommandType.EMAIL, correlationId, traceId,
            new EmailPayload(e.orderId(), e.clienteId(), template, e.to().name(), e.tecnicoId(), e.descripcion()));
    }

    private void dispatchStatus(WorkOrderStatusChangedEvent e, String correlationId, String traceId) {
        publisher.publish(Publication.DISPATCH_STATUS, CommandType.DISPATCH_STATUS, correlationId, traceId,
            new DispatchStatusPayload(e.orderId(), e.tecnicoId(), e.to().name()));
    }
}
